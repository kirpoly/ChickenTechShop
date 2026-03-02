package chickentechshop.campaign.submarkets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import chickentechshop.config.CTS_Config;
import chickentechshop.campaign.intel.missions.chicken.ChickenQuestUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

public class TechMarket extends BaseSubmarketPlugin {

    public static RepLevel MIN_STANDING = RepLevel.VENGEFUL;
    public static Logger log = Global.getLogger(TechMarket.class);
    private static final int PROGRESSION_DATA_VERSION = 2;
    private static final int[] LEVEL_COSTS_LEGACY = { 100000, 150000, 200000, 250000 };
    private static final int[] LEVEL_COSTS_CURRENT = { 250000, 500000, 900000, 1400000 };

    private int techMarketLevel = 1;
    private int currentCredits = 0;
    private int luckyRestockCharges = 0;
    private int queuedLuckyCategory = LUCKY_CATEGORY_NONE;
    private transient int activeLuckyCategory = LUCKY_CATEGORY_NONE;
    private int[] levelCosts = LEVEL_COSTS_CURRENT.clone();
    private int progressionDataVersion = PROGRESSION_DATA_VERSION;
    private static final int LUCKY_CATEGORY_NONE = 0;
    private static final int LUCKY_CATEGORY_SPECIAL = 1;
    private static final int LUCKY_CATEGORY_AI_CORES = 2;
    private static final int LUCKY_CATEGORY_WEAPON_BPS = 3;
    private static final int LUCKY_CATEGORY_FIGHTER_BPS = 4;
    private static final int LUCKY_CATEGORY_SHIP_BPS = 5;

    public int getTechMarketLevel() {
        return techMarketLevel;
    }

    private CTS_Config cfg() {
        return CTS_Config.get();
    }

    // Tech Market can be 1 to 5 inclusive
    public void setTechMarketLevel(int newLevel) {
        if (newLevel < 1) {
            techMarketLevel = 1;
        } else if (newLevel > 5) {
            techMarketLevel = 5;
        } else {
            techMarketLevel = newLevel;
        }
    }

    // Adding credits is the main way we increase our TechLevel
    public void addCreditsToTechMarket(int credits) {
        addCreditsToTechMarketWithMultiplier(credits, cfg().missionCreditContribution);
    }

    public void addCreditsToTechMarketWithMultiplier(int credits, float contributionMultiplier) {
        migrateProgressionDataIfNeeded();
        // The max level is 5
        if (techMarketLevel >= 5) {
            return;
        }

        float clampedMultiplier = Math.max(0f, contributionMultiplier);
        int creditedAmount = Math.max(1, Math.round(credits * clampedMultiplier));
        currentCredits += creditedAmount;
        while (techMarketLevel < 5 && currentCredits >= levelCosts[getTechMarketLevel() - 1]) {
            currentCredits -= levelCosts[getTechMarketLevel() - 1];
            setTechMarketLevel(getTechMarketLevel() + 1);
        }
    }

    public String ToNextLevelCreditsString() {
        migrateProgressionDataIfNeeded();
        if (getTechMarketLevel() == 5) {
            return "";
        }
        return currentCredits + "/" + levelCosts[getTechMarketLevel() - 1];
    }

    @Override
    public void updateCargoPrePlayerInteraction() {
        migrateProgressionDataIfNeeded();
        // log.info("Days since update: " + sinceLastCargoUpdate);
        if (sinceLastCargoUpdate < 30)
            return;
        sinceLastCargoUpdate = 0f;
        updateCargo();
    }

    // Force update the Marketplace
    public void updateCargoForce() {
        migrateProgressionDataIfNeeded();
        sinceLastCargoUpdate = 0f;
        updateCargo();
    }

