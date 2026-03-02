package chickentechshop.campaign.intel.missions.chicken;

import java.lang.reflect.Method;

import chickentechshop.campaign.submarkets.TechMarket;

public final class CTS_MissionRewards {

    private CTS_MissionRewards() {
    }

    public static void applyRewardIfMissionSucceeded(Object mission, int creditReward) {
        if (creditReward <= 0) {
            return;
        }
        if (!isMissionSucceeded(mission)) {
            return;
        }

        TechMarket submarket = ChickenQuestUtils.getChickenTechMarketOrNull();
        if (submarket == null) {
            return;
        }

        submarket.addCreditsToTechMarket(creditReward);
        submarket.updateCargoForce();
    }

    private static boolean isMissionSucceeded(Object mission) {
        if (mission == null) {
            return false;
        }

        Method method = findNoArgMethod(mission.getClass(), "isSucceeded");
        if (method == null) {
            return false;
        }

        try {
            Object result = method.invoke(mission);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Method findNoArgMethod(Class<?> cls, String methodName) {
        Class<?> current = cls;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
