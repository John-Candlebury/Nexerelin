package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventWithPerson;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.CovertOpsManager;
import static exerelin.campaign.intel.agents.AgentIntel.getString;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.StringHelper;
import java.awt.Color;

public class AgentBarEvent extends BaseBarEventWithPerson {
	
	protected static final WeightedRandomPicker<Integer> picker = new WeightedRandomPicker<>();
	
	protected int level = 1;
	
	public static enum OptionId {
		INIT,
		EXPLANATION,
		HIRE_OFFER,
		HIRE_CONFIRM,
		HIRE_DONE,
		CANCEL
	}
	
	static {
		picker.add(1, 10);
		picker.add(2, 4);
		picker.add(3, 1);
	}
		
	public AgentBarEvent() {
		super();
	}
	
	@Override
	public boolean shouldShowAtMarket(MarketAPI market) {
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
		if (!conf.allowAgentActions)
			return false;
		return super.shouldShowAtMarket(market);
	}
	
	@Override
	protected void regen(MarketAPI market) {
		if (this.market == market) return;
		super.regen(market);
		level = picker.pick();
		
		Global.getLogger(this.getClass()).info("Generated agent at " + market.getName() + " bar");
	}
	
	@Override
	public void addPromptAndOption(InteractionDialogAPI dialog) {
		super.addPromptAndOption(dialog);
		
		regen(dialog.getInteractionTarget().getMarket());
		
		TextPanelAPI text = dialog.getTextPanel();
		
		String str = getString("barPrompt");
		str = StringHelper.substituteToken(str, "$manOrWoman", getManOrWoman());
		str = StringHelper.substituteToken(str, "$heOrShe", getHeOrShe());
		str = StringHelper.substituteToken(str, "$HeOrShe", Misc.ucFirst(getHeOrShe()));
		
		text.addPara(str);
		
		str = getString("barOptionStart");
		dialog.getOptionPanel().addOption(str, this);
	}
	
	@Override
	public void init(InteractionDialogAPI dialog) {
		super.init(dialog);
		
		done = false;
		
		dialog.getVisualPanel().showPersonInfo(person, true);
		
		optionSelected(null, OptionId.INIT);
	}
	
	@Override
	public void optionSelected(String optionText, Object optionData) {
		if (!(optionData instanceof OptionId)) {
			return;
		}
		OptionId option = (OptionId) optionData;
		
		OptionPanelAPI options = dialog.getOptionPanel();
		TextPanelAPI text = dialog.getTextPanel();
		Color hl = Misc.getHighlightColor();
		String str;
		
		int salary = AgentIntel.getSalary(level);
		int hiringBonus = salary * 4;
		
		options.clearOptions();
		
		switch (option) {
			case INIT:
				text.addPara(getString("barDialogIntro1"));
				text.addPara(getString("barDialogIntro2"));

				options.addOption(StringHelper.getString("yes", true), OptionId.HIRE_OFFER);
				options.addOption(StringHelper.getString("no", true), OptionId.EXPLANATION);
				break;
			case EXPLANATION:
				str = getString("barDialogExplanation");
				str = StringHelper.substituteToken(str, "$manOrWoman", getManOrWoman());
				text.addPara(str);
			case HIRE_OFFER:
				text.setFontSmallInsignia();
				text.addPara(StringHelper.HR);
				str = getString("intelDescName");
				str = StringHelper.substituteToken(str, "$name", person.getNameString());
				text.addPara(str, hl, level + "");
				text.addPara(StringHelper.HR);
				text.setFontInsignia();
				
				String salaryStr = Misc.getWithDGS(salary);
				String bonusStr = Misc.getWithDGS(hiringBonus);
				str = getString("barDialogPay");
				
				text.addPara(str, hl, bonusStr, salaryStr);
				
				str = StringHelper.getString("exerelin_misc", "creditsAvailable");
				float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
				String creditsStr =  Misc.getWithDGS(credits);
				boolean enough = credits >= hiringBonus;
				text.addPara(str, enough? Misc.getHighlightColor() : Misc.getNegativeHighlightColor(), creditsStr);
				
				if (CovertOpsManager.getManager().getAgents().size() >= CovertOpsManager.getManager().getMaxAgents().getModifiedValue())
				{
					options.addOption(getString("barOptionMaxAgents"), OptionId.CANCEL);
				} 
				else if (!enough) {
					options.addOption(getString("barOptionNotEnoughCredits"), OptionId.CANCEL);
				} 
				else {
					options.addOption(getString("barOptionHire"), OptionId.HIRE_CONFIRM);
					options.addOption(getString("barOptionDecline"), OptionId.CANCEL);
				}
				break;
			case HIRE_CONFIRM:
				str = getString("barDialogHired");
				str = StringHelper.substituteToken(str, "$heOrShe", getHeOrShe());
				str = StringHelper.substituteToken(str, "$HeOrShe", Misc.ucFirst(getHeOrShe()));
				text.addPara(str);
				
				Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(hiringBonus);
				AddRemoveCommodity.addCreditsLossText(hiringBonus, text);
				
				BarEventManager.getInstance().notifyWasInteractedWith(this);
				addIntel();
				text.setFontSmallInsignia();
				text.addPara(getString("barDialogHiredTip"));
				text.setFontInsignia();
				options.addOption(StringHelper.getString("leave", true), OptionId.HIRE_DONE);
				break;
			case HIRE_DONE:
			case CANCEL:
				noContinue = true;
				done = true;
				break;
		}
	}

	protected void addIntel() {
		TextPanelAPI text = dialog.getTextPanel();
		AgentIntel intel = new AgentIntel(person, Global.getSector().getPlayerFaction(), level);
		intel.init();
		intel.setMarket(market);
		intel.setImportant(true);
		Global.getSector().getIntelManager().addIntelToTextPanel(intel, text);
	}

	@Override
	protected String getPersonFaction() {
		return Factions.INDEPENDENT;
	}
	
	@Override
	protected String getPersonRank() {
		return Ranks.AGENT;
	}
	
	@Override
	protected String getPersonPost() {
		return Ranks.POST_AGENT;
	}
	
	@Override
	protected String getPersonPortrait() {
		return null;
	}
	
	@Override
	protected Gender getPersonGender() {
		return Gender.ANY;
	}
}