    public void updateCargo() {
        CargoAPI cargo = getCargo();
        activeLuckyCategory = LUCKY_CATEGORY_NONE;
        if (luckyRestockCharges > 0) {
            activeLuckyCategory = queuedLuckyCategory;
            luckyRestockCharges--;
            if (luckyRestockCharges <= 0) {
                queuedLuckyCategory = LUCKY_CATEGORY_NONE;
            }
        }

        // clear inventory
        for (CargoStackAPI s : cargo.getStacksCopy()) {
            float qty = s.getSize();
            cargo.removeItems(s.getType(), s.getData(), qty);
        }
        cargo.removeEmptyStacks();
        addSpecialTech();
        addAICores();
        addBlueprints();
        cargo.sort();
        activeLuckyCategory = LUCKY_CATEGORY_NONE;
    }

    // addSpecialTech adds tech items to shop, such as colony items, AI cores and
    // blueprints
    // For now, just getting basic items to work!
    protected void addSpecialTech() {
        CargoAPI cargo = getCargo();
        Set<String> vanillaSpecialItemsList = new HashSet<String>();
        Set<String> diyPlanetsSpecialItemsList = new HashSet<String>();
        final List<String> vanillaItemTags = Arrays.asList("pather4", "hist3t");
        final List<String> diyPlanetsItemIDs = Arrays.asList(
                "atmo_mineralizer", "atmo_sublimator", "solar_reflector",
                "tectonic_attenuator", "weather_core", "climate_sculptor", "gravity_oscillator", "rad_remover");

        // Get all the items to add via tags or hardcoded ids
        for (SpecialItemSpecAPI spec : Global.getSettings().getAllSpecialItemSpecs()) {
            boolean hasVanillaTag = false;
            for (String tag : vanillaItemTags) {
                if (spec.hasTag(tag)) {
                    hasVanillaTag = true;
                    break;
                }
            }
            if (hasVanillaTag) {
                vanillaSpecialItemsList.add(spec.getId());
                continue;
            }
            if (diyPlanetsItemIDs.contains(spec.getId())) {
                diyPlanetsSpecialItemsList.add(spec.getId());
            }
        }

        // Now Pick based on the techMarketLevel
        // Take random 20% of total items per market level
        // Use 20% of each list for more even distribution
        // Quantity is random number from 1 to market level
        // Make our random picker list
        WeightedRandomPicker<String> randomVanillaPicker = new WeightedRandomPicker<>(itemGenRandom);
        WeightedRandomPicker<String> randomDIYPicker = new WeightedRandomPicker<>(itemGenRandom);
        for (String itemId : vanillaSpecialItemsList) {
            randomVanillaPicker.add(itemId);
        }
        for (String itemId : diyPlanetsSpecialItemsList) {
            randomDIYPicker.add(itemId);
        }

        int totalItems = randomVanillaPicker.getItems().size() + randomDIYPicker.getItems().size();

        // Then add the items
        int itemPickerNum = getPicksFromPool(totalItems, cfg().specialItemPoolFraction, totalItems);
        if (isLuckyCategory(LUCKY_CATEGORY_SPECIAL)) {
            int luckyExtraPicks = techMarketLevel >= 4 ? cfg().luckySpecialExtraPicksHighLevel
                    : cfg().luckySpecialExtraPicksLowLevel;
            itemPickerNum = Math.min(totalItems, itemPickerNum + luckyExtraPicks);
        }
        for (int i = 0; i < itemPickerNum; i++) {
            if (!randomVanillaPicker.isEmpty()) {
                String itemID = randomVanillaPicker.pickAndRemove();
                int quantity = 1;
                if (isLuckyCategory(LUCKY_CATEGORY_SPECIAL)) {
                    if (itemGenRandom.nextFloat() < cfg().luckySpecialQty2Chance) {
                        quantity = 2;
                    }
                    if (techMarketLevel >= 5 && itemGenRandom.nextFloat() < cfg().luckySpecialQty3ChanceAtLevel5) {
                        quantity = 3;
                    }
                } else if (techMarketLevel >= 4 && itemGenRandom.nextFloat() < 0.35f) {
                    quantity = 2;
                }
                log.info("Trying to add " + itemID + " with quantity " + quantity);
                cargo.addSpecial(new SpecialItemData(itemID, null), quantity);
            }
            if (!randomDIYPicker.isEmpty()) {
                String itemID = randomDIYPicker.pickAndRemove();
                int quantity = 1;
                if (isLuckyCategory(LUCKY_CATEGORY_SPECIAL)) {
                    if (itemGenRandom.nextFloat() < cfg().luckySpecialQty2Chance) {
                        quantity = 2;
                    }
                    if (techMarketLevel >= 5 && itemGenRandom.nextFloat() < cfg().luckySpecialQty3ChanceAtLevel5) {
                        quantity = 3;
                    }
                } else if (techMarketLevel >= 4 && itemGenRandom.nextFloat() < 0.35f) {
                    quantity = 2;
                }
                log.info("Trying to add " + itemID + " with quantity " + quantity);
                cargo.addSpecial(new SpecialItemData(itemID, null), quantity);
            }
        }
    }

