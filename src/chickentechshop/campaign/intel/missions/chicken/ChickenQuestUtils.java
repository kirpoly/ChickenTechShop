package chickentechshop.campaign.intel.missions.chicken;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

import chickentechshop.campaign.submarkets.TechMarket;

public class ChickenQuestUtils {

	public static final String PERSON_CHICKEN = "chicken";
	public static final String SUBMARKET_CHICKEN = "chicken_market";

	public static PersonAPI createChicken(MarketAPI market) {
		PersonAPI existing = getChickenOrNull();
		if (existing != null) {
			if (market != null && existing.getMarket() != market) {
				MarketAPI oldMarket = existing.getMarket();
				if (oldMarket != null) {
					oldMarket.removePerson(existing);
				}
				market.addPerson(existing);
			}
			return existing;
		}
		if (market == null) {
			return null;
		}

		PersonAPI person = Global.getFactory().createPerson();
		person.setId(PERSON_CHICKEN);
		person.setImportance(PersonImportance.HIGH);
		person.setVoice(Voices.SPACER);
		person.setFaction(Factions.INDEPENDENT);
		person.setGender(FullName.Gender.FEMALE);
		person.setRankId(Ranks.UNKNOWN);
		person.setPostId(Ranks.POST_CITIZEN);
		person.getName().setFirst("Chicken");
		person.getName().setLast("");
		person.setPortraitSprite(Global.getSettings().getSpriteName("characters", "chicken"));
		person.addTag("chicken");
		// person.addTag(Tags.CONTACT_TRADE);
		// person.addTag(Tags.CONTACT_UNDERWORLD);
		Global.getSector().getImportantPeople().addPerson(person);
		market.addPerson(person);
		return person;
	}

	public static PersonAPI getChickenOrNull() {
		if (Global.getSector() == null || Global.getSector().getImportantPeople() == null) {
			return null;
		}
		return Global.getSector().getImportantPeople().getPerson(PERSON_CHICKEN);
	}

	public static MarketAPI getChickenMarketOrNull() {
		PersonAPI chicken = getChickenOrNull();
		if (chicken == null) {
			return null;
		}
		return chicken.getMarket();
	}

	public static TechMarket getChickenTechMarketOrNull() {
		MarketAPI market = getChickenMarketOrNull();
		if (market == null || !market.hasSubmarket(SUBMARKET_CHICKEN)) {
			return null;
		}
		if (!(market.getSubmarket(SUBMARKET_CHICKEN).getPlugin() instanceof TechMarket)) {
			return null;
		}
		return (TechMarket) market.getSubmarket(SUBMARKET_CHICKEN).getPlugin();
	}

	public static MarketAPI getChickenMarket() {
		return getChickenMarketOrNull();
	}
}
