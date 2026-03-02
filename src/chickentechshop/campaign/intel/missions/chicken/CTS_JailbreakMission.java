package chickentechshop.campaign.intel.missions.chicken;

import com.fs.starfarer.api.impl.campaign.missions.JailbreakMission;

public class CTS_JailbreakMission extends JailbreakMission {

	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		CTS_MissionRewards.applyRewardIfMissionSucceeded(this, creditReward);
	}
}
