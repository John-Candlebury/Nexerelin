package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import static exerelin.campaign.CovertOpsManager.NPC_EFFECT_MULT;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.StatsTracker;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.intel.MilestoneTracker;
import exerelin.campaign.intel.diplomacy.DiplomacyIntel;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.lazywizard.lazylib.MathUtils;

public abstract class CovertActionIntel extends BaseIntelPlugin implements Cloneable {
	
	public static final String[] EVENT_ICONS = new String[]{
		"graphics/exerelin/icons/intel/spy4.png",
		"graphics/exerelin/icons/intel/spy4_amber.png",
		"graphics/exerelin/icons/intel/spy4_red.png"
	};
	
	public static final ExerelinReputationAdjustmentResult NO_EFFECT = new ExerelinReputationAdjustmentResult(0);
	public static final boolean ALWAYS_REPORT = false;	// debug
	public static final String BUTTON_GOTOAGENT = "goToAgent";
	public static final int DEFAULT_AGENT_LEVEL = 2;
	public static final int DEFAULT_AGENT_LEVEL_DEVIOUS = 4;
	public static final float AI_ADMIN_SUCCESS_MULT = 0.75f;
	
	protected Map<String, Object> params;
	protected MarketAPI market;
	protected AgentIntel agent;
	protected FactionAPI agentFaction;
	protected FactionAPI targetFaction;
	protected boolean playerInvolved = false;
	protected CovertActionResult result;
	protected ExerelinReputationAdjustmentResult repResult;
	protected float relation;
	protected int xpGain = -1;
	protected int newLevel = -1;
	protected float days;
	protected float cost;
	protected StoryPointUse sp = StoryPointUse.NONE;
	protected float daysRemaining;
	protected Float injuryTime;
	protected MarketAPI agentEscapeDest;
	protected Long timestampActual;
	
	protected boolean started;
	
	/**
	 *
	 * @param agent Agent executing the covert action (null for NPC actions)
	 * @param market Target market. Usually the market the agent is on, but 
	 * for {@code Travel} this is the destination market.
	 * @param agentFaction Faction conducting the covert action.
	 * @param targetFaction
	 * @param playerInvolved
	 * @param params
	 */
	public CovertActionIntel(AgentIntel agent, MarketAPI market, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params)
	{
		this.agent = agent;
		this.market = market;
		this.agentFaction = agentFaction;
		this.targetFaction = targetFaction;
		this.playerInvolved = playerInvolved;
		this.params = params;
	}
	
	public void init() {
		daysRemaining = getTimeNeeded();
		days = daysRemaining;
		cost = getCost();
	}
	
	public void activate() {
		init();
		started = true;
		Global.getSector().addScript(this);
	}
	
	public void reset() {
		result = null;
		repResult  = null;
		relation = 0;
		xpGain = -1;
		newLevel = -1;
		injuryTime = null;
		agentEscapeDest = null;
		ending = false;
		ended = false;
		init();
	}
	
	public ExerelinReputationAdjustmentResult getReputationResult() {
		return repResult;
	}
	
	public void setMarket(MarketAPI market) {
		this.market = market;
	}
	
	public void setResult(CovertActionResult result) {
		this.result = result;
	}
	
	public abstract String getDefId();
	
	public CovertOpsManager.CovertActionDef getDef() {
		return CovertOpsManager.getDef(getDefId());
	}	
	
	public String getActionName(boolean uppercase) {
		String name = getDef().name;
		if (!uppercase) {
			name = Misc.ucFirst(name.toLowerCase());
		}
		return name;
	}
	
	public AgentIntel getAgent() {
		return agent;
	}
	
	public FactionAPI getAgentFaction() {
		return agentFaction;
	}
	
	public FactionAPI getTargetFaction() {
		return targetFaction;
	}
	
	public boolean isPlayerInvolved() {
		return playerInvolved;
	}

	private boolean seenByPlayer = false;
	private boolean shouldEndWhenSeen = false;

	@Override
	public void endAfterDelay() {
		if (ExerelinModPlugin.isNexDev) {
			Global.getSector().getCampaignUI().addMessage("endAfterDelay() in CovertActionIntel called");
		}
		if (!seenByPlayer) {
			shouldEndWhenSeen = true;
			return; // don't do anything if not seen by player
		}
		super.endAfterDelay(); // whatever other code normally goes there
	}

