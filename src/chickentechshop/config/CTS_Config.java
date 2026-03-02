package chickentechshop.config;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;

public class CTS_Config {

    public static final String MOD_ID = "chickentechshop";
    private static final String CONFIG_PATH = "data/config/cts_settings.json";
    private static Logger log = Global.getLogger(CTS_Config.class);
    private static CTS_Config current;

    public int[] levelCosts = { 250000, 500000, 900000, 1400000 };
    public float missionCreditContribution = 0.35f;
    public float spendingCreditContribution = 0.25f;

    public float[] specialItemPoolFraction = { 0.10f, 0.15f, 0.20f, 0.25f, 0.30f };
    public float[] blueprintPoolFraction = { 0.02f, 0.04f, 0.06f, 0.08f, 0.10f };
    public int[] blueprintMaxWeapons = { 2, 3, 4, 5, 6 };
    public int[] blueprintMaxFighters = { 1, 2, 3, 4, 5 };
    public int[] blueprintMaxShips = { 1, 2, 2, 3, 4 };

    public int[] aiGammaBase = { 2, 4, 6, 8, 8 };
    public int[] aiBetaBase = { 1, 2, 3, 4, 4 };
    public int[] aiAlphaBase = { 0, 0, 1, 2, 3 };
    public int aiCoreRandomDelta = 1;
    public float moddedAiCoreRestockChance = 0f;
    public float moddedAiCorePriceWeightExponent = 1.0f;
    public int moddedAiCoreMinBasePrice = 10;
    public int moddedAiCoreMinQty = 1;
    public int moddedAiCoreMaxQty = 1;

    public float tariffNeutral = 4.0f;
    public float tariffFavorable = 3.0f;
    public float tariffWelcoming = 2.5f;
    public float tariffFriendly = 2.2f;
    public float tariffCooperative = 2.0f;

    public float weaponBpPriceMult = 3.0f;
    public float fighterBpPriceMult = 3.0f;
    public float shipBpPriceMult = 1.2f;
    public float aiCorePriceMult = 1.1f;
    public float specialItemPriceMult = 1.0f;

    public int luckyRestockMinLevel = 2;
    public int luckyPriceMean = 200000;
    public int luckyPriceStd = 50000;
    public int luckyPriceFloor = 25000;
    public int luckySpecialExtraPicksLowLevel = 1;
    public int luckySpecialExtraPicksHighLevel = 2;
    public float luckySpecialQty2Chance = 0.75f;
    public float luckySpecialQty3ChanceAtLevel5 = 0.20f;
    public int luckyCoreBonusPerUnlockedTier = 1;
    public int luckyBpBonusExtraMin = 1;
    public int luckyBpBonusExtraMax = 2;

    public static CTS_Config get() {
        if (current == null) {
            reload();
        }
        return current;
    }

    public static void reload() {
        CTS_Config cfg = new CTS_Config();
        cfg.loadFromJson();
        cfg.applyLunaOverrides();
        current = cfg;
    }

