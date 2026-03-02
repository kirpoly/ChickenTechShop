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
import com.fs.starfarer.api.characters.PersonAPI;
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

import java.util.ArrayList;
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
    private static final int[] LEVEL_COSTS_BOOTSTRAP = { 250000, 500000, 900000, 1400000 };

    private int techMarketLevel = 1;
    private int currentCredits = 0;
    private int luckyRestockCharges = 0;
    private int queuedLuckyCategory = LUCKY_CATEGORY_NONE;
    private transient int activeLuckyCategory = LUCKY_CATEGORY_NONE;
    private int[] levelCosts = LEVEL_COSTS_BOOTSTRAP.clone();
    private int progressionDataVersion = PROGRESSION_DATA_VERSION;
    private static final int LUCKY_CATEGORY_NONE = 0;
    private static final int LUCKY_CATEGORY_SPECIAL = 1;
    private static final int LUCKY_CATEGORY_AI_CORES = 2;
    private static final int LUCKY_CATEGORY_WEAPON_BPS = 3;
    private static final int LUCKY_CATEGORY_FIGHTER_BPS = 4;
    private static final int LUCKY_CATEGORY_SHIP_BPS = 5;
    private static final List<String> VANILLA_SPECIAL_ITEM_TAGS = Arrays.asList("pather4", "hist3t");
    private static final Set<String> DIY_PLANETS_SPECIAL_ITEM_IDS = new HashSet<String>(Arrays.asList(
            "atmo_mineralizer", "atmo_sublimator", "solar_reflector",
            "tectonic_attenuator", "weather_core", "climate_sculptor", "gravity_oscillator", "rad_remover"));

    private static final Object ITEM_POOL_LOCK = new Object();
    private static List<String> cachedSpecialItemIds;
    private static List<String> cachedWeaponBlueprintIds;
    private static List<String> cachedFighterBlueprintIds;
    private static List<String> cachedShipBlueprintIds;
    private static List<ModdedAiCoreCandidate> cachedModdedAiCoreCandidates;
    private static int cachedModdedAiCoreMinBasePrice = Integer.MIN_VALUE;

    private static final class ModdedAiCoreCandidate {
        private final String commodityId;
        private final float basePrice;

        private ModdedAiCoreCandidate(String commodityId, float basePrice) {
            this.commodityId = commodityId;
            this.basePrice = basePrice;
        }
    }

    public int getTechMarketLevel() {
        return techMarketLevel;
    }

    private CTS_Config cfg() {
        return CTS_Config.get();
    }

    public static void invalidateItemPools() {
        synchronized (ITEM_POOL_LOCK) {
            cachedSpecialItemIds = null;
            cachedWeaponBlueprintIds = null;
            cachedFighterBlueprintIds = null;
            cachedShipBlueprintIds = null;
            cachedModdedAiCoreCandidates = null;
            cachedModdedAiCoreMinBasePrice = Integer.MIN_VALUE;
        }
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
        if (credits <= 0) {
            return;
        }

        float clampedMultiplier = Math.max(0f, contributionMultiplier);
        if (clampedMultiplier <= 0f) {
            return;
        }
        int creditedAmount = Math.round(credits * clampedMultiplier);
        if (creditedAmount <= 0) {
            return;
        }
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
        int restockIntervalDays = Math.max(1, cfg().restockIntervalDays);
        if (sinceLastCargoUpdate < restockIntervalDays)
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
        try {
            activeLuckyCategory = LUCKY_CATEGORY_NONE;
            if (luckyRestockCharges > 0) {
                activeLuckyCategory = queuedLuckyCategory;
                luckyRestockCharges--;
                if (luckyRestockCharges <= 0) {
                    queuedLuckyCategory = LUCKY_CATEGORY_NONE;
                }
            }
            ensureItemPoolsBuilt();
            clearInventory(cargo);
            addSpecialTech();
            addAICores();
            addBlueprints();
            cargo.sort();
        } finally {
            activeLuckyCategory = LUCKY_CATEGORY_NONE;
        }
    }

    private void clearInventory(CargoAPI cargo) {
        for (CargoStackAPI s : cargo.getStacksCopy()) {
            float qty = s.getSize();
            cargo.removeItems(s.getType(), s.getData(), qty);
        }
        cargo.removeEmptyStacks();
    }

    protected void addSpecialTech() {
        CargoAPI cargo = getCargo();
        WeightedRandomPicker<String> randomSpecialPicker = new WeightedRandomPicker<>(itemGenRandom);
        for (String itemId : cachedSpecialItemIds) {
            randomSpecialPicker.add(itemId);
        }

        int totalItems = randomSpecialPicker.getItems().size();
        int itemPickerNum = getSpecialItemPickCount(totalItems);
        for (int i = 0; i < itemPickerNum; i++) {
            if (!randomSpecialPicker.isEmpty()) {
                String itemID = randomSpecialPicker.pickAndRemove();
                int quantity = rollSpecialItemQuantity();
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
        int gamma = rollCoreQuantityAroundBase(getLevelValue(gammaByLevel, 0));
        int beta = rollCoreQuantityAroundBase(getLevelValue(betaByLevel, 0));
        int alpha = rollCoreQuantityAroundBase(getLevelValue(alphaByLevel, 0));

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
        addBlueprintsFromPool(cachedWeaponBlueprintIds, Items.WEAPON_BP, cfg().blueprintMaxWeapons,
                LUCKY_CATEGORY_WEAPON_BPS);
    }

    protected void addWingsBlueprints() {
        addBlueprintsFromPool(cachedFighterBlueprintIds, Items.FIGHTER_BP, cfg().blueprintMaxFighters,
                LUCKY_CATEGORY_FIGHTER_BPS);
    }

    protected void addShipsBlueprints() {
        addBlueprintsFromPool(cachedShipBlueprintIds, Items.SHIP_BP, cfg().blueprintMaxShips,
                LUCKY_CATEGORY_SHIP_BPS);
    }

    private void addBlueprintsFromPool(List<String> pool, String blueprintItemId, int[] levelCaps, int luckyCategory) {
        CargoAPI cargo = getCargo();
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(itemGenRandom);
        for (String id : pool) {
            picker.add(id);
        }

        int levelCap = Math.max(0, getLevelValue(levelCaps, 0));
        int itemPickerNum = getPicksFromPool(picker.getItems().size(), cfg().blueprintPoolFraction, levelCap);
        itemPickerNum = applyBlueprintBonusPicks(itemPickerNum, picker.getItems().size(), luckyCategory);
        for (int i = 0; i < itemPickerNum; i++) {
            if (!picker.isEmpty()) {
                String itemID = picker.pickAndRemove();
                cargo.addSpecial(new SpecialItemData(blueprintItemId, itemID), 1);
            }
        }
    }

    private int rollSpecialItemQuantity() {
        if (isLuckyCategory(LUCKY_CATEGORY_SPECIAL)) {
            if (techMarketLevel >= 5 && itemGenRandom.nextFloat() < cfg().luckySpecialQty3ChanceAtLevel5) {
                return 3;
            }
            if (itemGenRandom.nextFloat() < cfg().luckySpecialQty2Chance) {
                return 2;
            }
            return 1;
        }
        if (techMarketLevel >= cfg().specialQty2MinLevel && itemGenRandom.nextFloat() < cfg().specialQty2Chance) {
            return 2;
        }
        return 1;
    }

    private void ensureItemPoolsBuilt() {
        int requiredMinPrice = cfg().moddedAiCoreMinBasePrice;
        synchronized (ITEM_POOL_LOCK) {
            boolean needsRebuild = cachedSpecialItemIds == null
                    || cachedWeaponBlueprintIds == null
                    || cachedFighterBlueprintIds == null
                    || cachedShipBlueprintIds == null
                    || cachedModdedAiCoreCandidates == null
                    || cachedModdedAiCoreMinBasePrice != requiredMinPrice;
            if (!needsRebuild) {
                return;
            }

            cachedSpecialItemIds = buildSpecialItemPool();
            cachedWeaponBlueprintIds = buildWeaponBlueprintPool();
            cachedFighterBlueprintIds = buildFighterBlueprintPool();
            cachedShipBlueprintIds = buildShipBlueprintPool();
            cachedModdedAiCoreCandidates = buildModdedAiCoreCandidatePool(requiredMinPrice);
            cachedModdedAiCoreMinBasePrice = requiredMinPrice;
        }
    }

    private List<String> buildSpecialItemPool() {
        Set<String> specialItemIds = new HashSet<String>();
        for (SpecialItemSpecAPI spec : Global.getSettings().getAllSpecialItemSpecs()) {
            if (spec == null) {
                continue;
            }
            boolean hasVanillaTag = false;
            for (String tag : VANILLA_SPECIAL_ITEM_TAGS) {
                if (spec.hasTag(tag)) {
                    hasVanillaTag = true;
                    break;
                }
            }
            if (hasVanillaTag || DIY_PLANETS_SPECIAL_ITEM_IDS.contains(spec.getId())) {
                specialItemIds.add(spec.getId());
            }
        }
        return new ArrayList<String>(specialItemIds);
    }

    private List<String> buildWeaponBlueprintPool() {
        List<String> ids = new ArrayList<String>();
        for (WeaponSpecAPI spec : Global.getSettings().getAllWeaponSpecs()) {
            if (spec.hasTag("rare_bp") && !spec.hasTag(Tags.NO_DROP) && !spec.hasTag(Tags.NO_BP_DROP)) {
                ids.add(spec.getWeaponId());
            }
        }
        return ids;
    }

    private List<String> buildFighterBlueprintPool() {
        List<String> ids = new ArrayList<String>();
        for (FighterWingSpecAPI spec : Global.getSettings().getAllFighterWingSpecs()) {
            if (spec.hasTag("rare_bp") && !spec.hasTag(Tags.NO_DROP) && !spec.hasTag(Tags.NO_BP_DROP)) {
                ids.add(spec.getId());
            }
        }
        return ids;
    }

    private List<String> buildShipBlueprintPool() {
        List<String> ids = new ArrayList<String>();
        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
            if (spec.hasTag("rare_bp") && !spec.hasTag(Tags.NO_DROP) && !spec.hasTag(Tags.NO_BP_DROP)) {
                ids.add(spec.getHullId());
            }
        }
        return ids;
    }

    private List<ModdedAiCoreCandidate> buildModdedAiCoreCandidatePool(int minBasePrice) {
        List<ModdedAiCoreCandidate> ids = new ArrayList<ModdedAiCoreCandidate>();
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
            if (spec.getBasePrice() < minBasePrice) {
                continue;
            }
            ids.add(new ModdedAiCoreCandidate(spec.getId(), Math.max(1f, spec.getBasePrice())));
        }
        return ids;
    }

    private int getLevelValue(int[] values, int fallback) {
        if (values == null || values.length == 0) {
            return fallback;
        }
        int levelIndex = Math.max(0, Math.min(techMarketLevel - 1, values.length - 1));
        return values[levelIndex];
    }

    private float getLevelValue(float[] values, float fallback) {
        if (values == null || values.length == 0) {
            return fallback;
        }
        int levelIndex = Math.max(0, Math.min(techMarketLevel - 1, values.length - 1));
        return values[levelIndex];
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
        PersonAPI chicken = ChickenQuestUtils.getChickenOrNull();
        if (chicken == null) {
            return cfg().tariffNeutral;
        }
        RepLevel chicken_repLevel = chicken.getRelToPlayer().getLevel();
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
        float levelFraction = Math.max(0f, getLevelValue(progressionFraction, 0f));
        int byFraction = Math.max(1, Math.round(poolSize * levelFraction));
        return Math.min(byFraction, levelCap);
    }

    private int getSpecialItemPickCount(int poolSize) {
        if (poolSize <= 0) {
            return 0;
        }
        int hardCap = Math.max(1, getLevelValue(cfg().specialItemMaxTotal, 1));

        int floor = hardCap;
        if (techMarketLevel >= 2) {
            floor = Math.max(1, hardCap - 1);
        }
        int picks = floor;
        if (hardCap > floor) {
            picks += itemGenRandom.nextInt(hardCap - floor + 1);
        }

        if (isLuckyCategory(LUCKY_CATEGORY_SPECIAL)) {
            int luckyBonus = techMarketLevel >= cfg().luckySpecialHighLevelThreshold
                    ? cfg().luckySpecialExtraPicksHighLevel
                    : cfg().luckySpecialExtraPicksLowLevel;
            picks = hardCap + Math.max(0, luckyBonus);
        }

        return Math.min(poolSize, picks);
    }

    private int applyBlueprintBonusPicks(int basePicks, int poolSize, int luckyCategory) {
        if (poolSize <= 0) {
            return 0;
        }
        int minBonus = 0;
        int maxBonus = 0;
        if (techMarketLevel >= cfg().blueprintBonusUnlockLevel) {
            minBonus = cfg().blueprintBonusBaseMin;
            maxBonus = cfg().blueprintBonusBaseMax;
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
        return itemGenRandom.nextInt(LUCKY_CATEGORY_SHIP_BPS) + 1;
    }

    private void addRandomModdedAICore(CargoAPI cargo) {
        if (itemGenRandom.nextFloat() > cfg().moddedAiCoreRestockChance) {
            return;
        }

        List<ModdedAiCoreCandidate> candidates;
        synchronized (ITEM_POOL_LOCK) {
            candidates = cachedModdedAiCoreCandidates;
        }
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(itemGenRandom);
        for (ModdedAiCoreCandidate candidate : candidates) {
            float price = Math.max(1f, candidate.basePrice);
            float weight = (float) (1d / Math.pow(price, cfg().moddedAiCorePriceWeightExponent));
            picker.add(candidate.commodityId, Math.max(0.0001f, weight));
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
        if (configuredCosts == null || configuredCosts.length == 0) {
            configuredCosts = LEVEL_COSTS_BOOTSTRAP;
        }

        int levelIndex = Math.max(0, Math.min(getTechMarketLevel() - 1, configuredCosts.length - 1));

        if (progressionDataVersion < PROGRESSION_DATA_VERSION) {
            int[] sourceCosts = levelCosts;
            // If old saves don't have serialized costs and got class defaults, prefer known legacy ladder.
            if (sourceCosts == null || sourceCosts.length <= levelIndex || Arrays.equals(sourceCosts, LEVEL_COSTS_BOOTSTRAP)) {
                sourceCosts = LEVEL_COSTS_LEGACY;
            }
            remapProgressCreditsForCostChange(costAt(sourceCosts, levelIndex, LEVEL_COSTS_LEGACY[levelIndex]),
                    costAt(configuredCosts, levelIndex, LEVEL_COSTS_BOOTSTRAP[levelIndex]));
            levelCosts = configuredCosts.clone();
            progressionDataVersion = PROGRESSION_DATA_VERSION;
            return;
        }

        // Settings changed in JSON/Luna for an existing save: preserve same progression ratio.
        if (!Arrays.equals(levelCosts, configuredCosts)) {
            remapProgressCreditsForCostChange(costAt(levelCosts, levelIndex, configuredCosts[levelIndex]),
                    costAt(configuredCosts, levelIndex, LEVEL_COSTS_BOOTSTRAP[levelIndex]));
            levelCosts = configuredCosts.clone();
        }
    }

    private int costAt(int[] costs, int levelIndex, int fallback) {
        if (costs == null || levelIndex < 0 || levelIndex >= costs.length || costs[levelIndex] <= 0) {
            return Math.max(1, fallback);
        }
        return costs[levelIndex];
    }

    private void remapProgressCreditsForCostChange(int oldCostForLevel, int newCostForLevel) {
        int safeOld = Math.max(1, oldCostForLevel);
        int safeNew = Math.max(1, newCostForLevel);

        if (getTechMarketLevel() >= 5) {
            currentCredits = 0;
            return;
        }

        float progressRatio = currentCredits / (float) safeOld;
        progressRatio = Math.max(0f, Math.min(1f, progressRatio));
        currentCredits = Math.round(progressRatio * safeNew);
        currentCredits = Math.max(0, Math.min(currentCredits, safeNew - 1));
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
