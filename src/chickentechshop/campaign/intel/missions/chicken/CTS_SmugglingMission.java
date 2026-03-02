package chickentechshop.campaign.intel.missions.chicken;

import com.fs.starfarer.api.impl.campaign.missions.SmugglingMission;

public class CTS_SmugglingMission extends SmugglingMission {

	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		CTS_MissionRewards.applyRewardIfMissionSucceeded(this, creditReward);
	}
}