    // Add a number of Gamma/Beta/Alpha Cores
    // Beta Cores "unlock" at Market Level 2
    // Alpha Cores "unlock" at Market Level 4
    protected void addAICores() {
        CargoAPI cargo = getCargo();
        final int[] gammaByLevel = cfg().aiGammaBase;
        final int[] betaByLevel = cfg().aiBetaBase;
        final int[] alphaByLevel = cfg().aiAlphaBase;
        int levelIndex = techMarketLevel - 1;
        int gamma = rollCoreQuantityAroundBase(gammaByLevel[levelIndex]);
        int beta = rollCoreQuantityAroundBase(betaByLevel[levelIndex]);
        int alpha = rollCoreQuantityAroundBase(alphaByLevel[levelIndex]);

        if (isLuckyCategory(LUCKY_CATEGORY_AI_CORES)) {
            gamma += cfg().luckyCoreBonusPerUnlockedTier;
            if (beta > 0) {
                beta += cfg().luckyCoreBonusPerUnlockedTier;
            }
            if (alpha > 0) {
                alpha += cfg().luckyCoreBonusPerUnlockedTier;
            }
        }

        // Add Gammas
        cargo.addCommodity("gamma_core", gamma);

        // Add Betas
        if (beta > 0) {
            cargo.addCommodity("beta_core", beta);
        }

        // Add Alphas
        if (alpha > 0) {
            cargo.addCommodity("alpha_core", alpha);
        }

        addRandomModdedAICore(cargo);

    }

    protected void addBlueprints() {
        addWeaponBlueprints();
        addWingsBlueprints();
        addShipsBlueprints();
    }

    protected void addWeaponBlueprints() {
        CargoAPI cargo = getCargo();
        List<WeaponSpecAPI> weaponSpecs = Global.getSettings().getAllWeaponSpecs();
        WeightedRandomPicker<String> randomWeaponPicker = new WeightedRandomPicker<>(itemGenRandom);

        for (WeaponSpecAPI spec : weaponSpecs) {
            // Check if this is not a blueprint that should drop etc
            if (!spec.hasTag("rare_bp") || spec.hasTag(Tags.NO_DROP) || spec.hasTag(Tags.NO_BP_DROP)) {
                continue;
            }
            // Check if player already knows this weapon?
            // if (Global.getSector().getPlayerFaction().knowsWeapon(spec.getWeaponId())) {
            // continue;
            // }
            // Add the data to our picker
            randomWeaponPicker.add(spec.getWeaponId());
        }

        // Now make our Blueprints
        int itemPickerNum = getPicksFromPool(randomWeaponPicker.getItems().size(), cfg().blueprintPoolFraction,
                cfg().blueprintMaxWeapons[techMarketLevel - 1]);
        itemPickerNum = applyBlueprintBonusPicks(itemPickerNum, randomWeaponPicker.getItems().size(),
                LUCKY_CATEGORY_WEAPON_BPS);
        // log.info("randomWeaponPicker has " + randomWeaponPicker.getItems().size());
        // log.info("num picked for weapons is " + itemPickerNum);
        for (int i = 0; i < itemPickerNum; i++) {
            if (!randomWeaponPicker.isEmpty()) {
                String itemID = randomWeaponPicker.pickAndRemove();
                // Only need 1 of each Blueprint
                // log.info("Trying to add Weapon blueprint for " + itemID);
                cargo.addSpecial(new SpecialItemData(Items.WEAPON_BP, itemID), 1);
            }
        }
    }