	@Override
	public void reportMadeVisibleToPlayer() {
		if (ExerelinModPlugin.isNexDev) {
			Global.getSector().getCampaignUI().addMessage("reportMadeVisibleToPlayer() in CovertActionIntel called");
		}
		seenByPlayer = true;
		if (shouldEndWhenSeen) {
			endAfterDelay();
		}
	}
	
	/**
	 * Gets the level of the current agent, or the appropriate level for NPC actions based on the faction.
	 * @return
	 */
	public int getLevel() {
		if (agent != null) return agent.getLevel();
		if (DiplomacyTraits.getFactionTraits(agentFaction.getId()).contains(DiplomacyTraits.TraitIds.DEVIOUS))
			return DEFAULT_AGENT_LEVEL_DEVIOUS;
		return DEFAULT_AGENT_LEVEL;
	}
	
	public float getTimeNeeded() {
		int level = getLevel();
		float time = getDef().time;
		time *= 1 - 0.1f * (level - 1);
		
		if (getDef().costScaling) {
			time *= 1 + 0.25f * (market.getSize() - 3);
		}
		if (CovertOpsManager.isDebugMode() || NexUtils.isNonPlaytestDevMode())
			time *= 0.1f;
		
		return time;
	}
	
	public int getCost() {
		return getCostStat().getModifiedInt();
	}
	
	public MutableStat getCostStat() {
		MutableStat cost = new MutableStat(0);
		
		float baseCost = getDef().baseCost;
		cost.modifyFlat("base", baseCost, getString("costBase", true));
		
		int level = getLevel();
		float levelMult = 1 - 0.1f * (level - 1);
		//cost.modifyMult("levelMult", baseCost, getString("costLevelMult", true));
		
		if (getDef().costScaling) {
			float sizeMult = Math.max(market.getSize() - 3, 0.5f);
			cost.modifyMult("sizeMult", sizeMult, getString("costSizeMult", true));
		}
		
		return cost;
	}
	
	public int getAbortRefund() {
		float daysLeftMult = daysRemaining/days;
		if (days - daysRemaining <= 1) daysLeftMult = 1;
		
		return Math.round(cost * daysLeftMult);
	}
	
	public float getEffectMultForLevel() {
		int level = getLevel();
		float mult = 1 + 0.2f * (level - 1);
		return mult;
	}
	
	protected MutableStat getSuccessChance() {
		return getSuccessChance(true);
	}
	
	protected MutableStat getSuccessChance(boolean checkSP) {

		MutableStat stat = new MutableStat(0);

		if (checkSP && sp.preventFailure()) {
			stat.modifyFlat("baseChance", 999, getString("baseChance", true));
			return stat;
		}

		CovertOpsManager.CovertActionDef def = getDef();
		int level = getLevel();
		
		// base chance
		float base = def.successChance * 100;
		if (base <= 0) return stat;
		stat.modifyFlat("baseChance", base, getString("baseChance", true));
		
		// level
		float failChance = 100 - base;
		float failChanceNew = failChance * (1 - 0.15f * (level - 1));
		float diff = failChance - failChanceNew;
		stat.modifyFlat("agentLevel", diff, StringHelper.getString("nex_agents", "agentLevel", true));
		
		// buildings
		if (def.useIndustrySecurity) {
			for (Industry ind : market.getIndustries()) {
				float mult = CovertOpsManager.getIndustrySuccessMult(ind);
				if (mult != 1)
					stat.modifyMult(ind.getId(), mult, ind.getNameForModifier());
			}
		}
		
		// alert level
		if (def.useAlertLevel) {
			float mult = 1 - CovertOpsManager.getAlertLevel(market);
			stat.modifyMult("alertLevel", mult, StringHelper.getString("nex_agents", "alertLevel", true));
		}
		
		// AI admin
		if (def.useIndustrySecurity) {
			if (market.getAdmin() != null && market.getAdmin().isAICore()) {
				stat.modifyMult("aiAdmin", AI_ADMIN_SUCCESS_MULT, StringHelper.getString("nex_agents", "aiAdmin", true));
			}
		}
		
		return stat;
	}
	
