package chickentechshop.campaign.intel.missions.chicken;

import com.fs.starfarer.api.impl.campaign.missions.SmugglingMission;

public class CTS_SmugglingMission extends SmugglingMission {

	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		if (isSucceeded()) {
			CTS_MissionRewards.applyReward(creditReward);
		}
	}
}
