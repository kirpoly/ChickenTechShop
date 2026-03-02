package chickentechshop.campaign.rulecmd;

import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import chickentechshop.campaign.intel.missions.chicken.ChickenQuestUtils;
import chickentechshop.campaign.submarkets.TechMarket;
import chickentechshop.config.CTS_Config;

public class CTS_LuckyRestockInteraction extends BaseCommandPlugin {

    private static final String PRICE_MEMORY_KEY = "$cts_lrestock_price_fixed";
    private static final String PRICE_CFG_MEAN_KEY = "$cts_lrestock_price_cfg_mean";
    private static final String PRICE_CFG_STD_KEY = "$cts_lrestock_price_cfg_std";
    private static final String PRICE_CFG_FLOOR_KEY = "$cts_lrestock_price_cfg_floor";

    @Override
    public boolean execute(final String ruleId, final InteractionDialogAPI dialog, final List<Misc.Token> params,
            final Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params == null || params.isEmpty()) {
            return false;
        }

        String action = params.get(0).getString(memoryMap);
        if ("canShow".equals(action)) {
            int requiredLevel = CTS_Config.get().luckyRestockMinLevel;
            if (params.size() > 1) {
                requiredLevel = params.get(1).getInt(memoryMap);
            }
            return canShow(requiredLevel);
        } else if ("prepare".equals(action)) {
            return prepare(dialog, memoryMap);
        } else if ("buy".equals(action)) {
            return buy(dialog, memoryMap);
        } else if ("decline".equals(action)) {
            return decline(dialog);
        }

        return false;
    }

    private boolean canShow(int requiredLevel) {
        MarketAPI market = ChickenQuestUtils.getChickenMarket();
        if (market == null || !market.hasSubmarket("chicken_market")) {
            return false;
        }
        TechMarket submarket = (TechMarket) market.getSubmarket("chicken_market").getPlugin();
        return submarket.getTechMarketLevel() >= requiredLevel;
    }

    private boolean prepare(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        int price = getOrCreateStoredPrice();
        MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
        if (local == null) {
            return false;
        }
        local.set("$cts_lrestock_price_num", price, 0f);
        local.set("$cts_lrestock_price", Misc.getDGSCredits(price), 0f);
        dialog.getTextPanel().addPara("Current price: " + Misc.getDGSCredits(price) + ".");
        return true;
    }

    private boolean buy(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
        if (local == null) {
            return false;
        }

        int price = getOrCreateStoredPrice();
        local.set("$cts_lrestock_price_num", price, 0f);
        local.set("$cts_lrestock_price", Misc.getDGSCredits(price), 0f);

        float playerCredits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        TextPanelAPI text = dialog.getTextPanel();
        if (playerCredits < price) {
            text.addPara("You don't have enough credits for this restock.");
            text.addPara("Required: " + Misc.getDGSCredits(price));
            return true;
        }

        MarketAPI market = ChickenQuestUtils.getChickenMarket();
        if (market == null || !market.hasSubmarket("chicken_market")) {
            text.addPara("Chicken can't access the tech market right now.");
            return true;
        }

        TechMarket submarket = (TechMarket) market.getSubmarket("chicken_market").getPlugin();
        if (submarket.getTechMarketLevel() < CTS_Config.get().luckyRestockMinLevel) {
            text.addPara("Chicken isn't ready to run premium restocks yet.");
            return true;
        }

        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(price);
        AddRemoveCommodity.addCreditsLossText(price, text);
        submarket.addCreditsFromSpending(price);
        submarket.triggerLuckyStockRefresh();
        clearStoredPrice();
        return true;
    }

    private boolean decline(InteractionDialogAPI dialog) {
        return true;
    }

    private int rollPrice() {
        Random random = new Random();
        int rolled = (int) Math.round(
                CTS_Config.get().luckyPriceMean + random.nextGaussian() * CTS_Config.get().luckyPriceStd);
        return Math.max(CTS_Config.get().luckyPriceFloor, rolled);
    }

    private int getOrCreateStoredPrice() {
        MarketAPI market = ChickenQuestUtils.getChickenMarket();
        CTS_Config cfg = CTS_Config.get();
        if (market == null) {
            return cfg.luckyPriceMean;
        }

        MemoryAPI marketMemory = market.getMemoryWithoutUpdate();
        if (marketMemory == null) {
            return cfg.luckyPriceMean;
        }

        boolean hasPrice = marketMemory.contains(PRICE_MEMORY_KEY);
        boolean hasCfgSnapshot = marketMemory.contains(PRICE_CFG_MEAN_KEY)
                && marketMemory.contains(PRICE_CFG_STD_KEY)
                && marketMemory.contains(PRICE_CFG_FLOOR_KEY);

        boolean configChanged = true;
        if (hasCfgSnapshot) {
            int storedMean = Math.round(marketMemory.getFloat(PRICE_CFG_MEAN_KEY));
            int storedStd = Math.round(marketMemory.getFloat(PRICE_CFG_STD_KEY));
            int storedFloor = Math.round(marketMemory.getFloat(PRICE_CFG_FLOOR_KEY));
            configChanged = storedMean != cfg.luckyPriceMean
                    || storedStd != cfg.luckyPriceStd
                    || storedFloor != cfg.luckyPriceFloor;
        }

        if (!hasPrice || !hasCfgSnapshot || configChanged) {
            int price = rollPrice();
            marketMemory.set(PRICE_MEMORY_KEY, price, 0f);
            marketMemory.set(PRICE_CFG_MEAN_KEY, cfg.luckyPriceMean, 0f);
            marketMemory.set(PRICE_CFG_STD_KEY, cfg.luckyPriceStd, 0f);
            marketMemory.set(PRICE_CFG_FLOOR_KEY, cfg.luckyPriceFloor, 0f);
            return price;
        }

        float stored = marketMemory.getFloat(PRICE_MEMORY_KEY);
        int price = Math.round(stored);
        if (price <= 0) {
            price = rollPrice();
            marketMemory.set(PRICE_MEMORY_KEY, price, 0f);
            marketMemory.set(PRICE_CFG_MEAN_KEY, cfg.luckyPriceMean, 0f);
            marketMemory.set(PRICE_CFG_STD_KEY, cfg.luckyPriceStd, 0f);
            marketMemory.set(PRICE_CFG_FLOOR_KEY, cfg.luckyPriceFloor, 0f);
        }
        return price;
    }

    private void clearStoredPrice() {
        MarketAPI market = ChickenQuestUtils.getChickenMarket();
        if (market == null) {
            return;
        }
        MemoryAPI marketMemory = market.getMemoryWithoutUpdate();
        if (marketMemory == null) {
            return;
        }
        marketMemory.unset(PRICE_MEMORY_KEY);
        marketMemory.unset(PRICE_CFG_MEAN_KEY);
        marketMemory.unset(PRICE_CFG_STD_KEY);
        marketMemory.unset(PRICE_CFG_FLOOR_KEY);
    }
}