    protected void addWingsBlueprints() {
        CargoAPI cargo = getCargo();
        List<FighterWingSpecAPI> fighterSpecs = Global.getSettings().getAllFighterWingSpecs();
        WeightedRandomPicker<String> randomFighterPicker = new WeightedRandomPicker<>(itemGenRandom);

        for (FighterWingSpecAPI spec : fighterSpecs) {
            // Check if this is not a blueprint that should drop etc
            if (!spec.hasTag("rare_bp") || spec.hasTag(Tags.NO_DROP) || spec.hasTag(Tags.NO_BP_DROP)) {
                continue;
            }
            // Check if player already knows this weapon?
            // if (Global.getSector().getPlayerFaction().knowsWeapon(spec.getWeaponId())) {
            // continue;
            // }
            // Add the data to our picker
            randomFighterPicker.add(spec.getId());
        }

        // Now make our Blueprints
        int itemPickerNum = getPicksFromPool(randomFighterPicker.getItems().size(), cfg().blueprintPoolFraction,
                cfg().blueprintMaxFighters[techMarketLevel - 1]);
        itemPickerNum = applyBlueprintBonusPicks(itemPickerNum, randomFighterPicker.getItems().size(),
                LUCKY_CATEGORY_FIGHTER_BPS);
        // log.info("randomFighterPicker has " + randomFighterPicker.getItems().size());
        // log.info("num picked for fighters is " + itemPickerNum);
        for (int i = 0; i < itemPickerNum; i++) {
            if (!randomFighterPicker.isEmpty()) {
                String itemID = randomFighterPicker.pickAndRemove();
                // Only need 1 of each Blueprint
                // log.info("Trying to add Fighter blueprint for " + itemID);
                cargo.addSpecial(new SpecialItemData(Items.FIGHTER_BP, itemID), 1);
            }
        }
    }

    protected void addShipsBlueprints() {
        CargoAPI cargo = getCargo();
        List<ShipHullSpecAPI> hullSpecs = Global.getSettings().getAllShipHullSpecs();
        WeightedRandomPicker<String> randomHullPicker = new WeightedRandomPicker<>(itemGenRandom);

        for (ShipHullSpecAPI spec : hullSpecs) {
            // Check if this is not a blueprint that should drop etc
            if (!spec.hasTag("rare_bp") || spec.hasTag(Tags.NO_DROP) || spec.hasTag(Tags.NO_BP_DROP)) {
                continue;
            }
            // Check if player already knows this weapon?
            // if (Global.getSector().getPlayerFaction().knowsWeapon(spec.getWeaponId())) {
            // continue;
            // }
            // Add the data to our picker
            randomHullPicker.add(spec.getHullId());
        }

        // Now make our Blueprints
        int itemPickerNum = getPicksFromPool(randomHullPicker.getItems().size(), cfg().blueprintPoolFraction,
                cfg().blueprintMaxShips[techMarketLevel - 1]);
        itemPickerNum = applyBlueprintBonusPicks(itemPickerNum, randomHullPicker.getItems().size(),
                LUCKY_CATEGORY_SHIP_BPS);
        // log.info("randomHullPicker has " + randomHullPicker.getItems().size());
        // log.info("num picked for hulls is " + itemPickerNum);
        for (int i = 0; i < itemPickerNum; i++) {
            if (!randomHullPicker.isEmpty()) {
                String itemID = randomHullPicker.pickAndRemove();
                // Only need 1 of each Blueprint
                // log.info("Trying to add Hull blueprint for " + itemID);
                cargo.addSpecial(new SpecialItemData(Items.SHIP_BP, itemID), 1);
            }
        }

    }