	protected MutableStat getDetectionChance(boolean fail) {

		MutableStat stat = new MutableStat(0);

		if (sp.preventDetection()) {
			stat.modifyFlat("baseChance", 0, getString("baseChance", true));
			return stat;
		}

		CovertOpsManager.CovertActionDef def = getDef();
		int level = getLevel();

		// base chance
		float base = fail ? def.detectionChanceFail : def.detectionChance;
		if (base <= 0) return stat;
		base *= 100;
		
		stat.modifyFlat("baseChance", base, getString("baseChance", true));
		
		// level
		float levelMult = 1 - 0.15f * (level - 1);
		if (levelMult < 1)
			stat.modifyMult("agentLevel", levelMult, StringHelper.getString("nex_agents", "agentLevel", true));
		
		// buildings
		for (Industry ind : market.getIndustries()) {
			float mult = CovertOpsManager.getIndustryDetectionMult(ind);
			if (mult != 1)
				stat.modifyMult(ind.getId(), mult, ind.getNameForModifier());
		}
		
		return stat;
	}
	
	public boolean canAbort() {
		return result == null;
	}
	
	public void handleStoryPointRefund() {
		String bonusXPId = null;
		switch (sp) {
			case SUCCESS:
				bonusXPId = "agentOrderSuccess";
				break;
			case DETECTION:
				bonusXPId = "agentOrderDetection";
				break;
			case BOTH:
				bonusXPId = "agentOrderBoth";
				break;
			default:
				return;
		}
		float bonusXPFraction = 1 - Global.getSettings().getBonusXP(bonusXPId);
		if (bonusXPFraction == 0) return;
		long xpToGrant = Math.round(NexUtils.getBonusXPForSpendingStoryPointBeforeSpendingIt(Global.getSector().getPlayerStats())
				* bonusXPFraction);
		Global.getSector().getPlayerStats().addBonusXP(xpToGrant, true, null, true);
	}
	
	public void abort() {
		if (playerInvolved) {
			int refund = getAbortRefund();
			Global.getSector().getPlayerFleet().getCargo().getCredits().add(refund);
			handleStoryPointRefund();
		}
		if (agent != null){
			agent.removeActionFromQueue(this);
		}
		endImmediately();
	}
	
	public boolean canRepeat() {
		return false;
	}
		
	/**
	 * Rolls a success/failure and detected/undetected result for the covert action.
	 * @return
	 */
	protected CovertActionResult covertActionRoll()
	{
		CovertOpsManager.CovertActionDef def = getDef();
		CovertActionResult rollResult = null;
		
		Random random = CovertOpsManager.getRandom(market);
		
		MutableStat sChance = getSuccessChance();
		MutableStat sDetectChance = getDetectionChance(false);
		MutableStat fDetectChance = getDetectionChance(true);
			
		if (random.nextFloat() * 100 < sChance.getModifiedValue())
		{
			rollResult = CovertActionResult.SUCCESS;
			if (random.nextFloat() * 100 < sDetectChance.getModifiedValue())
				rollResult = CovertActionResult.SUCCESS_DETECTED;
		}
		else
		{
			rollResult = CovertActionResult.FAILURE;
			if (random.nextFloat() * 100 < fDetectChance.getModifiedValue())
				rollResult = CovertActionResult.FAILURE_DETECTED;
		}
		return rollResult;
	}
	
	public CovertActionResult execute()
	{
		if (targetFaction == null)
			targetFaction = market.getFaction();
		if (playerInvolved) {
			agentFaction = PlayerFactionStore.getPlayerFaction();	// in case player changed faction since launching action
			if (agentFaction == targetFaction) {
				agentFaction = Global.getSector().getPlayerFaction();
			}
		}
		
		result = covertActionRoll();
				
		if (result.isSuccessful())
			onSuccess();
		else
			onFailure();
		
		if (agent != null) {
			gainAgentXP();
			agent.notifyActionCompleted();
			rollInjury();			
		}
		
		timestampActual = Global.getSector().getClock().getTimestamp();
			
		if (market != null) CovertOpsManager.modifyAlertLevel(market, getAlertLevelIncrease());
		
		if (playerInvolved && getSuccessChance().getModifiedValue() <= 100) {
			StatsTracker.getStatsTracker().notifyAgentActions(1);
			if (StatsTracker.getStatsTracker().getNumAgentActions() >= 15)
				MilestoneTracker.getIntel().awardMilestone("agentAction15");
		}		
		
		return result;
	}
	
