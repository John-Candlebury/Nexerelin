package exerelin.campaign.events;

import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinConfig;
import java.util.ArrayList;
import java.util.List;


public class FactionSalaryEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(FactionSalaryEvent.class);
	
	private static final float BASE_SALARY;
	private static final float INCREMENT_PER_LEVEL;
	private int month;
	private float paidAmount = 0f;
	
	static {
		BASE_SALARY = ExerelinConfig.playerBaseSalary;
		INCREMENT_PER_LEVEL = ExerelinConfig.playerSalaryIncrementPerLevel;
	}
	
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		Global.getSector().getPersistentData().put("salariesClock", Global.getSector().getClock().createClock(Global.getSector().getClock().getTimestamp()));
		month = Global.getSector().getClock().getMonth();
	}
	
	@Override
	public void advance(float amount) {
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		if (playerFleet == null) {
			return;
		}
		CampaignClockAPI clock = Global.getSector().getClock();
		if (clock.getDay() == 1 && clock.getMonth() != month) {
		month = Global.getSector().getClock().getMonth();
		int level = Global.getSector().getPlayerPerson().getStats().getLevel();
		String stage = "report";
		paidAmount = BASE_SALARY + INCREMENT_PER_LEVEL * (level - 1);
                if (paidAmount == 0)
                    return;
		
		FactionAPI alignedFaction = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());
		RepLevel relation = alignedFaction.getRelationshipLevel("player");
		if (alignedFaction.isAtBest("player", RepLevel.INHOSPITABLE))
		{
			paidAmount = 0;
			stage = "report_unpaid";
		}
		else if (relation == RepLevel.SUSPICIOUS)
			paidAmount *= 0.25f;
		else if (relation == RepLevel.NEUTRAL)
			paidAmount *= 0.5f;
		else if (relation == RepLevel.FAVORABLE)
			paidAmount *= 0.75f;
		
		playerFleet.getCargo().getCredits().add(paidAmount);
		Global.getSector().reportEventStage(this, stage, playerFleet, MessagePriority.DELIVER_IMMEDIATELY);
		Global.getSector().getPersistentData().put("salariesClock", Global.getSector().getClock().createClock(Global.getSector().getClock().getTimestamp()));
		}
	}

	@Override
	public String getEventName() {
		return ("Faction salary report");
	}
	
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		CampaignClockAPI previous = (CampaignClockAPI) Global.getSector().getPersistentData().get("salariesClock");
		if (previous != null) {
		map.put("$date", previous.getMonthString() + ", c." + previous.getCycle());
		}
		String factionName = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId()).getDisplayNameWithArticle();
		map.put("$employer", factionName);
		map.put("$Employer", Misc.ucFirst(factionName));
		map.put("$paid", "" + (int) paidAmount + Strings.C);
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		addTokensToList(result, "$paid");
		return result.toArray(new String[0]);
	}
	
	@Override
	public String getCurrentImage() {
		FactionAPI myFaction = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());
		return myFaction.getLogo();
	}
	
	@Override
	public boolean isDone() {
		return false;
	}
	
	@Override
	public boolean showAllMessagesIfOngoing() {
		return false;
	}
}