    // ==========================================================================
    // ==========================================================================

    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        if (action == TransferAction.PLAYER_SELL)
            return true;
        return false;
    }

    @Override
    public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
        if (action == TransferAction.PLAYER_SELL)
            return true;
        return false;
    }

    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        if (action == TransferAction.PLAYER_SELL)
            return true;
        return false;
    }

    @Override
    public float getTariff() {
        migrateProgressionDataIfNeeded();
        RepLevel chicken_repLevel = Global.getSector().getImportantPeople().getPerson(ChickenQuestUtils.PERSON_CHICKEN)
                .getRelToPlayer().getLevel();
        float mult;
        switch (chicken_repLevel) {
            case NEUTRAL:
                mult = cfg().tariffNeutral;
                break;
            case FAVORABLE:
                mult = cfg().tariffFavorable;
                break;
            case WELCOMING:
                mult = cfg().tariffWelcoming;
                break;
            case FRIENDLY:
                mult = cfg().tariffFriendly;
                break;
            case COOPERATIVE:
                mult = cfg().tariffCooperative;
                break;
            default:
                mult = cfg().tariffNeutral;
        }
        return mult;
    }

    private int getPicksFromPool(int poolSize, float[] progressionFraction, int levelCap) {
        if (poolSize <= 0) {
            return 0;
        }
        int levelIndex = techMarketLevel - 1;
        int byFraction = Math.max(1, Math.round(poolSize * progressionFraction[levelIndex]));
        return Math.min(byFraction, levelCap);
    }

    private int applyBlueprintBonusPicks(int basePicks, int poolSize, int luckyCategory) {
        if (poolSize <= 0) {
            return 0;
        }
        int minBonus = 0;
        int maxBonus = 0;
        if (techMarketLevel >= 4) {
            minBonus = 1;
            maxBonus = 2;
        }
        if (isLuckyCategory(luckyCategory)) {
            minBonus += cfg().luckyBpBonusExtraMin;
            maxBonus += cfg().luckyBpBonusExtraMax;
        }
        int bonusPicks = 0;
        if (maxBonus > 0) {
            bonusPicks = itemGenRandom.nextInt(maxBonus - minBonus + 1) + minBonus;
        }
        return Math.min(basePicks + bonusPicks, poolSize);
    }

    public void triggerLuckyStockRefresh() {
        migrateProgressionDataIfNeeded();
        luckyRestockCharges++;
        queuedLuckyCategory = rollLuckyCategory();
        updateCargoForce();
    }

    public void addCreditsFromSpending(int creditsSpent) {
        addCreditsToTechMarketWithMultiplier(creditsSpent, cfg().spendingCreditContribution);
    }

    private int rollCoreQuantityAroundBase(int baseQty) {
        if (baseQty <= 0) {
            return 0;
        }
        int randomDelta = Math.max(0, cfg().aiCoreRandomDelta);
        int delta = itemGenRandom.nextInt(randomDelta * 2 + 1) - randomDelta;
        return Math.max(1, baseQty + delta);
    }

    private int rollLuckyCategory() {
        return itemGenRandom.nextInt(5) + 1;
    }

    private void addRandomModdedAICore(CargoAPI cargo) {
        if (itemGenRandom.nextFloat() > cfg().moddedAiCoreRestockChance) {
            return;
        }

        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(itemGenRandom);
        for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {
            boolean isAiCoreLike = spec.hasTag(Commodities.TAG_AI_CORE)
                    || Commodities.AI_CORES.equals(spec.getDemandClass());
            if (!isAiCoreLike) {
                continue;
            }
            if (Commodities.AI_CORES.equals(spec.getId())
                    || Commodities.GAMMA_CORE.equals(spec.getId())
                    || Commodities.BETA_CORE.equals(spec.getId())
                    || Commodities.ALPHA_CORE.equals(spec.getId())
                    || Commodities.OMEGA_CORE.equals(spec.getId())) {
                continue;
            }
            if (spec.isMeta()) {
                continue;
            }
            if (spec.hasTag(Tags.NO_DROP)) {
                continue;
            }
            if (spec.hasTag("no_sell") || spec.hasTag("restricted") || spec.hasTag("hide_in_codex")) {
                continue;
            }
            if (spec.getBasePrice() < cfg().moddedAiCoreMinBasePrice) {
                continue;
            }

            float price = Math.max(1f, spec.getBasePrice());
            float weight = (float) (1d / Math.pow(price, cfg().moddedAiCorePriceWeightExponent));
            picker.add(spec.getId(), Math.max(0.0001f, weight));
        }

        if (picker.isEmpty()) {
            return;
        }

        String commodityId = picker.pick();
        int maxQty = Math.max(cfg().moddedAiCoreMinQty, cfg().moddedAiCoreMaxQty);
        int qtyRange = maxQty - cfg().moddedAiCoreMinQty + 1;
        int qty = cfg().moddedAiCoreMinQty + (qtyRange <= 1 ? 0 : itemGenRandom.nextInt(qtyRange));
        cargo.addCommodity(commodityId, Math.max(1, qty));
    }

    private boolean isLuckyCategory(int luckyCategory) {
        return activeLuckyCategory == luckyCategory;
    }

    public void migrateProgressionDataNow() {
        migrateProgressionDataIfNeeded();
    }

    private void migrateProgressionDataIfNeeded() {
        int[] configuredCosts = cfg().levelCosts;
        if (progressionDataVersion >= PROGRESSION_DATA_VERSION) {
            if (!Arrays.equals(levelCosts, configuredCosts)) {
                levelCosts = configuredCosts.clone();
            }
            return;
        }

        int levelIndex = Math.max(0, Math.min(getTechMarketLevel() - 1, configuredCosts.length - 1));
        int oldCostForLevel = LEVEL_COSTS_LEGACY[levelIndex];
        if (levelCosts != null && levelCosts.length > levelIndex && levelCosts[levelIndex] > 0) {
            oldCostForLevel = levelCosts[levelIndex];
        }
        int newCostForLevel = configuredCosts[levelIndex];

        if (getTechMarketLevel() >= 5) {
            currentCredits = 0;
        } else if (oldCostForLevel > 0) {
            float progressRatio = currentCredits / (float) oldCostForLevel;
            progressRatio = Math.max(0f, Math.min(1f, progressRatio));
            currentCredits = Math.round(progressRatio * newCostForLevel);
            currentCredits = Math.min(currentCredits, newCostForLevel - 1);
        } else {
            currentCredits = Math.max(0, Math.min(currentCredits, newCostForLevel - 1));
        }

        levelCosts = configuredCosts.clone();
        progressionDataVersion = PROGRESSION_DATA_VERSION;
    }

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        return "No sales/returns";
    }

    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
        return "No sales/returns";
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        super.reportPlayerMarketTransaction(transaction);
        if (transaction == null) {
            return;
        }
        if (transaction.getSubmarket() != null && transaction.getSubmarket().getPlugin() != this) {
            return;
        }
        CargoAPI bought = transaction.getBought();
        if (bought == null) {
            return;
        }

        float extraCredits = 0f;
        for (CargoStackAPI stack : bought.getStacksCopy()) {
            float priceMult = getStackPriceMultiplier(stack);
            if (priceMult <= 1f) {
                continue;
            }

            float baseValuePerUnit = getStackBaseValuePerUnit(stack);
            if (baseValuePerUnit <= 0f) {
                continue;
            }

            float estimatedPaidPerUnit = baseValuePerUnit * (1f + getTariff());
            extraCredits += estimatedPaidPerUnit * stack.getSize() * (priceMult - 1f);
        }

        if (extraCredits > 0f) {
            float currentCredits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
            float toSubtract = Math.min(currentCredits, extraCredits);
            if (toSubtract > 0f) {
                Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(toSubtract);
            }
        }
    }

    private float getStackPriceMultiplier(CargoStackAPI stack) {
        if (stack == null) {
            return 1f;
        }
        if (stack.isSpecialStack()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null) {
                return 1f;
            }
            if (Items.WEAPON_BP.equals(data.getId())) {
                return cfg().weaponBpPriceMult;
            }
            if (Items.FIGHTER_BP.equals(data.getId())) {
                return cfg().fighterBpPriceMult;
            }
            if (Items.SHIP_BP.equals(data.getId())) {
                return cfg().shipBpPriceMult;
            }
            return cfg().specialItemPriceMult;
        }
        CommoditySpecAPI commodity = stack.getResourceIfResource();
        if (isAiCoreCommodity(commodity)) {
            return cfg().aiCorePriceMult;
        }
        return 1f;
    }

    private boolean isAiCoreCommodity(CommoditySpecAPI commodity) {
        if (commodity == null) {
            return false;
        }
        if (Commodities.AI_CORES.equals(commodity.getId())) {
            return false;
        }
        return commodity.hasTag(Commodities.TAG_AI_CORE) || Commodities.AI_CORES.equals(commodity.getDemandClass());
    }

    private float getStackBaseValuePerUnit(CargoStackAPI stack) {
        if (stack == null) {
            return 0f;
        }
        if (stack.isSpecialStack()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null) {
                return 0f;
            }
            if (Items.WEAPON_BP.equals(data.getId()) || Items.FIGHTER_BP.equals(data.getId())
                    || Items.SHIP_BP.equals(data.getId())) {
                return getBlueprintBaseValuePerUnit(data, stack);
            }
            if (stack.getSpecialItemSpecIfSpecial() != null) {
                return stack.getSpecialItemSpecIfSpecial().getBasePrice();
            }
            return Math.max(0f, stack.getBaseValuePerUnit());
        }
        CommoditySpecAPI commodity = stack.getResourceIfResource();
        if (commodity != null) {
            float stackBase = stack.getBaseValuePerUnit();
            if (stackBase > 0f) {
                return stackBase;
            }
            return Math.max(0f, commodity.getBasePrice());
        }
        return Math.max(0f, stack.getBaseValuePerUnit());
    }

    private float getBlueprintBaseValuePerUnit(SpecialItemData data, CargoStackAPI stack) {
        if (data == null) {
            return 0f;
        }
        try {
            String blueprintId = data.getData();
            if (Items.WEAPON_BP.equals(data.getId()) && blueprintId != null) {
                WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(blueprintId);
                if (spec != null) {
                    return spec.getBaseValue();
                }
            }
            if (Items.FIGHTER_BP.equals(data.getId()) && blueprintId != null) {
                FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(blueprintId);
                if (spec != null) {
                    return spec.getBaseValue();
                }
            }
            if (Items.SHIP_BP.equals(data.getId()) && blueprintId != null) {
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(blueprintId);
                if (spec != null) {
                    return spec.getBaseValue();
                }
            }
        } catch (Exception ignored) {
            // Fall back below when an item id is invalid or unavailable.
        }

        float fallback = stack.getBaseValuePerUnit();
        if (fallback <= 0f && stack.getSpecialItemSpecIfSpecial() != null) {
            fallback = stack.getSpecialItemSpecIfSpecial().getBasePrice();
        }
        return fallback;
    }

    @Override
    public String getTooltipAppendix(CoreUIAPI ui) {
        if (getTechMarketLevel() == 5) {
            return "The Tech market is at the Max Level 5";
        }
        return "The Tech Market is currently at Level " + techMarketLevel + ".\nThe next level will be reach in "
                + ToNextLevelCreditsString() + " credits";
    }

    @Override
    public Highlights getTooltipAppendixHighlights(CoreUIAPI ui) {
        Highlights highlights = new Highlights();
        highlights.append("Level " + techMarketLevel, Misc.getHighlightColor());
        highlights.append(ToNextLevelCreditsString() + " Credits" + "", Misc.getHighlightColor());
        return highlights;
    }

    @Override
    public boolean isEnabled(CoreUIAPI ui) {
        RepLevel level = submarket.getFaction().getRelationshipLevel(Global.getSector().getFaction(Factions.PLAYER));
        return level.isAtWorst(MIN_STANDING);
    }

    @Override
    public boolean isBlackMarket() {
        return false;
    }

}