	public boolean rollInjury() {
		if (sp == StoryPointUse.NONE && result.isSuccessful() && !result.isDetected())
			return false;
		
		float chance = CovertOpsManager.getBaseInjuryChance(result.isSuccessful());
		chance *= getDef().injuryChanceMult;
		if (chance <= 0) return false;
		if (sp.preventFailure()) {
			chance *= 2 - getSuccessChance(false).getModifiedValue();
		}
		if (CovertOpsManager.getRandom(market).nextFloat() <= chance)
		{
			Injury injury = new Injury(agent, agentFaction, targetFaction, playerInvolved, params);
			agent.addAction(injury, 0);
			injury.activate();
			injuryTime = injury.days;
			return true;
		}
		return false;
	}
	
	public CovertActionResult getResult()	{
		return result;
	}
	
	/**
	 * Returns true if this is NOT an allow-own-market action, and we meet certain conditions. 
	 * Normally the condition is just "agent faction == market faction", 
	 * but actions like Raise/Lower Relations should also check for e.g. market faction = third faction.
	 * @return
	 */
	public boolean shouldAbortIfOwnMarket() {
		if (agent != null && (market.getFaction() == agent.faction)) {
			return true;
		}
		else {
			return false;
		}
	}
	
	@Override
	public void advanceImpl(float amount) {
		super.advanceImpl(amount);
		
		if (agent != null && !allowOwnMarket() && shouldAbortIfOwnMarket() && canAbort())
		{
			abort();
			agent.sendUpdateIfPlayerHasIntel(AgentIntel.UPDATE_ABORTED, false);
			return;
		}
		if (!market.isInEconomy() && canAbort()) {
			abort();
			agent.sendUpdateIfPlayerHasIntel(AgentIntel.UPDATE_ABORTED, false);
			return;
		}
		
		float days = Global.getSector().getClock().convertToDays(amount);
		if (result == null) {
			daysRemaining -= days;
			if (daysRemaining <= 0)
				execute();
		}
	}
	
	protected abstract void onSuccess();
	
	protected abstract void onFailure();
	
	protected void adjustRepIfDetected(RepLevel ensureAtBest, RepLevel limit)
	{
		if (agentFaction == targetFaction)
			repResult = NO_EFFECT;
		else if (result.isDetected())
		{
			repResult = adjustRelationsFromDetection(
					agentFaction, targetFaction, ensureAtBest, null, limit, false);
			DiplomacyManager.getManager().getDiplomacyBrain(targetFaction.getId()).reportDiplomacyEvent(
					agentFaction.getId(), repResult.delta);
			relation = agentFaction.getRelationship(targetFaction.getId());
		}
		else repResult = NO_EFFECT;
	}
	
	protected ExerelinReputationAdjustmentResult adjustRelationsFromDetection(FactionAPI faction1, 
			FactionAPI faction2, RepLevel ensureAtBest, RepLevel ensureAtWorst, RepLevel limit, boolean useNPCMult)
	{
		float effectMin = -getDef().repLossOnDetect.two;
		float effectMax = -getDef().repLossOnDetect.one;
		return adjustRelations(faction1, faction2, effectMin, effectMax, ensureAtBest, ensureAtWorst, limit, useNPCMult);
	}
	
	protected ExerelinReputationAdjustmentResult adjustRelations(FactionAPI faction1, FactionAPI faction2, 
			float effectMin, float effectMax, RepLevel ensureAtBest, RepLevel ensureAtWorst, RepLevel limit,
			boolean useNPCMult)
	{
		float effect = MathUtils.getRandomNumberInRange(effectMin, effectMax);
		if (!playerInvolved && useNPCMult) effect *= NPC_EFFECT_MULT;
		ExerelinReputationAdjustmentResult result = DiplomacyManager.adjustRelations(
				faction1, faction2, effect, ensureAtBest, ensureAtWorst, limit);
						
		return result;
	}
	
	protected float getXPMult() {
		if (!getDef().costScaling) return 1;
		return 0.5f + 0.5f * (market.getSize() - 2);
	}
	
	protected void gainAgentXP() {
		if (agent == null) return;
		xpGain = (int)(getDef().xp * getXPMult());
		
		int currLevel = agent.level;
		agent.gainXP(xpGain);
		if (agent.level > currLevel) {
			newLevel = agent.level;
		}
	}
	
	public boolean allowOwnMarket() {
		return false;
	}
	
