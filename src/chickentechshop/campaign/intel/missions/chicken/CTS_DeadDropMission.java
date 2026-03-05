package chickentechshop.campaign.intel.missions.chicken;

import com.fs.starfarer.api.impl.campaign.missions.DeadDropMission;

public class CTS_DeadDropMission extends DeadDropMission {

	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		if (isSucceeded()) {
			CTS_MissionRewards.applyReward(creditReward);
		}
	}
}