    private void loadFromJson() {
        try {
            JSONObject json = Global.getSettings().getMergedJSONForMod(CONFIG_PATH, MOD_ID);
            levelCosts = readIntArray(json, "level_costs", levelCosts);
            missionCreditContribution = readFloat(json, "mission_credit_contribution", missionCreditContribution);
            spendingCreditContribution = readFloat(json, "spending_credit_contribution", spendingCreditContribution);

            specialItemPoolFraction = readFloatArray(json, "special_item_pool_fraction", specialItemPoolFraction);
            blueprintPoolFraction = readFloatArray(json, "blueprint_pool_fraction", blueprintPoolFraction);
            blueprintMaxWeapons = readIntArray(json, "blueprint_max_weapons", blueprintMaxWeapons);
            blueprintMaxFighters = readIntArray(json, "blueprint_max_fighters", blueprintMaxFighters);
            blueprintMaxShips = readIntArray(json, "blueprint_max_ships", blueprintMaxShips);

            aiGammaBase = readIntArray(json, "ai_gamma_base", aiGammaBase);
            aiBetaBase = readIntArray(json, "ai_beta_base", aiBetaBase);
            aiAlphaBase = readIntArray(json, "ai_alpha_base", aiAlphaBase);
            aiCoreRandomDelta = readInt(json, "ai_core_random_delta", aiCoreRandomDelta);
            moddedAiCoreRestockChance = readFloat(json, "modded_ai_core_restock_chance", moddedAiCoreRestockChance);
            moddedAiCorePriceWeightExponent = readFloat(json, "modded_ai_core_price_weight_exponent",
                    moddedAiCorePriceWeightExponent);
            moddedAiCoreMinBasePrice = readInt(json, "modded_ai_core_min_base_price", moddedAiCoreMinBasePrice);
            moddedAiCoreMinQty = readInt(json, "modded_ai_core_min_qty", moddedAiCoreMinQty);
            moddedAiCoreMaxQty = readInt(json, "modded_ai_core_max_qty", moddedAiCoreMaxQty);

            tariffNeutral = readFloat(json, "tariff_neutral", tariffNeutral);
            tariffFavorable = readFloat(json, "tariff_favorable", tariffFavorable);
            tariffWelcoming = readFloat(json, "tariff_welcoming", tariffWelcoming);
            tariffFriendly = readFloat(json, "tariff_friendly", tariffFriendly);
            tariffCooperative = readFloat(json, "tariff_cooperative", tariffCooperative);

            weaponBpPriceMult = readFloat(json, "weapon_bp_price_mult", weaponBpPriceMult);
            fighterBpPriceMult = readFloat(json, "fighter_bp_price_mult", fighterBpPriceMult);
            shipBpPriceMult = readFloat(json, "ship_bp_price_mult", shipBpPriceMult);
            aiCorePriceMult = readFloat(json, "ai_core_price_mult", aiCorePriceMult);
            specialItemPriceMult = readFloat(json, "special_item_price_mult", specialItemPriceMult);

            luckyRestockMinLevel = readInt(json, "lucky_restock_min_level", luckyRestockMinLevel);
            luckyPriceMean = readInt(json, "lucky_price_mean", luckyPriceMean);
            luckyPriceStd = readInt(json, "lucky_price_std", luckyPriceStd);
            luckyPriceFloor = readInt(json, "lucky_price_floor", luckyPriceFloor);
            luckySpecialExtraPicksLowLevel = readInt(json, "lucky_special_extra_picks_low_level",
                    luckySpecialExtraPicksLowLevel);
            luckySpecialExtraPicksHighLevel = readInt(json, "lucky_special_extra_picks_high_level",
                    luckySpecialExtraPicksHighLevel);
            luckySpecialQty2Chance = readFloat(json, "lucky_special_qty2_chance", luckySpecialQty2Chance);
            luckySpecialQty3ChanceAtLevel5 = readFloat(json, "lucky_special_qty3_chance_level5",
                    luckySpecialQty3ChanceAtLevel5);
            luckyCoreBonusPerUnlockedTier = readInt(json, "lucky_core_bonus_per_unlocked_tier",
                    luckyCoreBonusPerUnlockedTier);
            luckyBpBonusExtraMin = readInt(json, "lucky_bp_bonus_extra_min", luckyBpBonusExtraMin);
            luckyBpBonusExtraMax = readInt(json, "lucky_bp_bonus_extra_max", luckyBpBonusExtraMax);
        } catch (Exception ex) {
            log.warn("Could not load " + CONFIG_PATH + ", using defaults", ex);
        }
        sanitize();
    }

