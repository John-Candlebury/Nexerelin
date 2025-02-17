package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker.PersonnelData;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker.PersonnelRank;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.util.Misc;
import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.getString;
import exerelin.campaign.intel.groundbattle.plugins.GroundBattlePlugin;
import exerelin.campaign.intel.specialforces.namer.PlanetNamer;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsMath;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class GroundUnit {
	
	public static final boolean USE_LOCATION_IMAGE = true;
	
	public static float PANEL_WIDTH = USE_LOCATION_IMAGE ? 220 : 200;
	public static float PANEL_HEIGHT = USE_LOCATION_IMAGE ? 120 : 110;
	public static final float TITLE_HEIGHT = 16;
	public static float LOCATION_SECTION_HEIGHT = USE_LOCATION_IMAGE ? 32 : 24;
	public static final float PADDING_X = 4;
	public static final float BUTTON_SECTION_WIDTH = 64;
	public static final Object BUTTON_NEW_MARINE = new Object();
	public static final Object BUTTON_NEW_HEAVY = new Object();
	
	public static final float HEAVY_COUNT_DIVISOR = 6f;	// a marine platoon has 6x as many marines as a mech platoon has mechs
	public static final int CREW_PER_MECH = 2;
		
	public final String id = Misc.genUID();
	protected int index;
	protected GroundBattleIntel intel;
	protected String name;
	protected FactionAPI faction;
	protected CampaignFleetAPI fleet;
	protected boolean isPlayer;
	protected boolean isAttacker;
	protected ForceType type;
	
	protected int personnel;
	protected int heavyArms;
	protected int lossesLastTurn;
	protected float moraleDeltaLastTurn;
	protected float morale = 0.8f;
	
	public Map<String, Object> data = new HashMap<>();
	
	protected String currAction;
	protected IndustryForBattle location;
	protected IndustryForBattle destination;
	
	public GroundUnit(GroundBattleIntel intel, ForceType type, int num, int index) {
		this.intel = intel;
		this.type = type;
		this.index = index;
		setSize(num, false);
		name = generateName();
	}
	
	protected static CargoAPI getCargo() {
		return Global.getSector().getPlayerFleet().getCargo();
	}
	
	public float setStartingMorale() {
		float morale = GBConstants.BASE_MORALE;
		if (isPlayer) {
			float xp = intel.playerData.xpTracker.data.getXPLevel();
			morale += xp * GBConstants.XP_MORALE_BONUS;
		}
		
		return morale;
	}
	
	public void setSize(int num, boolean takeFromCargo) {
		// first return the existing units to cargo
		if (takeFromCargo) {
			returnUnitsToCargo();
		}
		
		if (type == ForceType.HEAVY) {
			heavyArms = num;
			personnel = num * CREW_PER_MECH;
		}
		else {
			personnel = num;
		}
		// now take the new ones from cargo
		if (takeFromCargo) {
			getCargo().removeMarines(personnel);
			getCargo().removeCommodity(Commodities.HAND_WEAPONS, heavyArms);
			
			// move the XP from player cargo to battle player data
			PlayerFleetPersonnelTracker.transferPersonnel(
					PlayerFleetPersonnelTracker.getInstance().getMarineData(),
					intel.playerData.xpTracker.data,
					personnel, null);
		}
	}
	
	public String generateName() {
		String name = Misc.ucFirst(intel.unitSize.getName());
		int num = index + 1;
		switch (intel.unitSize) {
			case PLATOON:
			case COMPANY:
				int alphabetIndex = this.index % 26;
				return GBDataManager.NATO_ALPHABET.get(alphabetIndex) + " " + name;
			case BATTALION:
				return num + PlanetNamer.getSuffix(num) + " " + name;
			case REGIMENT:
				return Global.getSettings().getRoman(num) + " " + name;
			default:
				return name + " " + num;
		}
	}
	
	public String getName() {
		return name;
	}
	
	public IndustryForBattle getLocation() {
		return location;
	}
	
	public IndustryForBattle getDestination() {
		return destination;
	}
	
	public boolean isWithdrawing() {
		return GBConstants.ACTION_WITHDRAW.equals(currAction);
	}
	
	public String getCurrAction() {
		return currAction;
	}
	
	public ForceType getType() {
		return type;
	}
	
	public boolean isAttacker() {
		return isAttacker;
	}
	
	/**
	 * Result is identical to {@code getSize()} for normal units,
	 * or {@code CREW_PER_MECH} times larger for heavy units.
	 * @return
	 */
	public int getPersonnel() {
		return personnel;
	}
	
	public float getMorale() {
		return morale;
	}
	
	public FactionAPI getFaction() {
		return faction;
	}
	
	/**
	 * Adds the current personnel and heavy armaments in the unit back to player cargo.
	 */
	public void returnUnitsToCargo() {
		getCargo().addMarines(personnel);
		getCargo().addCommodity(Commodities.HAND_WEAPONS, heavyArms);
		
		// move the XP from battle player data to player cargo
		PlayerFleetPersonnelTracker.transferPersonnel(
				intel.playerData.xpTracker.data,
				PlayerFleetPersonnelTracker.getInstance().getMarineData(), 
				personnel, null);
		personnel = 0;
		heavyArms = 0;
	}
	
	public void setLocation(IndustryForBattle newLoc) {
		if (newLoc == location) return;
		if (location != null) location.removeUnit(this);
		location = newLoc;
		if (newLoc != null) newLoc.addUnit(this);
	}
	
	public void setDestination(IndustryForBattle destination) {
		if (destination == location) {
			cancelMove();
			return;
		}
		this.destination = destination;
		intel.getSide(isAttacker).getMovementPointsSpent().modifyFlat(id + "_move", getDeployCost());
		currAction = null;
	}
	
	public void cancelMove() {
		destination = null;
		currAction = null;
		intel.getSide(isAttacker).getMovementPointsSpent().unmodifyFlat(id + "_move");
	}
	
	public void inflictAttrition(float amount, GroundBattleRoundResolve resolve, 
			InteractionDialogAPI dialog) 
	{
		if (resolve == null)
			resolve = new GroundBattleRoundResolve(intel);
		int losses = (int)(this.getSize() * amount);
		if (losses > 0) {
			float morale = resolve.damageUnitMorale(this, losses);
			resolve.inflictUnitLosses(this, losses);
			
			if (isPlayer && dialog != null) {
				TextPanelAPI text = dialog.getTextPanel();
				text.setFontSmallInsignia();
				String moraleStr = StringHelper.toPercent(morale);
				String str = String.format(getString("deployAttrition"), losses, moraleStr);
				Color neg = Misc.getNegativeHighlightColor();
				LabelAPI para = text.addPara(str);
				para.setHighlight(losses + "", moraleStr);
				para.setHighlightColors(neg, neg);
				text.setFontInsignia();
			}
		}
	}
	
	public void deploy(IndustryForBattle newLoc, InteractionDialogAPI dialog) {		
		setLocation(newLoc);
		int cost = getDeployCost();
		if (isPlayer) getCargo().removeSupplies(cost);
		if (dialog != null) {
			AddRemoveCommodity.addCommodityLossText(Commodities.SUPPLIES, cost, dialog.getTextPanel());
		}
		destination = null;
		float attrition = intel.getSide(isAttacker).dropAttrition.getModifiedValue()/100;
		if (attrition > 0) {
			Global.getLogger(this.getClass()).info(String.format(
					"%s receiving %s attrition during deployment", 
					toString(), StringHelper.toPercent(attrition)));
			inflictAttrition(attrition, null, dialog);
		}
		if (true || isPlayer) {
			GroundBattleLog log = new GroundBattleLog(intel, GroundBattleLog.TYPE_UNIT_MOVED, intel.turnNum);
			log.params.put("unit", this);
			log.params.put("location", location);
			intel.addLogEvent(log);
		}
		intel.getSide(isAttacker).getMovementPointsSpent().modifyFlat(id + "_deploy", cost);
		for (GroundBattlePlugin plugin : intel.getPlugins()) {
			plugin.reportUnitMoved(this, null);
		}
	}
	
	public void orderWithdrawal() {
		currAction = GBConstants.ACTION_WITHDRAW;
		intel.getSide(isAttacker).getMovementPointsSpent().modifyFlat(id + "_move", getDeployCost());
		destination = null;
	}
	
	public void executeMove() {
		IndustryForBattle lastLoc = location;
		if (isWithdrawing()) {
			if (intel.isPlayerInRange()) {
				setLocation(null);
				currAction = null;
			}
		}
		else {
			if (destination == null) return;
			intel.getMovedFromLastTurn().put(this, location);
			setLocation(destination);
			destination = null;
		}
		if (true || isPlayer) {
			GroundBattleLog log = new GroundBattleLog(intel, GroundBattleLog.TYPE_UNIT_MOVED, intel.turnNum);
			log.params.put("unit", this);
			log.params.put("previous", lastLoc);
			log.params.put("location", location);
			intel.addLogEvent(log);
		}
		for (GroundBattlePlugin plugin : intel.getPlugins()) {
			plugin.reportUnitMoved(this, lastLoc);
		}
	}
	
	public void removeUnit(boolean returnToCargo) 
	{
		if (isPlayer && returnToCargo) {
			NexUtils.modifyMapEntry(intel.playerData.getDisbanded(), type, getSize());
			Global.getLogger(this.getClass()).info("Disbanding " + name + ": " + getSize());
			int inFleet = intel.countPersonnelFromMap(intel.playerData.getDisbanded());
			Global.getLogger(this.getClass()).info("Disbanded personnel count: " + getSize());
			returnUnitsToCargo();
		}
		setLocation(null);
		intel.getSide(isAttacker).units.remove(this);
		if (isPlayer)
			intel.playerData.getUnits().remove(this);
		
		Global.getLogger(this.getClass()).info(String.format("Removed unit %s (%s)", name, type));
	}
	
	public void destroyUnit(float recoverProportion) {
		cancelMove();
		if (isPlayer && intel.isPlayerInRange()) {
			getCargo().addMarines((int)(personnel * recoverProportion));
			getCargo().addCommodity(Commodities.HAND_WEAPONS, (int)(heavyArms * recoverProportion));
		}
		GroundBattleLog lg = new GroundBattleLog(intel, GroundBattleLog.TYPE_UNIT_DESTROYED, intel.turnNum);
		lg.params.put("unit", this);
		lg.params.put("location", this.location);
		intel.addLogEvent(lg);	
		removeUnit(false);
	}
	
	/**
	 * Spend an additional {@code turns} reorganizing. Negative values to progress reorganization.
	 * @param turns
	 */
	public void reorganize(int turns) {
		Integer curr = (Integer)data.get("reorganizing");
		if (curr == null) curr = 0;
		int newVal = curr + turns;
		if (newVal <= 0) data.remove("reorganizing");
		else data.put("reorganizing", newVal);
	}
	
	public boolean isReorganizing() {
		return data.containsKey("reorganizing");
	}
	
	public void preventAttack(int turns) {
		Integer curr = (Integer)data.get("preventAttack");
		if (curr == null) curr = 0;
		int newVal = curr + turns;
		if (newVal <= 0) data.remove("preventAttack");
		else data.put("preventAttack", newVal);
	}
	
	public boolean isAttackPrevented() {
		return data.containsKey("preventAttack");
	}
	
	public void addActionText(TooltipMakerAPI info) {
		Color color = Misc.getTextColor();
		String strId = "currAction";
		
		if (GBConstants.ACTION_WITHDRAW.equals(currAction)) {
			strId += "Withdrawing";
		}
		else if (isReorganizing() && isAttackPrevented()) {
			strId += "Shocked";
			color = Misc.getNegativeHighlightColor();
		}
		else if (isReorganizing()) {
			strId += "Reorganizing";
			color = Misc.getNegativeHighlightColor();
		}
		else if (destination != null) {
			strId += "Moving";
		}
		else if (location == null) {
			strId += "WaitingFleet";
		}
		else if (location.isContested()) {
			strId += "Engaged";
		}
		else {
			strId += "Waiting";
		}
		String str = getString(strId);
		if (destination != null) {
			str = String.format(str, destination.ind.getCurrentName());
		}
		else if (location != null && location.isContested()) {
			str = String.format(str, location.ind.getCurrentName());
		}
		
		info.addPara(str, color, 0);
	}
	
	/**
	 * Clamped to range [0, 1].
	 * @param delta
	 * @return Delta after clamping.
	 */
	public float modifyMorale(float delta) {
		return modifyMorale(delta, 0, 1);
	}
	
	/**
	 * Clamped to range [min, max].
	 * @param delta
	 * @param min
	 * @param max
	 * @return Delta after clamping.
	 */
	public float modifyMorale(float delta, float min, float max) {
		float newMorale = morale + delta;
		if (newMorale > max) newMorale = max;
		if (newMorale < 0) newMorale = 0;
		
		// prevent "going backwards"
		if (delta > 0 && newMorale < morale) {
			newMorale = morale;
		}
		else if (delta < 0 && newMorale > morale) {
			newMorale = morale;
		}
		
		delta = newMorale - morale;
		
		morale = newMorale;
		moraleDeltaLastTurn += delta;
		return delta;
	}
	
	/**
	 * Unit size multiplied by the unit type's strength multiplier.
	 * @return
	 */
	public float getBaseStrength() {
		return getSize() * type.strength;
	}
	
	public static float getBaseStrengthForAverageUnit(UnitSize size, ForceType type) 
	{
		return size.avgSize * type.strength;
	}
	
	public int getDeployCost() {
		float cost = getSize() * GBConstants.SUPPLIES_TO_DEPLOY_MULT;
		if (type == ForceType.HEAVY) cost *= HEAVY_COUNT_DIVISOR;
		cost *= type.dropCostMult;
		cost = intel.getSide(isAttacker).dropCostMod.computeEffective(cost);
		return Math.round(cost);
	}
	
	protected void modifyAttackStatWithDesc(MutableStat stat, String id, float mult) 
	{
		String desc = getString("unitCard_tooltip_atkbreakdown_" + id);
		stat.modifyMult(id, mult, desc);
	}
	
	/**
	 * Partial attack stat, before fleet/market bonuses are applied.
	 * @return
	 */
	public MutableStat getAttackStat() {
		MutableStat stat = new MutableStat(0);
		
		if (isAttackPrevented()) {
			stat.modifyMult("disabled", 0, getString("unitCard_tooltip_atkbreakdown_disabled"));
			return stat;
		}
		
		float baseStr = getBaseStrength();
		stat.modifyFlat("base", baseStr, getString("unitCard_tooltip_atkbreakdown_base"));
		
		IndustryForBattle ifb = location;
		
		if (ifb != null) 
		{
			if (ifb.heldByAttacker == isAttacker) {
				// apply strength modifiers to defense instead, not attack
				/*
				float industryMult = ifb.getPlugin().getStrengthMult();
				if (industryMult != 1) {
					stat.modifyMult("industry", industryMult, ifb.ind.getCurrentName());
				}
				*/
			} else if (type == ForceType.HEAVY) {	// heavy unit bonus on offensive
				modifyAttackStatWithDesc(stat, "heavy_offensive", GBConstants.HEAVY_OFFENSIVE_MULT);
			}
		}
		
		if (intel.isCramped()) {
			if (type == ForceType.HEAVY) {
				modifyAttackStatWithDesc(stat, "heavy_cramped", GBConstants.HEAVY_STATION_MULT);
			}
		}
		
		if (type == ForceType.REBEL)
			modifyAttackStatWithDesc(stat, "rebel", GBConstants.REBEL_DAMAGE_MULT);
		
		float moraleMult = NexUtilsMath.lerp(1 - GBConstants.MORALE_ATTACK_MOD, 
				1 + GBConstants.MORALE_ATTACK_MOD, morale);
		modifyAttackStatWithDesc(stat, "morale", moraleMult);
				
		if (isReorganizing()) {
			modifyAttackStatWithDesc(stat, "reorganizing", GBConstants.REORGANIZING_DMG_MULT);
		}
				
		for (GroundBattlePlugin plugin : intel.getPlugins()) {
			plugin.modifyDamageDealt(this, stat);
		}
		
		return stat;
	}
	
	// 

	/**
	 * Hack to replace fleet marine XP bonus with the local XP bonus.
	 * @param stats
	 * @param attackPower True to get modifier to attack power, false to get reduction to casualties.
	 */
	public void substituteLocalXPBonus(StatBonus stats, boolean attackPower) {
		PersonnelData data = intel.playerData.xpTracker.data;
		injectXPBonus(stats, data, attackPower);
	}
	
	/**
	 * Applies the XP bonus from the provided {@code PersonnelData} to the provided stats.
	 * @param stats
	 * @param data
	 * @param attackPower True to get modifier to attack power, false to get reduction to casualties.
	 */
	public void injectXPBonus(StatBonus stats, PersonnelData data, boolean attackPower) {
		String id = "marineXP";
		float effectBonus = PlayerFleetPersonnelTracker.getInstance().getMarineEffectBonus(data);
		float casualtyReduction = PlayerFleetPersonnelTracker.getInstance().getMarineLossesReductionPercent(data);
		//Global.getLogger(this.getClass()).info(String.format("XP %s translating to %s effect bonus, %s casualty red.", data.xp, effectBonus, casualtyReduction));
		PersonnelRank rank = data.getRank();
		if (attackPower) {
			if (effectBonus > 0) {
			//stats.getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD).modifyMult(id, 1f + effectBonus * 0.01f, rank.name + " marines");
				stats.modifyPercent(id, effectBonus, rank.name + " " + StringHelper.getString("marines"));
			} else {
				//stats.getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD).unmodifyMult(id);
				stats.unmodifyPercent(id);
			}
		}
		else {
			if (casualtyReduction > 0) {
				stats.modifyMult(id, 1f - casualtyReduction * 0.01f, rank.name + " " + StringHelper.getString("marines"));
			} else {
				stats.unmodifyMult(id);
			}
		}
	}
	
	public StatBonus getAttackStatBonus() {
		if (isAttacker) {
			if (fleet != null && intel.isFleetInRange(fleet)) {
				StatBonus bonus = NexUtils.cloneStatBonus(fleet.getStats().getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD));
				if (isPlayer) {
					substituteLocalXPBonus(bonus, true);
				}
				return bonus;
			}
		}
		else {
			StatBonus bonus = NexUtils.cloneStatBonus(intel.market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD));
			bonus.getFlatBonuses().clear();
			for (StatMod mod : new ArrayList<>(bonus.getMultBonuses().values())) 
			{
				if (mod.getSource().startsWith("ind_")) {
					bonus.unmodifyMult(mod.getSource());
				}
			}
			for (StatMod mod : new ArrayList<>(bonus.getPercentBonuses().values())) 
			{
				if (mod.getSource().startsWith("ind_")) {
					bonus.unmodifyPercent(mod.getSource());
				}
			}
			injectXPBonus(bonus, GBConstants.DEFENSE_STAT, true);
			
			return bonus;
			//return intel.market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD);
		}
		return null;
	}
	
	public float getAttackStrength() {
		if (isAttackPrevented()) return 0;
		
		MutableStat stat = getAttackStat();
		
		float output = stat.getModifiedValue();
		
		StatBonus bonus = getAttackStatBonus();
		if (bonus != null) {
			output = bonus.computeEffective(output);
		}
		
		output = intel.getSide(isAttacker).damageDealtMod.computeEffective(output);
		
		return output;
	}
	
	public float getAdjustedMoraleDamageTaken(float dmg) {
		float mult = 1;
		if (isPlayer) {
			//mult -= GBConstants.MORALE_DAM_XP_REDUCTION_MULT * PlayerFleetPersonnelTracker.getInstance().getMarineData().getXPLevel();
		}
		mult /= type.moraleMult;
		dmg = intel.getSide(isAttacker).moraleDamTakenMod.computeEffective(dmg);
		dmg *= mult;
		
		if (!isAttacker) dmg *= GBConstants.DEFENDER_MORALE_DMG_MULT;
		
		for (GroundBattlePlugin plugin : intel.getPlugins()) {
			dmg = plugin.modifyMoraleDamageReceived(this, dmg);
		}		
		return dmg;
	}
	
	public StatBonus getDefenseStatBonus() {
		if (isAttacker) {
			if (fleet != null) {
				StatBonus bonus = NexUtils.cloneStatBonus(fleet.getStats().getDynamic().getMod(Stats.PLANETARY_OPERATIONS_CASUALTIES_MULT));
				if (isPlayer) {
					substituteLocalXPBonus(bonus, false);
				}
			}
		}
		else {
			StatBonus bonus = new StatBonus();
			injectXPBonus(bonus, GBConstants.DEFENSE_STAT, false);
			return bonus;
			//return intel.market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD);
		}
		return null;
	}
	
	public float getAdjustedDamageTaken(float dmg) {
		if (location != null && isAttacker == location.heldByAttacker)
			dmg *= 1/location.getPlugin().getStrengthMult();
		
		if (type == ForceType.REBEL)
			dmg *= GBConstants.REBEL_DAMAGE_MULT;
				
		for (GroundBattlePlugin plugin : intel.getPlugins()) {
			dmg = plugin.modifyDamageReceived(this, dmg);
		}
		
		StatBonus bonus = getDefenseStatBonus();
		if (bonus != null) {
			dmg = bonus.computeEffective(dmg);
		}
		
		dmg = intel.getSide(isAttacker).damageTakenMod.computeEffective(dmg);
		return dmg;
	}
	
	public int getSize() {
		int num = type == ForceType.HEAVY ? heavyArms : personnel;
		return num;
	}
	
	/**
	 * e.g. two half-companies and one full company will return about the same value.
	 * @return
	 */
	public float getNumUnitEquivalents() {
		float num = type == ForceType.HEAVY ? heavyArms : personnel;
		return num/intel.unitSize.getAverageSizeForType(type);
	}
	
	public static CustomPanelAPI createBlankCard(CustomPanelAPI parent, UnitSize size) 
	{
		FactionAPI faction = Global.getSector().getPlayerFaction();
		CargoAPI cargo = getCargo();
		
		CustomPanelAPI card = parent.createCustomPanel(PANEL_WIDTH, PANEL_HEIGHT, 
				new GroundUnitPanelPlugin(faction, null, faction.getCrest()));
		
		float btnWidth = 160;
		
		TooltipMakerAPI buttonHolder = card.createUIElement(PANEL_WIDTH, PANEL_HEIGHT, false);
		ButtonAPI newMarine = buttonHolder.addButton(String.format(getString("btnNewUnitMarine")
				, size.getName()), BUTTON_NEW_MARINE, btnWidth, 24, 0);
		if (cargo.getMarines() <= 0) newMarine.setEnabled(false);
		
		ButtonAPI newHeavy = buttonHolder.addButton(String.format(getString("btnNewUnitHeavy")
				, size.getName()), BUTTON_NEW_HEAVY, btnWidth, 24, 0);
		if (cargo.getMarines() < CREW_PER_MECH || cargo.getCommodityQuantity(Commodities.HAND_WEAPONS) <= 0) 
			newHeavy.setEnabled(false);
		
		card.addUIElement(buttonHolder).inTL((PANEL_WIDTH-btnWidth)/2, PANEL_HEIGHT/2 - 24);
		
		return card;
	}
	
	public CustomPanelAPI createUnitCard(CustomPanelAPI parent, boolean forDialog)
	{
		float sizeMult = 1, pad = 3;
		if (forDialog) {
			sizeMult = 1.5f;
			pad = 4.5f;
		}
		
		String commoditySprite = Global.getSettings().getCommoditySpec(type.commodityId).getIconName();
		String crest = faction.getCrest();
		
		CustomPanelAPI card = parent.createCustomPanel(PANEL_WIDTH * sizeMult, 
				PANEL_HEIGHT * sizeMult, 
				new GroundUnitPanelPlugin(faction, commoditySprite, crest));
		TooltipMakerAPI title = card.createUIElement(PANEL_WIDTH * sizeMult, 
				TITLE_HEIGHT * sizeMult, false);
		if (forDialog) title.setParaSmallInsignia();
		title.addPara(name, faction.getBaseUIColor(), pad);
		card.addUIElement(title).inTL(0, 0);
		
		// begin stats section
		TooltipMakerAPI stats = card.createUIElement((PANEL_WIDTH - BUTTON_SECTION_WIDTH)/2 * sizeMult, 
				(PANEL_HEIGHT - TITLE_HEIGHT - LOCATION_SECTION_HEIGHT) * sizeMult, false);
		
		// number of marines
		TooltipMakerAPI line = stats.beginImageWithText(Global.getSettings().getCommoditySpec(
				Commodities.MARINES).getIconName(), 16 * sizeMult);
		line.addPara(personnel + "", 0);
		stats.addImageWithText(pad);
		stats.addTooltipToPrevious(createTooltip("marines"), TooltipLocation.BELOW);
		
		// number of heavy arms
		if (heavyArms > 0) {
			line = stats.beginImageWithText(Global.getSettings().getCommoditySpec(
					Commodities.HAND_WEAPONS).getIconName(), 16 * sizeMult);
			line.addPara(heavyArms + "", 0);
			stats.addImageWithText(pad);
			stats.addTooltipToPrevious(createTooltip("heavyArms"), TooltipLocation.BELOW);
		}
		else {
			stats.addSpacer(19 * sizeMult);
		}
		
		// morale
		line = stats.beginImageWithText("graphics/icons/insignia/16x_star_circle.png", 
				16 * sizeMult);
		Color moraleColor = getMoraleColor(morale);
		String moraleStr = Math.round(this.morale * 100) + "%";
		line.addPara(moraleStr, moraleColor, 0);
		stats.addImageWithText(pad);
		stats.addTooltipToPrevious(createTooltip("morale"), TooltipLocation.BELOW);
		
		card.addUIElement(stats).belowLeft(title, 0);
		
		TooltipMakerAPI stats2 = card.createUIElement((PANEL_WIDTH - BUTTON_SECTION_WIDTH)/2 * sizeMult, 
				(PANEL_HEIGHT - TITLE_HEIGHT - LOCATION_SECTION_HEIGHT) * sizeMult, false);
		
		// attack power;
		line = stats2.beginImageWithText(Global.getSettings().getSpriteName("misc", 
				"nex_groundunit_attackpower"), 16 * sizeMult);
		line.addPara(String.format("%.0f", getAttackStrength()), 0);
		stats2.addImageWithText(pad);
		stats2.addTooltipToPrevious(createTooltip("attackPower"), TooltipLocation.BELOW);
		card.addUIElement(stats2).rightOfTop(stats, 0);
		
		// deploy cost
		if (true || location == null) {
			line = stats2.beginImageWithText(Global.getSettings().getCommoditySpec(
					Commodities.SUPPLIES).getIconName(), 16 * sizeMult);
			line.addPara(getDeployCost() + "", 0);
			stats2.addImageWithText(pad);
			stats2.addTooltipToPrevious(createTooltip("supplies"), TooltipLocation.BELOW);
		}
		else {
			//stats.addSpacer(19);
		}		
		// end stats section
		
		// location
		TooltipMakerAPI loc = card.createUIElement(PANEL_WIDTH * sizeMult, 
				LOCATION_SECTION_HEIGHT * sizeMult, false);
		
		// with image version
		if (USE_LOCATION_IMAGE) {
			String img = location != null ? location.ind.getCurrentImage() : "graphics/illustrations/free_orbit.jpg";
			line = loc.beginImageWithText(img, 32 * sizeMult);
			addActionText(line);
			loc.addImageWithText(pad);
			
		} else {
			if (location != null) {
				String locStr = String.format(getString("currLocation"), location.getName());
				loc.addPara(locStr, 0);
			}
			addActionText(loc);
		}
		card.addUIElement(loc).inBL(0, 2 * sizeMult);
		
		
		// button holder
		if (!forDialog) {
			TooltipMakerAPI buttonHolder = card.createUIElement(BUTTON_SECTION_WIDTH * sizeMult, 
				PANEL_HEIGHT * sizeMult * 2, false);
			buttonHolder.addButton(StringHelper.getString("action", true), this, 
					(BUTTON_SECTION_WIDTH - 6) * sizeMult, 16 * sizeMult, 0);
			ButtonAPI qm = buttonHolder.addButton(getString("btnQuickMove", true), new UnitQuickMoveHax(this), 
					(BUTTON_SECTION_WIDTH - 6) * sizeMult, 16 * sizeMult, 0);
			if (isReorganizing()) {
				qm.setEnabled(false);
			}
			card.addUIElement(buttonHolder).inTR(1 * sizeMult, 2 * sizeMult);
		}
		
		return card;
	}
	
	public static Color getMoraleColor(float morale) {
		Color color = Misc.getHighlightColor();
		if (morale > .6) color = Misc.getPositiveHighlightColor();
		else if (morale < .3) color = Misc.getNegativeHighlightColor();
		return color;
	}
	
	public TooltipCreator createTooltip(final String id) {
		final GroundUnit unit = this;
		return new TooltipCreator() {
			@Override
			public boolean isTooltipExpandable(Object tooltipParam) {
				return false;
			}

			@Override
			public float getTooltipWidth(Object tooltipParam) {
				return 280;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
				String str = GroundBattleIntel.getString("unitCard_tooltip_" + id);
				tooltip.addPara(str, 0);
				if (id.equals("morale")) {
					Color hl = Misc.getHighlightColor();
					//tooltip.setBulletedListMode(BaseIntelPlugin.BULLET);
					str = " - " + GroundBattleIntel.getString("unitCard_tooltip_" + id + 2);
					tooltip.addPara(str, 3, hl, 
							StringHelper.toPercent(1 - GBConstants.MORALE_ATTACK_MOD),
							StringHelper.toPercent(1 + GBConstants.MORALE_ATTACK_MOD)
					);
					str = " - " + GroundBattleIntel.getString("unitCard_tooltip_" + id + 3);
					tooltip.addPara(str, 0, hl, StringHelper.toPercent(GBConstants.REORGANIZE_AT_MORALE));
					str = " - " + GroundBattleIntel.getString("unitCard_tooltip_" + id + 4);
					tooltip.addPara(str, 0);
				} else if (id.equals("attackPower")) {
					str = getString("unitCard_tooltip_atkbreakdown_header");
					tooltip.addPara(str, 3); 
					tooltip.addStatModGrid(360, 60, 10, 3, getAttackStat(), true, NexUtils.getStatModValueGetter(true, 0));
					
					StatBonus bonus = unit.getAttackStatBonus();
					if (bonus != null && !bonus.isUnmodified()) {
						tooltip.addStatModGrid(360, 60, 10, 3, bonus, true, NexUtils.getStatModValueGetter(true, 0));
					}
					
					bonus = intel.getSide(isAttacker).getDamageDealtMod();
					if (bonus != null && !bonus.isUnmodified()) {
						tooltip.addStatModGrid(360, 60, 10, 3, bonus, true, NexUtils.getStatModValueGetter(true, 0));
					}
				}
			}
		};
	}
	
	@Override
	public String toString() {
		return String.format("%s (%s)", name, type.toString().toLowerCase());
	}
	
	public static enum ForceType {
		MARINE(Commodities.MARINES, "troopNameMarine", 1, 1, 1), 
		HEAVY(Commodities.HAND_WEAPONS, "troopNameMech", 6, 1, GBConstants.HEAVY_DROP_COST_MULT),
		MILITIA(Commodities.CREW, "troopNameMilitia", 0.4f, 0.6f, 1), 
		REBEL(Commodities.CREW, "troopNameRebel", 0.8f, 0.7f, 1);	// note that attack power is halved later
		
		public final String commodityId;
		public final String nameStringId;
		public final float strength;
		public final float moraleMult;
		public final float dropCostMult;
		
		private ForceType(String commodityId, String nameStringId, float strength, 
				float moraleMult, float dropCostMult) 
		{
			this.commodityId = commodityId;
			this.nameStringId = nameStringId;
			this.strength = strength;
			this.moraleMult = moraleMult;
			this.dropCostMult = dropCostMult;
		}
		
		public String getName() {
			return getString(nameStringId);
		}
		
		public String getCommodityName() {
			return Global.getSettings().getCommoditySpec(commodityId).getName();
		}
	}
	
	public static enum UnitSize {
		PLATOON(40, 60, 1f),
		COMPANY(120, 200, 0.5f),
		BATTALION(500, 800, 0.25f),
		REGIMENT(2000, 3500, 0.1f);
		
		public int avgSize;
		public int maxSize;
		public float damMult;
		
		private UnitSize(int avgSize, int maxSize, float damMult) {
			this.avgSize = avgSize;
			this.maxSize = maxSize;
			this.damMult = damMult;
		}
		
		public int getAverageSizeForType(ForceType type) {
			int count = avgSize;
			if (type == ForceType.HEAVY) count = Math.round(count/GroundUnit.HEAVY_COUNT_DIVISOR);
			return count;
		}
		
		public String getName() {
			return getString("unit" + Misc.ucFirst(toString().toLowerCase()));
		}
		
		public String getNamePlural() {
			return getString("unit" + Misc.ucFirst(toString().toLowerCase()) + "Plural");
		}
	}
	
	public static class UnitQuickMoveHax {
		public GroundUnit unit;
		
		public UnitQuickMoveHax(GroundUnit unit) {
			this.unit = unit;
		}
	}
}