	public boolean showSuccessChance() {
		return true;
	}
	
	protected boolean shouldReportEvent() {
		return ALWAYS_REPORT 
				|| playerInvolved
				|| agentFaction == PlayerFactionStore.getPlayerFaction()
				//|| result != null && result.isDetected()
				|| (result != null && result.isSuccessful())
				|| (repResult != null && repResult != NO_EFFECT)
				|| Global.getSettings().isDevMode();
	}
	
	protected boolean affectsPlayerRep() {
		if (repResult == null || repResult == NO_EFFECT)
			return false;
		
		FactionAPI cf = Misc.getCommissionFaction();
		return agentFaction == cf || agentFaction.isPlayerFaction()
				|| targetFaction == cf || targetFaction.isPlayerFaction();
	}
	
	protected int getNotifyLevel() {
		int level = 0;
		if (affectsPlayerRep())
		{
			level += 1;
		}
		
		return level;
	}
	
	protected boolean shouldNotify() {
		if (ALWAYS_REPORT || playerInvolved || NexUtils.isNonPlaytestDevMode()) 
			return true;
		if (repResult != null && (repResult.isHostile != repResult.wasHostile))
			return true;
		if (result != null && result.isSuccessful()
				&& (targetFaction.isPlayerFaction() || targetFaction == Misc.getCommissionFaction()))
			return true;
		
		int maxLevelToNotify = getNotifyLevel();
		
		return NexConfig.agentEventFilterLevel <= maxLevelToNotify;
	}
	
	protected void reportEvent() {
		timestamp = Global.getSector().getClock().getTimestamp();
		if (ExerelinModPlugin.isNexDev) {
			Global.getSector().getCampaignUI().addMessage("reportEvent() called in CovertActionIntel");
			if (shouldReportEvent()){
				Global.getSector().getCampaignUI().addMessage("shouldReportEvent() in reportEvent() TRUE;if intel doesn't display, something bad happened.");
			}
		}
		if (shouldReportEvent()) { //TODO: make it so if an agent action makes 2 factions hostile, add it
			boolean notify = shouldNotify();
			if (NexConfig.nexIntelQueued <= 1) {
				if (NexConfig.nexIntelQueued <= 0
					||	affectsPlayerRep()
					||	playerInvolved
					||	agentFaction == PlayerFactionStore.getPlayerFaction()
					||	targetFaction.isPlayerFaction()
					||	targetFaction == Misc.getCommissionFaction()) {
					Global.getSector().getIntelManager().addIntel(this, !notify);

					if (!notify && ExerelinModPlugin.isNexDev) {
						Global.getSector().getCampaignUI().addMessage("Suppressed agent action notification "
								+ getName() + " due to filter level", Misc.getHighlightColor());
					}
				}
				else Global.getSector().getIntelManager().queueIntel(this);
			}

			else Global.getSector().getIntelManager().queueIntel(this);

			endAfterDelay();
		}
	}
	
	public float getAlertLevelIncrease() {
		return getDef().alertLevelIncrease;
	}
	
	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = Global.getSector().getPlayerFaction().getBaseUIColor();

		info.addPara(getName(), c, 0);

		Color tc = getBulletColorForMode(mode);
		Color hl = Misc.getHighlightColor();
		float initPad = 3;
		float pad = 0;
		
		bullet(info);
		