    private void applyLunaOverrides() {
        if (!isLunaAvailable()) {
            return;
        }
        JSONObject lunaJson = readLunaSettingsJson();
        levelCosts[0] = getLunaInt("cts_level_cost_1", levelCosts[0], lunaJson);
        levelCosts[1] = getLunaInt("cts_level_cost_2", levelCosts[1], lunaJson);
        levelCosts[2] = getLunaInt("cts_level_cost_3", levelCosts[2], lunaJson);
        levelCosts[3] = getLunaInt("cts_level_cost_4", levelCosts[3], lunaJson);

        missionCreditContribution = getLunaFloat("cts_mission_credit_mult", missionCreditContribution, lunaJson);
        spendingCreditContribution = getLunaFloat("cts_spending_credit_mult", spendingCreditContribution, lunaJson);

        tariffNeutral = getLunaFloat("cts_tariff_neutral", tariffNeutral, lunaJson);
        tariffFavorable = getLunaFloat("cts_tariff_favorable", tariffFavorable, lunaJson);
        tariffWelcoming = getLunaFloat("cts_tariff_welcoming", tariffWelcoming, lunaJson);
        tariffFriendly = getLunaFloat("cts_tariff_friendly", tariffFriendly, lunaJson);
        tariffCooperative = getLunaFloat("cts_tariff_cooperative", tariffCooperative, lunaJson);

        weaponBpPriceMult = getLunaFloat("cts_weapon_bp_price_mult", weaponBpPriceMult, lunaJson);
        fighterBpPriceMult = getLunaFloat("cts_fighter_bp_price_mult", fighterBpPriceMult, lunaJson);
        shipBpPriceMult = getLunaFloat("cts_ship_bp_price_mult", shipBpPriceMult, lunaJson);
        aiCorePriceMult = getLunaFloat("cts_ai_core_price_mult", aiCorePriceMult, lunaJson);
        specialItemPriceMult = getLunaFloat("cts_special_item_price_mult", specialItemPriceMult, lunaJson);
        moddedAiCoreRestockChance = getLunaFloat("cts_modded_core_chance", moddedAiCoreRestockChance, lunaJson);
        moddedAiCorePriceWeightExponent = getLunaFloat("cts_modded_core_price_exp", moddedAiCorePriceWeightExponent,
                lunaJson);
        moddedAiCoreMinBasePrice = getLunaInt("cts_modded_core_min_price", moddedAiCoreMinBasePrice, lunaJson);

        luckyRestockMinLevel = getLunaInt("cts_lucky_min_level", luckyRestockMinLevel, lunaJson);
        luckyPriceMean = getLunaInt("cts_lucky_price_mean", luckyPriceMean, lunaJson);
        luckyPriceStd = getLunaInt("cts_lucky_price_std", luckyPriceStd, lunaJson);
        sanitize();
        log.info("Loaded Luna settings: source=" + (lunaJson != null ? "common-json" : "api-reflection")
                + ", luckyPriceMean=" + luckyPriceMean
                + ", luckyPriceStd=" + luckyPriceStd
                + ", moddedAiCoreChance=" + moddedAiCoreRestockChance
                + ", moddedAiCorePriceExp=" + moddedAiCorePriceWeightExponent);
    }

