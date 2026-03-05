package chickentechshop.campaign.intel.missions.chicken;

import chickentechshop.campaign.submarkets.TechMarket;

public final class CTS_MissionRewards {

    private CTS_MissionRewards() {
    }

    public static void applyReward(int creditReward) {
        if (creditReward <= 0) {
            return;
        }

        TechMarket submarket = ChickenQuestUtils.getChickenTechMarketOrNull();
        if (submarket == null) {
            return;
        }

        submarket.addCreditsToTechMarket(creditReward);
        submarket.updateCargoForce();
    }
}