		addBulletPoints(info, tc, initPad, pad);
	}
	
	public void addBulletPoints(TooltipMakerAPI info, Color color, float initPad, float pad) {
		boolean afKnown = isAgentFactionKnown();
		if (afKnown)
			NexUtilsFaction.addFactionNamePara(info, initPad, color, agentFaction);
		
		info.addPara(getString("intelBulletTarget"), afKnown ? pad : initPad, color, 
				targetFaction.getBaseUIColor(), " " + market.getName());
	}
	
	protected String getName() {
		String str = getDef().name;
		if (result != null) { 
			if (result.isSuccessful())
				str += " - " + StringHelper.getString("nex_agents", "verbSuccess", true);
			else
				str += " - " + StringHelper.getString("nex_agents", "verbFailed", true);
		}
		
		return str;
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		addImages(info, width, opad);
		addMainDescPara(info, opad);
		if (timestampActual != null) 
			info.addPara(Misc.getAgoStringForTimestamp(timestampActual) + ".", opad);
		
		info.addSectionHeading(getString("intelResultHeader"), Alignment.MID, opad);
		addResultPara(info, opad);
		addAgentOutcomePara(info, opad);
		
		// goto agent button
		if (agent != null) {
			ButtonAPI button = info.addButton(getString("intelButton_goToAgent", true), 
					BUTTON_GOTOAGENT, agent.faction.getBaseUIColor(), agent.faction.getDarkUIColor(),
					(int)(width), 20f, opad * 2f);
		}
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId == BUTTON_GOTOAGENT && agent != null) {
			Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.INTEL, agent);
		}
	}
	
	@Override
	public boolean doesButtonHaveConfirmDialog(Object buttonId) {
		return false;
	}
	
	protected String getDescStringId() {
		String id = "intelDesc_" + getDef().id + "_";
		if (result.isSuccessful())
			id += "success";
		else
			id += "failure";
		if (result.isDetected())
			id += "Detected";
		if (playerInvolved)
			id += "Player";
		
		return id;
	}
	
	/**
	 * Is the agent faction known to the player? 
	 * (for whether to conceal some information in report)
	 * @return
	 */
	protected boolean isAgentFactionKnown() {
		if (playerInvolved) return true;
		if (agentFaction.isPlayerFaction()) return true;
		if (agentFaction == PlayerFactionStore.getPlayerFaction()) return true;
		if (result != null && result.isDetected()) return true;
		if (NexUtils.isNonPlaytestDevMode()) return true;
		
		return false;
	}
	
	protected List<Pair<String,String>> getStandardReplacements() {
		List<Pair<String,String>> sub = new ArrayList<>();
		if (agent != null)
			sub.add(new Pair("$agentName", agent.getAgent().getNameString()));
		if (market != null) {
			sub.add(new Pair<>("$market", market.getName()));
			sub.add(new Pair<>("$onOrAt", market.getOnOrAt()));
		}			
		
		if (isAgentFactionKnown())
			StringHelper.addFactionNameTokensCustom(sub, "agentFaction", agentFaction);
		else {
			String unknownFaction = getString("unknownFaction");
			String anUnknownFaction = getString("anUnknownFaction");
			sub.add(new Pair<>("$agentFaction", unknownFaction));
			sub.add(new Pair<>("$theAgentFaction", anUnknownFaction));
			sub.add(new Pair<>("$AgentFaction", Misc.ucFirst(unknownFaction)));
			sub.add(new Pair<>("$TheAgentFaction", Misc.ucFirst(unknownFaction)));
		}
		StringHelper.addFactionNameTokensCustom(sub, "faction", targetFaction);
		
		return sub;
	}
	
	/**
	 * Adds images to the top of the intel description panel.
	 * @param info
	 * @param width
	 * @param pad
	 */
	public void addImages(TooltipMakerAPI info, float width, float pad) {
		String crest1 = isAgentFactionKnown() ? agentFaction.getCrest() : 
				Global.getSector().getFaction(Factions.NEUTRAL).getCrest();
		info.addImages(width, 128, pad, pad, crest1, targetFaction.getCrest());
	}
	
	public void addPara(TooltipMakerAPI info, String stringId, List<Pair<String,String>> sub, 
		String[] highlights, Color[] highlightColors, float pad) {
		String str = StringHelper.getStringAndSubstituteTokens("nex_agentActions", stringId, sub);
		LabelAPI label = info.addPara(str, pad);
		label.setHighlight(highlights);
		label.setHighlightColors(highlightColors);
	}
	
	/**
	 * Generates a general description of the action for the intel item.
	 * @param info
	 * @param pad Padding.
	 */
	public void addMainDescPara(TooltipMakerAPI info, float pad) {
		List<Pair<String,String>> replace = getStandardReplacements();
		
		String[] highlights = new String[] {agentFaction.getDisplayName(), targetFaction.getDisplayName()};
		Color[] highlightColors = new Color[] {agentFaction.getBaseUIColor(), targetFaction.getBaseUIColor()};
		
		addPara(info, getDescStringId(), replace, highlights, highlightColors, pad);
	}
	
	/**
	 * Generates text for the intel item's description, covering the action's effects.
	 * @param info
	 * @param pad Padding.
	 */
	public void addResultPara(TooltipMakerAPI info, float pad) {
		if (repResult != null && repResult.delta != 0) {
			DiplomacyIntel.addRelationshipChangePara(info, agentFaction.getId(), targetFaction.getId(), 
					relation, repResult, pad);
		}
	}
	
	public abstract void addCurrentActionPara(TooltipMakerAPI info, float pad);
	
	public abstract void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad);
	
	public String getActionString(String strId) {
		return getActionString(strId, false);
	}
	
	public String getActionString(String strId, boolean withTime) {
		String action = getString(strId);
		if (withTime) {
			String daysNum = Math.round(daysRemaining) + "";
			String daysStr = RaidIntel.getDaysString(daysRemaining);
			String daysLeftStr = StringHelper.getString("nex_agents", "intelDescCurrActionDaysShort");
			daysLeftStr = StringHelper.substituteToken(daysLeftStr, "$daysStr", daysStr);
			daysLeftStr = String.format(daysLeftStr, daysNum);
			
			action += " (" + daysLeftStr + ")";
		}
		
		return action;
	}
	
	public void addLastMessagePara(TooltipMakerAPI info, float pad) {
		if (result == null)
			return;
		String str = StringHelper.getString("nex_agentActions", "intel_lastMessage_" 
				+ getDefId() + "_" + (result.isSuccessful() ? "success" : "failure"));
		str = StringHelper.substituteTokens(str, getStandardReplacements());
		
		info.addPara(str, pad);
	}
	
	public void addAgentOutcomePara(TooltipMakerAPI info, float pad) {
		if (agent == null) return;
		String name = agent.getAgent().getName().getFullName();
		Color hl = Misc.getHighlightColor();
		String str;
		
		if (agent.isDead) {
			str = StringHelper.getStringAndSubstituteToken("nex_agentActions", 
					"intelDesc_agentLost", "$agentName", agent.getAgent().getNameString());
			info.addPara(str, pad);
			return;
		}
		
		if (newLevel > -1) {
			str = StringHelper.getStringAndSubstituteToken("nex_agentActions", 
					"intelDesc_gainedXPAndLeveledUp", "$agentName", name);
			info.addPara(str, pad, hl, xpGain + "", newLevel + "");
		}
		else if (xpGain > 0) {
			str = StringHelper.getStringAndSubstituteToken("nex_agentActions", 
					"intelDesc_gainedXP", "$agentName", name);
			info.addPara(str, pad, hl, xpGain + "");
		}
		
		if (injuryTime != null) {
			str = StringHelper.getStringAndSubstituteToken("nex_agentActions", 
					"intelDesc_injured", "$agentName", name);
			info.addPara(str, pad, Misc.getNegativeHighlightColor(), Math.round(injuryTime) + "");
		}
		
		if (agentEscapeDest != null) {
			str = StringHelper.getStringAndSubstituteToken("nex_agentActions", 
					"intelDesc_agentExfiltrate", "$agentName", name);
			info.addPara(str, pad, market.getFaction().getBaseUIColor(), agentEscapeDest.getName());
		}
	}
	
	@Override
	protected float getBaseDaysAfterEnd() {
		if (repResult != null) {
			if (repResult.wasHostile && !repResult.isHostile) return 15;
			if (repResult.isHostile && !repResult.wasHostile) return 30;
		}
		return 10;
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(StringHelper.getString("nex_agents", "agents", true));
		tags.add(agentFaction.getId());
		tags.add(targetFaction.getId());
		return tags;
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		if (market != null)
			return market.getPrimaryEntity();
		return null;
	}
	
	@Override
	public String getIcon() {
		int significance = 0;
		if (result != null) {
			if (result.isDetected()) significance = 1;
		}
		if (repResult != null) {
			if (repResult.wasHostile && !repResult.isHostile) significance = 1;
			if (repResult.isHostile && !repResult.wasHostile) significance = 2;
		}
		return EVENT_ICONS[significance];
	}
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_agentActions", id, ucFirst);
	}
	
	@Override
	public Object clone() throws CloneNotSupportedException {
		CovertActionIntel self = (CovertActionIntel)super.clone();
		self.reset();
		
		return self;
	}
	
	public static enum StoryPointUse {
		NONE, SUCCESS, DETECTION, BOTH;
		
		public boolean preventDetection() {
			return this == DETECTION || this == BOTH;
		}
		
		public boolean preventFailure() {
			return this == SUCCESS || this == BOTH;
		}
	}
}