    private void sanitize() {
        if (levelCosts.length < 4) {
            levelCosts = Arrays.copyOf(levelCosts, 4);
            for (int i = 0; i < levelCosts.length; i++) {
                if (levelCosts[i] <= 0) {
                    levelCosts[i] = 1;
                }
            }
        }
        missionCreditContribution = Math.max(0f, missionCreditContribution);
        spendingCreditContribution = Math.max(0f, spendingCreditContribution);
        aiCoreRandomDelta = Math.max(0, aiCoreRandomDelta);
        moddedAiCoreRestockChance = clamp01(moddedAiCoreRestockChance);
        moddedAiCorePriceWeightExponent = Math.max(0f, moddedAiCorePriceWeightExponent);
        moddedAiCoreMinBasePrice = Math.max(0, moddedAiCoreMinBasePrice);
        moddedAiCoreMinQty = Math.max(1, moddedAiCoreMinQty);
        moddedAiCoreMaxQty = Math.max(moddedAiCoreMinQty, moddedAiCoreMaxQty);
        weaponBpPriceMult = Math.max(1f, weaponBpPriceMult);
        fighterBpPriceMult = Math.max(1f, fighterBpPriceMult);
        shipBpPriceMult = Math.max(1f, shipBpPriceMult);
        aiCorePriceMult = Math.max(1f, aiCorePriceMult);
        specialItemPriceMult = Math.max(0f, specialItemPriceMult);
        luckyRestockMinLevel = Math.max(1, luckyRestockMinLevel);
        luckyPriceMean = Math.max(0, luckyPriceMean);
        luckyPriceStd = Math.max(0, luckyPriceStd);
        luckyPriceFloor = Math.max(0, luckyPriceFloor);
        luckySpecialQty2Chance = clamp01(luckySpecialQty2Chance);
        luckySpecialQty3ChanceAtLevel5 = clamp01(luckySpecialQty3ChanceAtLevel5);
        luckyBpBonusExtraMin = Math.max(0, luckyBpBonusExtraMin);
        luckyBpBonusExtraMax = Math.max(luckyBpBonusExtraMin, luckyBpBonusExtraMax);
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private int[] readIntArray(JSONObject json, String key, int[] defaults) {
        JSONArray arr = json.optJSONArray(key);
        if (arr == null || arr.length() != defaults.length) {
            return defaults;
        }
        int[] out = Arrays.copyOf(defaults, defaults.length);
        for (int i = 0; i < out.length; i++) {
            out[i] = arr.optInt(i, defaults[i]);
        }
        return out;
    }

    private float[] readFloatArray(JSONObject json, String key, float[] defaults) {
        JSONArray arr = json.optJSONArray(key);
        if (arr == null || arr.length() != defaults.length) {
            return defaults;
        }
        float[] out = Arrays.copyOf(defaults, defaults.length);
        for (int i = 0; i < out.length; i++) {
            out[i] = (float) arr.optDouble(i, defaults[i]);
        }
        return out;
    }

    private int readInt(JSONObject json, String key, int fallback) {
        return json.has(key) ? json.optInt(key, fallback) : fallback;
    }

    private float readFloat(JSONObject json, String key, float fallback) {
        return json.has(key) ? (float) json.optDouble(key, fallback) : fallback;
    }

    private boolean isLunaAvailable() {
        try {
            return Global.getSettings().getModManager().isModEnabled("lunalib");
        } catch (Exception ex) {
            return false;
        }
    }

    private float getLunaFloat(String settingId, float fallback, JSONObject lunaJson) {
        if (lunaJson != null && lunaJson.has(settingId)) {
            return (float) lunaJson.optDouble(settingId, fallback);
        }
        Object value = getLunaValue("getFloat", settingId);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return fallback;
    }

    private int getLunaInt(String settingId, int fallback, JSONObject lunaJson) {
        if (lunaJson != null && lunaJson.has(settingId)) {
            return lunaJson.optInt(settingId, fallback);
        }
        Object value = getLunaValue("getInt", settingId);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return fallback;
    }

    private JSONObject readLunaSettingsJson() {
        try {
            String jsonText = Global.getSettings().readTextFileFromCommon("LunaSettings/" + MOD_ID + ".json");
            if (jsonText == null || jsonText.trim().isEmpty()) {
                return null;
            }
            return new JSONObject(jsonText);
        } catch (Exception ex) {
            return null;
        }
    }

    private Object getLunaValue(String methodName, String settingId) {
        try {
            Class<?> cls = Class.forName("lunalib.lunaSettings.LunaSettings");
            Method method = cls.getMethod(methodName, String.class, String.class);
            return method.invoke(null, MOD_ID, settingId);
        } catch (Exception ex) {
            return null;
        }
    }
}
