package chickentechshop.campaign.intel.missions.chicken;

import com.fs.starfarer.api.impl.campaign.missions.DisruptHeavyIndustry;

public class CTS_DisruptHeavyIndustry extends DisruptHeavyIndustry {

	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		if (isSucceeded()) {
			CTS_MissionRewards.applyReward(creditReward);
		}
	}
}
