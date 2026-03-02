package chickentechshop.campaign.intel.missions.chicken;

import com.fs.starfarer.api.impl.campaign.missions.DeadDropMission;

public class CTS_DeadDropMission extends DeadDropMission {

	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		CTS_MissionRewards.applyRewardIfMissionSucceeded(this, creditReward);
	}
}
