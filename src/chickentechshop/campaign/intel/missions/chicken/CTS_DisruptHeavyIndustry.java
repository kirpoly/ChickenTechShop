package chickentechshop.campaign.intel.missions.chicken;

import com.fs.starfarer.api.impl.campaign.missions.DisruptHeavyIndustry;

public class CTS_DisruptHeavyIndustry extends DisruptHeavyIndustry {

	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		CTS_MissionRewards.applyRewardIfMissionSucceeded(this, creditReward);
	}
}
