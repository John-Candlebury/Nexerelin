package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker.PersonnelAtEntity;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_BuyColony;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.intel.MarketTransferIntel;
import exerelin.campaign.intel.groundbattle.GBDataManager.ConditionDef;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
import exerelin.campaign.intel.groundbattle.GroundUnit.UnitQuickMoveHax;
import exerelin.campaign.intel.groundbattle.GroundUnit.UnitSize;
import exerelin.campaign.intel.groundbattle.dialog.AbilityDialogPlugin;
import exerelin.campaign.intel.groundbattle.dialog.UnitOrderDialogPlugin;
import exerelin.campaign.intel.groundbattle.plugins.AbilityPlugin;
import exerelin.campaign.intel.groundbattle.plugins.FactionBonusPlugin;
import exerelin.campaign.intel.groundbattle.plugins.FleetSupportPlugin;
import exerelin.campaign.intel.groundbattle.plugins.GeneralPlugin;
import exerelin.campaign.intel.groundbattle.plugins.GroundBattlePlugin;
import exerelin.campaign.intel.groundbattle.plugins.MarketConditionPlugin;
import exerelin.campaign.intel.groundbattle.plugins.MarketMapDrawer;
import exerelin.campaign.intel.groundbattle.plugins.PlanetHazardPlugin;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.ColonyNPCHostileActListener;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

// may not actually use this in the end and just go with the "disrupt everything" system
public class GroundBattleIntel extends BaseIntelPlugin implements 
		ColonyPlayerHostileActListener, ColonyNPCHostileActListener {
	
	public static int MAX_PLAYER_UNITS = 16;
	public static final boolean ALWAYS_RETURN_TO_FLEET = false;
	
	public static final float VIEW_BUTTON_WIDTH = 128;
	public static final float VIEW_BUTTON_HEIGHT = 24;
	public static final float MODIFIER_PANEL_HEIGHT = 160;
	public static final float ABILITY_PANEL_HEIGHT = 160;
	
	public static final Object UPDATE_TURN = new Object();
	public static final Object UPDATE_VICTORY = new Object();
	public static final Object BUTTON_RESOLVE = new Object();
	public static final Object BUTTON_AUTO_MOVE = new Object();
	public static final Object BUTTON_AUTO_MOVE_TOGGLE = new Object();
	public static final Object BUTTON_DEBUG_AI = new Object();
	public static final Object BUTTON_ANDRADA = new Object();
	public static final Object BUTTON_GOVERNORSHIP = new Object();
	
	public static Logger log = Global.getLogger(GroundBattleIntel.class);
	
	protected UnitSize unitSize;
	
	protected int turnNum;
	protected BattleOutcome outcome;
	protected int recentUnrest = 0;
	protected Float timerForDecision;
	
	protected MarketAPI market;
	protected InvasionIntel intel;
	protected boolean playerInitiated;
	protected Boolean playerIsAttacker;
	
	protected GroundBattleSide attacker;
	protected GroundBattleSide defender;
	protected GBPlayerData playerData;
		
	protected transient ViewMode viewMode;
	
	protected List<GroundBattleLog> battleLog = new LinkedList<>();
	//protected transient List<String> rawLog;
	protected Map<GroundUnit, IndustryForBattle> movedFromLastTurn = new HashMap<>();	// value is the industry the unit was on last turn
	
	protected List<IndustryForBattle> industries = new ArrayList<>();
	protected List<GroundBattlePlugin> marketConditionPlugins = new LinkedList<>();
	protected List<GroundBattlePlugin> otherPlugins = new LinkedList<>();
	
	protected Map<String, Object> data = new HashMap<>();
	
	protected IntervalUtil interval = new IntervalUtil(1, 1);
	protected IntervalUtil intervalShort = new IntervalUtil(0.2f, 0.2f);
	protected MilitaryResponseScript responseScript;
	
	protected transient List<Pair<Boolean, AbilityPlugin>> abilitiesUsedLastTurn = new ArrayList<>();
	
	// =========================================================================
	// setup, getters/setters and other logic
	
	public GroundBattleIntel(MarketAPI market, FactionAPI attacker, FactionAPI defender)
	{
		this.market = market;
		
		int size = market.getSize();
		if (size <= 3)
			unitSize = UnitSize.PLATOON;
		else if (size <= 5)
			unitSize = UnitSize.COMPANY;
		else if (size <= 7)
			unitSize = UnitSize.BATTALION;
		else
			unitSize = UnitSize.REGIMENT;
		
		this.attacker = new GroundBattleSide(this, true);
		this.defender = new GroundBattleSide(this, false);
		this.attacker.faction = attacker;
		this.defender.faction = defender;
		
		playerData = new GBPlayerData(this);
	}
	
	protected Object readResolve() {
		if (marketConditionPlugins == null)
			marketConditionPlugins = new ArrayList<>();
		if (abilitiesUsedLastTurn == null)
			abilitiesUsedLastTurn = new ArrayList<>();
		if (movedFromLastTurn == null)
			movedFromLastTurn = new HashMap<>();
		
		return this;
	}
	
	protected void generateDebugUnits() 
	{
		for (int i=0; i<6; i++) {
			GroundUnit unit = new GroundUnit(this, ForceType.MARINE, 0, i);
			unit.faction = Global.getSector().getPlayerFaction();
			unit.isPlayer = true;
			unit.isAttacker = this.playerIsAttacker;
			unit.type = i >= 4 ? ForceType.HEAVY : ForceType.MARINE;
			
			if (unit.type == ForceType.HEAVY) {
				unit.heavyArms = Math.round(this.unitSize.avgSize / GroundUnit.HEAVY_COUNT_DIVISOR * MathUtils.getRandomNumberInRange(1, 1.4f));
				unit.personnel = unit.heavyArms * 2;
			} else {
				unit.personnel = Math.round(this.unitSize.avgSize * MathUtils.getRandomNumberInRange(1, 1.4f));
				//unit.heavyArms = MathUtils.getRandomNumberInRange(10, 15);
			}
			
			IndustryForBattle loc = industries.get(MathUtils.getRandomNumberInRange(0, industries.size() - 1));
			//unit.setLocation(loc);
			if (unit.morale < GBConstants.REORGANIZE_AT_MORALE) {
				unit.reorganize(1);
			}
			else if (Math.random() > 0.5f) {
				//unit.dest = industries.get(MathUtils.getRandomNumberInRange(0, industries.size() - 1));
			}
			attacker.units.add(unit);
			playerData.units.add(unit);
		}
	}
	
	public void initPlugins() {
		GeneralPlugin general = new GeneralPlugin();
		general.init(this);
		otherPlugins.add(general);
		
		FactionBonusPlugin fb = new FactionBonusPlugin();
		fb.init(this);
		otherPlugins.add(fb);
		
		PlanetHazardPlugin hazard = new PlanetHazardPlugin();
		hazard.init(this);
		otherPlugins.add(hazard);
		
		FleetSupportPlugin fSupport = new FleetSupportPlugin();
		fSupport.init(this);
		otherPlugins.add(fSupport);
	}
	
	public List<GroundBattlePlugin> getOtherPlugins() {
		return otherPlugins;
	}
	
	public void addOtherPlugin(GroundBattlePlugin plugin) {
		otherPlugins.add(plugin);
	}
	
	public void removePlugin(GroundBattlePlugin plugin) {
		if (marketConditionPlugins.contains(plugin))
			marketConditionPlugins.remove(plugin);
		if (otherPlugins.contains(plugin))
			otherPlugins.remove(plugin);
	}
	
	public void initMarketConditions() {
		for (MarketConditionAPI cond : market.getConditions()) {
			String condId = cond.getId();
			ConditionDef def = GBDataManager.getConditionDef(condId);
			if (def != null) {
				log.info("Processing condition " + condId);
				if (def.tags.contains("cramped"))
					data.put("cramped", true);
				if (def.plugin != null) {
					MarketConditionPlugin plugin = MarketConditionPlugin.loadPlugin(this, condId);
					marketConditionPlugins.add(plugin);
				}
			}
		}
	}
	
	/**
	 * Should be called after relevant parameters are set.
	 */
	public void init() {
		turnNum = 1;
		
		List<Industry> mktIndustries = new ArrayList<>(market.getIndustries());
		Collections.sort(mktIndustries, INDUSTRY_COMPARATOR);
		for (Industry ind : mktIndustries) {
			if (ind.getId().equals(Industries.POPULATION)) continue;
			if (ind.isHidden()) continue;
			if (ind.getSpec().hasTag(Industries.TAG_STATION)) continue;
			
			addIndustry(ind.getId());
		}
		if (industries.isEmpty()) {
			addIndustry(Industries.POPULATION);
		}
		
		if (playerInitiated) {
			attacker.commander = Global.getSector().getPlayerPerson();
		}
		defender.commander = market.getAdmin();
		
		if (market.getPlanetEntity() == null) {
			data.put("cramped", true);
		}
		
		initPlugins();
		initMarketConditions();		
		
		reapply();
	}
	
	public void start() {
		defender.generateDefenders();
		if (playerInitiated) {
			autoGeneratePlayerUnits();
			this.setImportant(true);
		}
		
		addMilitaryResponse();
		Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().addScript(this);
		Global.getSector().getListenerManager().addListener(this);
		
		for (GroundBattleCampaignListener x : Global.getSector().getListenerManager().getListeners(GroundBattleCampaignListener.class)) 
		{
			x.reportBattleStarted(this);
		}
	}
	
	public void initDebug() {
		playerInitiated = true;
		playerIsAttacker = true;
		init();
		generateDebugUnits();
	}
	
	public IndustryForBattle addIndustry(String industry) 
	{
		Industry ind = market.getIndustry(industry);
		IndustryForBattle ifb = new IndustryForBattle(this, ind);
		
		industries.add(ifb);
		return ifb;
	}
	
	public List<GroundBattlePlugin> getMarketConditionPlugins() {
		return marketConditionPlugins;
	}
	
	public List<GroundBattlePlugin> getPlugins() {
		List<GroundBattlePlugin> list = new ArrayList<>();
		list.addAll(otherPlugins);
		list.addAll(marketConditionPlugins);
		for (IndustryForBattle ifb : industries) {
			if (ifb.getPlugin() == null) {
				log.warn("Null plugin for " + ifb.ind.getId());
				continue;
			}
			list.add(ifb.getPlugin());
		}
		
		return list;
	}
	
	public MarketAPI getMarket() {
		return market;
	}
	
	public List<IndustryForBattle> getIndustries() {
		return industries;
	}
	
	public UnitSize getUnitSize() {
		return unitSize;
	}
	
	public IndustryForBattle getIndustryForBattleByIndustry(Industry ind) {
		for (IndustryForBattle ifb : industries) {
			if (ifb.getIndustry() == ind) {
				return ifb;
			}
		}
		return null;
	}
	
	public GroundBattleSide getSide(boolean isAttacker) {
		if (isAttacker) return attacker;
		else return defender;
	}
	
	public GBPlayerData getPlayerData() {
		return playerData;
	}
	
	public Map<GroundUnit, IndustryForBattle> getMovedFromLastTurn() {
		return movedFromLastTurn;
	}
	
	public Map<String, Object> getCustomData() {
		return data;
	}
	
	public BattleOutcome getOutcome() {
		return outcome;
	}
	
	public Boolean isPlayerAttacker() {
		return playerIsAttacker;
	}
	
	public boolean isPlayerAttackerForGUI() {
		Boolean isAttacker = this.isPlayerAttacker();
		if (isAttacker != null) return isAttacker;
		return true;
	}
	
	public void setPlayerIsAttacker(Boolean bool) {
		playerIsAttacker = bool;
	}
	
	public void setPlayerInitiated(boolean playerInitiated) {
		this.playerInitiated = playerInitiated;
	}
		
	public Boolean isPlayerFriendly(boolean isAttacker) {
		if (playerIsAttacker == null) return null;
		return (playerIsAttacker == isAttacker);
	}
	
	public int getTurnNum() {
		return turnNum;
	}
	
	public boolean isCramped() {
		Boolean cramped = (Boolean)data.get("cramped");
		return Boolean.TRUE.equals(cramped);
	}
	
	public void setInvasionIntel(InvasionIntel intel) {
		this.intel = intel;
	}
	
	public Color getHighlightColorForSide(boolean isAttacker) {
		Boolean friendly = isPlayerFriendly(isAttacker);
		if (friendly == null) return Misc.getHighlightColor();
		else if (friendly == true) return Misc.getPositiveHighlightColor();
		else return Misc.getNegativeHighlightColor();
	}
	
	public List<GroundUnit> getAllUnits() {
		List<GroundUnit> results = new ArrayList<>(attacker.units);
		results.addAll(defender.units);
		return results;
	}
	
	public boolean hasStationFleet() {
		CampaignFleetAPI station = Misc.getStationFleet(market);
		if (station == null) return false;
		
		if (station.getFleetData().getMembersListCopy().isEmpty()) return false;
		
		return true;
	}
	
	/**
	 * Can {@code faction} support the specified side in this ground battle? 
	 * @param faction
	 * @param isAttacker
	 * @return
	 */
	protected boolean canSupport(FactionAPI faction, boolean isAttacker) {		
		FactionAPI supportee = getSide(isAttacker).faction;
		if (faction.isPlayerFaction() && Misc.getCommissionFaction() == supportee)
			return true;
		if (AllianceManager.areFactionsAllied(faction.getId(), supportee.getId()))
			return true;
		
		return false;
	}
	
	public boolean fleetCanSupport(CampaignFleetAPI fleet, boolean isAttacker) 
	{
		if (isAttacker && hasStationFleet()) return false;
		if (fleet.isStationMode()) return false;
		if (fleet.isPlayerFleet()) {
			return isAttacker == playerIsAttacker;
		}
		MemoryAPI mem = fleet.getMemory();
		if (mem.contains(MemFlags.MEMORY_KEY_TRADE_FLEET)) return false;
		
		return canSupport(fleet.getFaction(), isAttacker);
	}
	
	public List<CampaignFleetAPI> getSupportingFleets(boolean isAttacker) {
		List<CampaignFleetAPI> fleets = new ArrayList<>();
		SectorEntityToken token = market.getPrimaryEntity();
		for (CampaignFleetAPI fleet : token.getContainingLocation().getFleets()) 
		{
			if (!fleetCanSupport(fleet, isAttacker))
				continue;			
			
			if (MathUtils.getDistance(fleet, token) > GBConstants.MAX_SUPPORT_DIST)
				continue;
			
			fleets.add(fleet);
		}
		
		return fleets;
	}
	
	public boolean isPlayerInRange() {
		return isFleetInRange(Global.getSector().getPlayerFleet());
	}
	
	public boolean isFleetInRange(CampaignFleetAPI fleet) {
		return MathUtils.getDistance(fleet, 
				market.getPrimaryEntity()) <= GBConstants.MAX_SUPPORT_DIST;
	}
		
	public static void applyTagWithReason(Map<String, Object> data, String tag, String reason) {
		Object param = data.get(tag);
		if (param != null && !(param instanceof Collection)) {
			log.error("Attempt to add a tag-with-reason to invalid collection: " + tag + ", " + reason);
			return;
		}
		Collection<String> reasons;
		if (param != null) 
			reasons = (Collection<String>)param;
		else
			reasons = new HashSet<>();
		
		reasons.add(reason);
		data.put(tag, reasons);
	}
	
	public static void unapplyTagWithReason(Map<String, Object> data, String tag, String reason) {
		Object param = data.get(tag);
		if (param != null && !(param instanceof Collection)) {
			log.error("Attempt to add a tag-with-reason to invalid collection: " + tag + ", " + reason);
			return;
		}
		if (param == null) return;
		
		Collection<String> reasons = (Collection<String>)param;
		reasons.remove(reason);
		if (reasons.isEmpty()) data.remove(tag);
	}
	
	public GroundUnit createPlayerUnit(ForceType type) {
		int index = 0;
		if (!playerData.getUnits().isEmpty()) {
			index = playerData.getUnits().get(playerData.getUnits().size() - 1).index + 1;
		}
		GroundUnit unit = new GroundUnit(this, type, 0, index);
		unit.faction = PlayerFactionStore.getPlayerFaction();
		unit.isPlayer = true;
		unit.isAttacker = this.playerIsAttacker;
		unit.fleet = Global.getSector().getPlayerFleet();
		
		int size = UnitOrderDialogPlugin.getMaxCountForResize(unit, 0, unitSize.avgSize);
		unit.setSize(size, true);
		
		unit.setStartingMorale();
		
		playerData.getUnits().add(unit);
		getSide(playerIsAttacker).units.add(unit);
		return unit;
	}
	
	/**
	 * Creates units for player based on available marines and heavy armaments.<br/>
	 * Attempts to create the minimum number of units that will hold 100% of 
	 * player forces of each type, then distributes marines and heavy arms equally
	 * between each.
	 */
	public void autoGeneratePlayerUnits() {
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		int marines = cargo.getMarines();
		int heavyArms = (int)cargo.getCommodityQuantity(Commodities.HAND_WEAPONS);
		
		// add heavy units
		int usableHeavyArms = Math.min(heavyArms, marines/GroundUnit.CREW_PER_MECH);
		
		float perUnitSize = (int)(unitSize.maxSize/GroundUnit.HEAVY_COUNT_DIVISOR);
		int numCreatable = (int)Math.ceil(usableHeavyArms / perUnitSize);
		numCreatable = Math.min(numCreatable, MAX_PLAYER_UNITS);
		numCreatable = (int)Math.ceil(numCreatable * 0.75f);
		int numPerUnit = 0;
		if (numCreatable > 0) numPerUnit = usableHeavyArms/numCreatable;
		numPerUnit = (int)Math.min(numPerUnit, perUnitSize);
		
		if (isCramped()) {
			log.info("Cramped conditions, skipping generation of heavy units");
			usableHeavyArms = 0;
		} else {
			log.info(String.format("Can create %s heavies, %s units each, have %s heavies", numCreatable, numPerUnit, usableHeavyArms));
			for (int i=0; i<numCreatable; i++) {
				GroundUnit unit = createPlayerUnit(ForceType.HEAVY);
				unit.setSize(numPerUnit, true);
			}
		}
		
		// add marines
		marines -= usableHeavyArms * GroundUnit.CREW_PER_MECH;
		int remainingSlots = MAX_PLAYER_UNITS - playerData.getUnits().size();
		perUnitSize = unitSize.maxSize;
		numCreatable = (int)Math.ceil(marines / perUnitSize);
		numCreatable = Math.min(numCreatable, remainingSlots);
		numPerUnit = 0;
		if (numCreatable > 0) numPerUnit = marines/numCreatable;
		numPerUnit = (int)Math.min(numPerUnit, perUnitSize);
		
		log.info(String.format("Can create %s marines, %s units each, have %s marines", numCreatable, numPerUnit, marines));
		for (int i=0; i<numCreatable; i++) {
			GroundUnit unit = createPlayerUnit(ForceType.MARINE);
			unit.setSize(numPerUnit, true);
		}
	}
	
	public void updateStability() {
		int total = 0, attacker = 0;
		for (IndustryForBattle ifb : industries) {
			total++;
			if (ifb.heldByAttacker) attacker++;
		}
		String desc = getString("stabilityDesc");
		market.getStability().addTemporaryModFlat(3, "invasion", desc, 
				-GBConstants.STABILITY_PENALTY_BASE - GBConstants.STABILITY_PENALTY_OCCUPATION * (float)attacker/total);
	}
	
	public void reapply() {
		for (GroundBattlePlugin plugin : getPlugins()) {
			plugin.unapply();
			plugin.apply();
		}
		updateStability();
	}
	
	protected int countPersonnelFromMap(Map<ForceType, Integer> map) {
		int num = 0;
		for (ForceType type : map.keySet()) {
			int thisNum = map.get(type);
			if (type == ForceType.HEAVY)
				num += thisNum * GroundUnit.CREW_PER_MECH;
			else
				num += thisNum;
		}
		return num;
	}
	
	public void runAI(boolean isAttacker, boolean isPlayer) {
		GroundBattleAI ai = new GroundBattleAI(this, isAttacker, isPlayer);
		ai.giveOrders();
	}
	
	public void runAI() {
		if (playerIsAttacker != null) {
			if (playerData.autoMoveAtEndTurn)
				runAI(playerIsAttacker, true);
			runAI(!playerIsAttacker, false);		
		} else {
			runAI(true, false);
			runAI(false, false);
		}
	}
	
	/**
	 * Post-battle XP for player units, both those moved to fleet and to local storage.
	 * @param storage
	 */
	public void addXPToDeployedUnits(SubmarketAPI storage) 
	{
		// calc the number of marines involved
		int losses = countPersonnelFromMap(playerData.getLosses());
		int inFleet = countPersonnelFromMap(playerData.getDisbanded());
		Integer inStorage = playerData.getSentToStorage().get(Commodities.MARINES);
		if (inStorage == null) inStorage = 0;
		float total = inFleet + inStorage;
		if (total == 0) return;
		
		// calc XP to apply
		float sizeFactor = (float)Math.pow(2, market.getSize());
		float xp = GBConstants.XP_MARKET_SIZE_MULT * sizeFactor;
		log.info(String.format("%s xp from market size", xp));
		float xpFromLosses = losses * GBConstants.XP_CASUALTY_MULT;
		log.info(String.format("%s xp from losses", xpFromLosses));
		xp += xpFromLosses;
		xp = Math.min(xp, total/2);
		
		// apply the XP
		// TODO: log this		
		float fleetXP = (inFleet/total) * xp;
		if (fleetXP > 0) {
			log.info("Adding " + fleetXP + " XP for " + inFleet + " marines in fleet");
			PlayerFleetPersonnelTracker.getInstance().getMarineData().addXP(fleetXP);
			
			GroundBattleLog xpLog = new GroundBattleLog(this, GroundBattleLog.TYPE_XP_GAINED);
			xpLog.params.put("xp", fleetXP);
			xpLog.params.put("marines", inFleet);
			xpLog.params.put("isStorage", false);
			addLogEvent(xpLog);
		}
		
		float storageXP = (inStorage/total) * xp;
		if (storage != null && storageXP > 0) {
			// hack to make it apply XP properly: clear existing instance
			PlayerFleetPersonnelTracker.getInstance().reportCargoScreenOpened();
			
			log.info("Adding " + storageXP + " XP for " + inStorage + " marines in storage");
			//playerData.xpTracker.data.num = storage.getCargo().getMarines();
			//playerData.xpTracker.data.addXP(storageXP);
			
			PersonnelAtEntity local = PlayerFleetPersonnelTracker.getInstance().getDroppedOffAt(
				Commodities.MARINES, market.getPrimaryEntity(), 
				market.getSubmarket(Submarkets.SUBMARKET_STORAGE), true);
			local.data.num = storage.getCargo().getMarines();
			local.data.addXP(storageXP + playerData.xpTracker.data.xp);
			
			GroundBattleLog xpLog = new GroundBattleLog(this, GroundBattleLog.TYPE_XP_GAINED);
			xpLog.params.put("xp", storageXP);
			xpLog.params.put("marines", inStorage);
			xpLog.params.put("isStorage", true);
			addLogEvent(xpLog);
		}
		
		Global.getSector().getPlayerPerson().getStats().addXP(Math.round(xp * 1000));
	}
	
	/**
	 * Breaks up player units after the battle ends.
	 */
	public void disbandPlayerUnits() {
		SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
		
		boolean anyInStorage = false;
		for (GroundUnit unit : new ArrayList<>(playerData.getUnits())) {
			// if any player units are on market, send them to storage
			if (unit.getLocation() != null) {
				if (storage != null && !ALWAYS_RETURN_TO_FLEET) {
					storage.getCargo().addCommodity(Commodities.MARINES, unit.personnel);
					storage.getCargo().addCommodity(Commodities.HAND_WEAPONS, unit.heavyArms);
					if (playerData.getLoot() != null) {
						playerData.getLoot().addCommodity(Commodities.MARINES, unit.personnel);
						playerData.getLoot().addCommodity(Commodities.HAND_WEAPONS, unit.heavyArms);
					}
					NexUtils.modifyMapEntry(playerData.getSentToStorage(), Commodities.MARINES, unit.personnel);
					NexUtils.modifyMapEntry(playerData.getSentToStorage(), Commodities.HAND_WEAPONS, unit.heavyArms);
					unit.removeUnit(false);
					anyInStorage = true;
				}
				else {
					// no storage? teleport to cargo
					unit.removeUnit(true);
				}
				
			}
			// the ones in fleet we disband directly to player cargo
			else {
				unit.removeUnit(true);
			}
		}
		if (anyInStorage) {
			StoragePlugin plugin = (StoragePlugin)market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
			if (plugin != null)
				plugin.setPlayerPaidToUnlock(true);
		}
		
		if (outcome != outcome.CANCELLED) {
			addXPToDeployedUnits(storage);
		}
	}
	
	public void handleTransfer() {
		if (outcome == BattleOutcome.ATTACKER_VICTORY ) {
			InvasionRound.conquerMarket(market, attacker.getFaction(), playerInitiated);
			market.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_PLAYER_HOSTILE_ACTIVITY_NEAR_MARKET);
			market.getMemoryWithoutUpdate().set("$tradeMode", "OPEN", 0);
			
			InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
			if (dialog == null && dialog instanceof RuleBasedDialog) {
				((RuleBasedDialog)dialog.getPlugin()).updateMemory();
				FireAll.fire(null, dialog, dialog.getPlugin().getMemoryMap(), "PopulateOptions");
			}
		}
	}
	
	public void endBattle(BattleOutcome outcome) {
		this.outcome = outcome;
		market.getStability().removeTemporaryMod("invasion");
		if (outcome == BattleOutcome.ATTACKER_VICTORY || outcome == BattleOutcome.DEFENDER_VICTORY
				|| outcome == BattleOutcome.PEACE) {
			
			recentUnrest = 1 + (turnNum/Math.max(market.getSize() - 1, 1));
			
			String origOwner = NexUtilsMarket.getOriginalOwner(market);
			if (outcome == BattleOutcome.ATTACKER_VICTORY && origOwner != null 
					&& defender.getFaction().getId().equals(origOwner))
				recentUnrest += 2;
			
			RecentUnrest.get(market, true).add(recentUnrest, String.format(getString("unrestReason"), 
					attacker.getFaction().getDisplayName()));
		}
		
		if (Boolean.TRUE.equals(playerIsAttacker) && outcome == BattleOutcome.ATTACKER_VICTORY) 
		{
			playerData.setLoot(GroundBattleRoundResolve.lootMarket(market));
		}
		if (playerInitiated && outcome == BattleOutcome.ATTACKER_VICTORY && Misc.getCommissionFaction() != null) 
		{
			timerForDecision = 7f;
			market.getMemoryWithoutUpdate().set(GBConstants.MEMKEY_AWAIT_DECISION, true, timerForDecision);
		}
		
		if (outcome == BattleOutcome.ATTACKER_VICTORY) {
			// reset to 25% health?
			GBUtils.setGarrisonDamageMemory(market, 0.75f);
		}
		else if (outcome != BattleOutcome.DESTROYED) {
			float currStrength = defender.getBaseStrength();
			float strRatio = currStrength/defender.currNormalBaseStrength;
			GBUtils.setGarrisonDamageMemory(market, 1 - strRatio);
		}
		
		for (IndustryForBattle ifb : industries) {
			if (!ifb.isIndustryTrueDisrupted())
				ifb.ind.setDisrupted(0);
		}
		
		disbandPlayerUnits();
		
		handleTransfer();
		
		responseScript.forceDone();
		
		GroundBattleLog log = new GroundBattleLog(this, GroundBattleLog.TYPE_BATTLE_END, turnNum);
		if (outcome == BattleOutcome.ATTACKER_VICTORY)
			log.params.put("attackerIsWinner", true);
		else if (outcome == BattleOutcome.DEFENDER_VICTORY)
			log.params.put("attackerIsWinner", false);
		addLogEvent(log);
		
		if (playerIsAttacker != null) {
			log = new GroundBattleLog(this, GroundBattleLog.TYPE_LOSS_REPORT, turnNum);
			log.params.put("marinesLost", countPersonnelFromMap(playerData.getLosses()));
			log.params.put("heavyArmsLost", playerData.getLosses().get(ForceType.HEAVY));
			addLogEvent(log);
		}
		
		sendUpdateIfPlayerHasIntel(UPDATE_VICTORY, false);

		for (GroundBattleCampaignListener x : Global.getSector().getListenerManager().getListeners(GroundBattleCampaignListener.class)) 
		{
			x.reportBattleEnded(this);
		}
		
		endAfterDelay();
	}
	
	public boolean hasAnyDeployedUnits(boolean attacker) {
		for (GroundUnit unit : getSide(attacker).getUnits()) {
			if (unit.getLocation() != null)
				return true;
		}
		return false;
	}
	
	public void checkAnyAttackers() {
		// any units on ground?
		if (hasAnyDeployedUnits(true)) return;
		// guess not, end the battle
		endBattle(turnNum <= 1 ? BattleOutcome.CANCELLED : BattleOutcome.DEFENDER_VICTORY);
	}
	
	public void checkForVictory() {
		if (!hasAnyDeployedUnits(false)) {
			endBattle(BattleOutcome.ATTACKER_VICTORY);
		}
	}
	
	public void advanceTurn(boolean force) {
		if (force) {
			doShortIntervalStuff(interval.getIntervalDuration() - interval.getElapsed());
		}
		movedFromLastTurn.clear();
		
		reapply();
		checkAnyAttackers();
		runAI();
		for (GroundBattleCampaignListener x : Global.getSector().getListenerManager().getListeners(GroundBattleCampaignListener.class)) 
		{
			x.reportBattleBeforeTurn(this, turnNum);
		}
		new GroundBattleRoundResolve(this).resolveRound();
		for (GroundBattleCampaignListener x : Global.getSector().getListenerManager().getListeners(GroundBattleCampaignListener.class)) 
		{
			x.reportBattleAfterTurn(this, turnNum);
		}
		checkForVictory();
		playerData.updateXPTrackerNum();
		
		if (outcome != null) {
			return;
		}
		
		if (playerIsAttacker != null)
			sendUpdateIfPlayerHasIntel(UPDATE_TURN, false);
		interval.setElapsed(0);
		turnNum++;
		reapply();
		attacker.reportTurn();
		defender.reportTurn();
		abilitiesUsedLastTurn.clear();
	}
	
	/**
	 * Was this market originally owned by the player?
	 * @return
	 */
	protected boolean wasPlayerMarket() {
		String origOwner = NexUtilsMarket.getOriginalOwner(market);
		boolean originallyPlayer = origOwner == null || origOwner.equals(Factions.PLAYER);
		return originallyPlayer;
	}
	
	/**
	 * If the player decides to keep the market for themselves rather than 
	 * transferring it to commissioning faction.
	 */
	public void handleAndradaOption() {
		if (attacker.getFaction().isPlayerFaction()) return;
		SectorManager.transferMarket(market, Global.getSector().getPlayerFaction(), 
				market.getFaction(), true, false, new ArrayList<String>(), 0, true);
		
		if (!wasPlayerMarket()) {
			CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
			impact.delta = -0.05f * market.getSize();
			//impact.ensureAtBest = RepLevel.SUSPICIOUS;
			impact.limit = RepLevel.INHOSPITABLE;
			ReputationAdjustmentResult result = Global.getSector().adjustPlayerReputation(
					new CoreReputationPlugin.RepActionEnvelope(
					CoreReputationPlugin.RepActions.CUSTOM, impact, null, null, true), 
					PlayerFactionStore.getPlayerFactionId());
			playerData.andradaRepChange = result;
			playerData.andradaRepAfter = Global.getSector().getPlayerFaction().getRelationship(PlayerFactionStore.getPlayerFactionId());
		}
	}
	
	public void handleGovernorshipPurchase() {
		MutableStat cost = Nex_BuyColony.getValue(market, false, true);
		int curr = (int)Global.getSector().getPlayerFleet().getCargo().getCredits().get();
		if (curr > cost.getModifiedValue()) {
			Nex_BuyColony.buy(market, null);
			playerData.governorshipPrice = cost.getModifiedValue();
		}
	}
	
	/**
	 * Briefly, periodically disrupt all industries occupied by attacker.
	 */
	public void disruptIndustries() {
		for (IndustryForBattle ifb : industries) {
			if (ifb.heldByAttacker) {
				float currDisruptionTime = ifb.getIndustry().getDisruptedDays();
				if (currDisruptionTime < GBConstants.DISRUPT_WHEN_CAPTURED_TIME)
					ifb.getIndustry().setDisrupted(GBConstants.DISRUPT_WHEN_CAPTURED_TIME);
			}
		}
	}
	
	protected void addMilitaryResponse() {
		if (!market.getFaction().getCustomBoolean(Factions.CUSTOM_NO_WAR_SIM)) {
			MilitaryResponseScript.MilitaryResponseParams params = new MilitaryResponseScript.MilitaryResponseParams(CampaignFleetAIAPI.ActionType.HOSTILE, 
					"nex_player_invasion_" + market.getId(), 
					market.getFaction(),
					market.getPrimaryEntity(),
					0.75f,
					900);
			params.actionText = getString("responseStr");
			params.travelText = getString("responseTravelStr");
			responseScript = new GBMilitaryResponseScript(params);
			market.getContainingLocation().addScript(responseScript);
		}
		List<CampaignFleetAPI> fleets = market.getContainingLocation().getFleets();
		for (CampaignFleetAPI other : fleets) {
			if (other.getFaction() == market.getFaction()) {
				MemoryAPI mem = other.getMemoryWithoutUpdate();
				Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF, "raidAlarm", true, 1f);
			}
		}
	}
	
	public void reportExternalBombardment(IndustryForBattle ifb) {
		if (ifb == null) return;
		InteractionDialogAPI dialog = null;	//Global.getSector().getCampaignUI().getCurrentInteractionDialog();
		for (GroundUnit unit : ifb.units) {
			unit.inflictAttrition(GBConstants.EXTERNAL_BOMBARDMENT_DAMAGE, null, dialog);
			unit.reorganize(1);
			unit.preventAttack(1);
		}
		reapply();
	}
	
	public void loot(IndustryForBattle ifb) {
		String aiCore = ifb.getIndustry().getAICoreId();
		SpecialItemData special = ifb.getIndustry().getSpecialItem();
		if (aiCore != null) {
			Global.getSector().getPlayerFleet().getCargo().addCommodity(aiCore, 1);
			ifb.getIndustry().setAICoreId(null);
		}
		if (special != null) {
			Global.getSector().getPlayerFleet().getCargo().addSpecial(special, 1);
			ifb.getIndustry().setSpecialItem(null);
		}		
		
		Global.getSoundPlayer().playUISound("ui_cargo_special_military_drop", 1, 1);
	}
	
	public void reportAbilityUsed(AbilityPlugin ability, GroundBattleSide side, PersonAPI person) 
	{
		if (person.isPlayer()) return;
		Pair<Boolean, AbilityPlugin> entry = new Pair<>(side.isAttacker(), ability);
		abilitiesUsedLastTurn.add(entry);
	}
	
	public void doShortIntervalStuff(float days) {
		disruptIndustries();
		for (GroundBattlePlugin plugin : getPlugins()) {
			plugin.advance(days);
		}
	}
	
	// =========================================================================
	// callins
	
	@Override
	public void advance(float amount) {
		super.advance(amount);
		// needs to be in advance rather than advanceImpl so it runs after event ends
		float days = Global.getSector().getClock().convertToDays(amount);
		if (timerForDecision != null) {
			//log.info(String.format("Timer for decision: curr %s, subtracting %s", timerForDecision, days));
			timerForDecision -= days;
			if (timerForDecision <= 0) {
				timerForDecision = null;
			}
		}
	}
	
	@Override
	protected void advanceImpl(float amount) {		
		if (outcome != null) {
			return;
		}
		
		if (!market.isInEconomy()) {
			endBattle(BattleOutcome.DESTROYED);
			return;
		}
		if (!attacker.getFaction().isHostileTo(defender.getFaction())) {
			endBattle(BattleOutcome.PEACE);
			return;
		}
		
		float days = Global.getSector().getClock().convertToDays(amount);
		intervalShort.advance(days);
		if (intervalShort.intervalElapsed()) {
			doShortIntervalStuff(intervalShort.getElapsed());
		}
		
		interval.advance(days);
		if (interval.intervalElapsed()) {
			advanceTurn(false);
		}
	}

	@Override
	public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData, CargoAPI cargo) {}

	@Override
	public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, 
			MarketCMD.TempData actionData, Industry industry) {}

	@Override
	public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData) {
		List<Industry> industries = actionData.bombardmentTargets;
		List<String> indNames = new ArrayList<>();
		for (Industry industry : industries) {
			reportExternalBombardment(getIndustryForBattleByIndustry(industry));
			indNames.add(industry.getCurrentName());
		}
		
		GroundBattleLog log = new GroundBattleLog(this, GroundBattleLog.TYPE_EXTERNAL_BOMBARDMENT, turnNum);
		log.params.put("isSaturation", false);
		log.params.put("industries", indNames);
		addLogEvent(log);
	}

	@Override
	public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData) {
		List<Industry> industries = actionData.bombardmentTargets;
		for (Industry industry : industries) {
			reportExternalBombardment(getIndustryForBattleByIndustry(industry));
		}
		GroundBattleLog log = new GroundBattleLog(this, GroundBattleLog.TYPE_EXTERNAL_BOMBARDMENT, turnNum);
		log.params.put("isSaturation", true);
		addLogEvent(log);
	}
	
	@Override
	public void reportNPCGenericRaid(MarketAPI market, MarketCMD.TempData actionData) {}

	@Override
	public void reportNPCIndustryRaid(MarketAPI market, MarketCMD.TempData actionData, Industry industry) {}

	@Override
	public void reportNPCTacticalBombardment(MarketAPI market, MarketCMD.TempData actionData) 
	{
		reportTacticalBombardmentFinished(null, market, actionData);
	}

	@Override
	public void reportNPCSaturationBombardment(MarketAPI market, MarketCMD.TempData actionData) 
	{
		reportSaturationBombardmentFinished(null, market, actionData);
	}
	
	// =========================================================================
	// GUI stuff 
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
								   Color tc, float initPad) {
		info.addPara(Misc.ucFirst(attacker.faction.getDisplayName()), attacker.faction.getBaseUIColor(), 3);
		info.addPara(Misc.ucFirst(defender.faction.getDisplayName()), defender.faction.getBaseUIColor(), 0);
		
		if (listInfoParam == UPDATE_TURN) {
			info.addPara(getString("intelDesc_round"), 0, Misc.getHighlightColor(), turnNum + "");
			writeTurnBullets(info);
		}
		
		if (outcome != null) {
			String id = "outcome";
			switch (outcome) {
				case ATTACKER_VICTORY:
					id += "AttackerVictory";
					break;
				case DEFENDER_VICTORY:
					id += "DefenderVictory";
					break;
				case PEACE:
					id += "Peace";
					break;
				case DESTROYED:
					id += "Destroyed";
					break;
				case CANCELLED:
					id += "Cancelled";
					break;
				default:
					id += "Other";
					break;
			}
			info.addPara(getString(id), getBulletColorForMode(mode), 0);
		}
	}
	
	public Map<String, String> getFactionSubs() {
		Map<String, String> sub = new HashMap<>();
		sub.put("$attacker", attacker.faction.getDisplayName());
		sub.put("$theAttacker", attacker.faction.getDisplayNameWithArticle());
		sub.put("$defender", defender.faction.getDisplayNameWithArticle());
		sub.put("$theDefender", defender.faction.getDisplayNameWithArticle());
		sub.put("$market", market.getName());
		sub.put("$location", market.getContainingLocation().getNameWithLowercaseType());
		return sub;
	}
	
	public TooltipMakerAPI generateViewModeButton(CustomPanelAPI buttonRow, String nameId, ViewMode mode,
			Color base, Color bg, Color bright, TooltipMakerAPI rightOf) 
	{
		TooltipMakerAPI holder = buttonRow.createUIElement(VIEW_BUTTON_WIDTH, 
				VIEW_BUTTON_HEIGHT, false);
		
		ButtonAPI button = holder.addAreaCheckbox(getString(nameId), mode, base, bg, bright,
				VIEW_BUTTON_WIDTH, VIEW_BUTTON_HEIGHT, 0);
		button.setChecked(mode == this.viewMode);
		
		if (rightOf != null) {
			buttonRow.addUIElement(holder).rightOfTop(rightOf, 4);
		} else {
			buttonRow.addUIElement(holder).inTL(0, 3);
		}
		
		return holder;
	}
	
	public void generateIntro(CustomPanelAPI outer, TooltipMakerAPI info, float width, float pad) {
		info.addImages(width, 128, pad, pad, attacker.faction.getLogo(), defender.faction.getLogo());
				
		FactionAPI fc = getFactionForUIColors();
		Color base = fc.getBaseUIColor(), bg = fc.getDarkUIColor(), bright = fc.getBrightUIColor();
		
		String str = getString("intelDesc_intro");
		Map<String, String> sub = getFactionSubs();
		
		str = StringHelper.substituteTokens(str, sub);
		
		LabelAPI label = info.addPara(str, pad);
		label.setHighlight(attacker.faction.getDisplayNameWithArticleWithoutArticle(),
				market.getName(),
				defender.faction.getDisplayNameWithArticleWithoutArticle());
		label.setHighlightColors(attacker.faction.getBaseUIColor(),
				Misc.getHighlightColor(),
				defender.faction.getBaseUIColor());
		
		str = getString("intelDesc_unitSize");
		info.addPara(str, pad, Misc.getHighlightColor(), Misc.ucFirst(unitSize.getName()), 
				unitSize.avgSize + "", unitSize.maxSize + "", String.format("%.2f", unitSize.damMult) + "×");
		
		str = getString("intelDesc_round");
		info.addPara(str, pad, Misc.getHighlightColor(), turnNum + "");
		
		if (ExerelinModPlugin.isNexDev) {
			CustomPanelAPI buttonDebugRow = outer.createCustomPanel(width, 24, null);
			
			TooltipMakerAPI btnHolder1 = buttonDebugRow.createUIElement(160, 
				VIEW_BUTTON_HEIGHT, false);
			btnHolder1.addButton(getString("btnResolveRound"), BUTTON_RESOLVE, base, bg, 160, 24, 0);
			buttonDebugRow.addUIElement(btnHolder1).inTL(0, 0);
			
			TooltipMakerAPI btnHolder2 = buttonDebugRow.createUIElement(160, 
				VIEW_BUTTON_HEIGHT, false);
			btnHolder2.addButton(getString("btnAIDebug"), BUTTON_DEBUG_AI, base, bg, 160, 24, 0);
			buttonDebugRow.addUIElement(btnHolder2).rightOfTop(btnHolder1, 4);
			
			info.addCustom(buttonDebugRow, 3);
		}
		
		// view mode buttons
		CustomPanelAPI buttonRow = outer.createCustomPanel(width, 24, null);
		
		TooltipMakerAPI btnHolder1 = generateViewModeButton(buttonRow, "btnViewUnits", 
				ViewMode.UNITS, base, bg, bright, null);		
		TooltipMakerAPI btnHolder2 = generateViewModeButton(buttonRow, "btnViewAbilities", 
				ViewMode.ABILITIES, base, bg, bright, btnHolder1);		
		TooltipMakerAPI btnHolder3 = generateViewModeButton(buttonRow, "btnViewInfo", 
				ViewMode.INFO, base, bg, bright, btnHolder2);		
		TooltipMakerAPI btnHolder4 = generateViewModeButton(buttonRow, "btnViewLog", 
				ViewMode.LOG, base, bg, bright, btnHolder3);
		TooltipMakerAPI btnHolder5 = generateViewModeButton(buttonRow, "btnViewHelp", 
				ViewMode.HELP, base, bg, bright, btnHolder4);
		
		info.addCustom(buttonRow, 3);
	}
	
	protected String getCommoditySprite(String commodityId) {
		return Global.getSettings().getCommoditySpec(commodityId).getIconName();
	}
	
	public TooltipMakerAPI addResourceSubpanel(CustomPanelAPI resourcePanel, float width, 
			TooltipMakerAPI rightOf, String commodity, int amount) 
	{
		TooltipMakerAPI subpanel = resourcePanel.createUIElement(width, 32, false);
		TooltipMakerAPI image = subpanel.beginImageWithText(getCommoditySprite(commodity), 32);
		image.addPara(amount + "", 0);
		subpanel.addImageWithText(0);
		if (rightOf == null)
			resourcePanel.addUIElement(subpanel).inTL(0, 0);
		else
			resourcePanel.addUIElement(subpanel).rightOfTop(rightOf, 0);
		
		return subpanel;
	}
	
	public void placeElementInRows(CustomPanelAPI element, int numPrevious, 
			CustomPanelAPI holder, List<CustomPanelAPI> elements,int maxPerRow) {
		if (numPrevious == 0) {
			// first card, place in TL
			holder.addComponent(element).inTL(0, 3);
			//log.info("Placing card in TL");
		}
		else if (numPrevious % maxPerRow == 0) {
			// row filled, place under first card of previous row
			int rowNum = numPrevious/maxPerRow - 1;
			CustomPanelAPI firstOfPrevious = elements.get(maxPerRow * rowNum);
			holder.addComponent(element).belowLeft(firstOfPrevious, 3);
			//log.info("Placing card in new row");
		}
		else {
			// right of last card
			holder.addComponent(element).rightOfTop(elements.get(numPrevious - 1), GroundUnit.PADDING_X);
			//log.info("Placing card in current row");
		}
	}
	
	public void generateUnitDisplay(TooltipMakerAPI info, CustomPanelAPI outer, float width, float opad) 
	{
		float pad = 3;
		info.addSectionHeading(getString("unitPanel_header"), Alignment.MID, opad);
		
		// movement points display
		TooltipMakerAPI movementPoints = info.beginImageWithText(Global.getSettings().
				getCommoditySpec(Commodities.SUPPLIES).getIconName(), 24);
		int maxPoints = getSide(playerIsAttacker).getMovementPointsPerTurn().getModifiedInt();
		int available = maxPoints - getSide(playerIsAttacker).getMovementPointsSpent().getModifiedInt();
		Color h = Misc.getHighlightColor();
		if (available <= 0) h = Misc.getNegativeHighlightColor();
		else if (available >= maxPoints) h = Misc.getPositiveHighlightColor();
		
		String str = getString("unitPanel_movementPoints") + ": %s / %s";
		movementPoints.addPara(str, pad, h, available + "", maxPoints + "");
		
		info.addImageWithText(pad);
		info.addTooltipToPrevious(new TooltipCreator() {
			@Override
			public boolean isTooltipExpandable(Object tooltipParam) {
					return false;
				}
				public float getTooltipWidth(Object tooltipParam) {
					return 400;	// FIXME magic number
				}
				public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
					Color h = Misc.getHighlightColor();
		
					String str = getString("unitPanel_movementPoints_tooltip1");
					tooltip.addPara(str, 0);
					
					str = getString("unitPanel_movementPoints_tooltip2");
					tooltip.addPara(str, 10);
					
					str = getString("unitPanel_movementPoints_tooltip3");
					tooltip.addPara(str, 10);

					tooltip.addStatModGrid(360, 60, 10, 3, getSide(playerIsAttacker).getMovementPointsPerTurn(), 
							true, NexUtils.getStatModValueGetter(true, 0));
							
				}			
		}, TooltipMakerAPI.TooltipLocation.BELOW);
		
		// cargo display
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		info.addPara(getString("unitPanel_resources"), pad);
		
		CustomPanelAPI resourcePanel = outer.createCustomPanel(width, 32, null);
		
		TooltipMakerAPI resourceSubPanel;
		int subWidth = 96;
		resourceSubPanel = addResourceSubpanel(resourcePanel, subWidth, null, 
				Commodities.MARINES, cargo.getMarines());
		resourceSubPanel = addResourceSubpanel(resourcePanel, subWidth, resourceSubPanel, 
				Commodities.HAND_WEAPONS, (int)cargo.getCommodityQuantity(Commodities.HAND_WEAPONS));
		resourceSubPanel = addResourceSubpanel(resourcePanel, subWidth, resourceSubPanel, 
				Commodities.SUPPLIES, (int)cargo.getSupplies());
		resourceSubPanel = addResourceSubpanel(resourcePanel, subWidth, resourceSubPanel, 
				Commodities.FUEL, (int)cargo.getFuel());
		
		info.addCustom(resourcePanel, pad);
		
		// player AI buttons
		FactionAPI fc = getFactionForUIColors();
		Color base = fc.getBaseUIColor(), bg = fc.getDarkUIColor();
		
		CustomPanelAPI buttonRow = outer.createCustomPanel(width, 24, null);
		TooltipMakerAPI btnHolder1 = buttonRow.createUIElement(160, 
				VIEW_BUTTON_HEIGHT, false);
		btnHolder1.addButton(getString("btnRunPlayerAI"), BUTTON_AUTO_MOVE,	base,
				bg, 160, VIEW_BUTTON_HEIGHT, 0);
		String tooltipStr = getString("btnRunPlayerAI_tooltip");
		TooltipCreator tt = NexUtils.createSimpleTextTooltip(tooltipStr, 360);
		btnHolder1.addTooltipToPrevious(tt, TooltipMakerAPI.TooltipLocation.BELOW);
		buttonRow.addUIElement(btnHolder1).inTL(0, 3);
		
		TooltipMakerAPI btnHolder2 = buttonRow.createUIElement(240, 
				VIEW_BUTTON_HEIGHT, false);
		ButtonAPI check = btnHolder2.addAreaCheckbox(getString("btnTogglePlayerAI"), BUTTON_AUTO_MOVE_TOGGLE, 
				base, bg, fc.getBrightUIColor(),
				240, VIEW_BUTTON_HEIGHT, 0);
		check.setChecked(playerData.autoMoveAtEndTurn);
		btnHolder2.addTooltipToPrevious(tt, TooltipMakerAPI.TooltipLocation.BELOW);
		buttonRow.addUIElement(btnHolder2).rightOfTop(btnHolder1, 4);		
		
		info.addCustom(buttonRow, 0);
		
		// unit cards
		int CARDS_PER_ROW = (int)(width/(GroundUnit.PANEL_WIDTH + GroundUnit.PADDING_X));
		List<GroundUnit> listToRead = playerData.getUnits();	// units whose cards should be shown
		if (Global.getSettings().isDevMode()) {
			listToRead = getAllUnits();
		}
		int numCards = listToRead.size();
		if (listToRead.size() < MAX_PLAYER_UNITS)
			numCards++;	// for the "create unit" card
		
		int NUM_ROWS = (int)Math.ceil((float)numCards/CARDS_PER_ROW);
		//log.info("Number of rows: " + NUM_ROWS);
		//log.info("Cards per row: " + CARDS_PER_ROW);
		
		CustomPanelAPI unitPanel = outer.createCustomPanel(width, NUM_ROWS * (GroundUnit.PANEL_HEIGHT + pad), null);
		
		//TooltipMakerAPI test = unitPanel.createUIElement(64, 64, true);
		//test.addPara("wololo", 0);
		//unitPanel.addUIElement(test).inTL(0, 0);
		
		List<CustomPanelAPI> unitCards = new ArrayList<>();
		try {
			for (GroundUnit unit : listToRead) {
				CustomPanelAPI unitCard = unit.createUnitCard(unitPanel, false);
				//log.info("Created card for " + unit.name);
				
				int numPrevious = unitCards.size();
				placeElementInRows(unitCard, numPrevious, unitPanel, unitCards, CARDS_PER_ROW);
				unitCards.add(unitCard);
			}
			if (listToRead.size() < MAX_PLAYER_UNITS) {
				CustomPanelAPI newCard = GroundUnit.createBlankCard(unitPanel, unitSize);
				placeElementInRows(newCard, unitCards.size(), unitPanel, unitCards, CARDS_PER_ROW);
			}
			
		} catch (Exception ex) {
			log.error("Failed to display unit cards", ex);
		}
				
		info.addCustom(unitPanel, pad);
	}
	
	public void populateModifiersDisplay(CustomPanelAPI outer, TooltipMakerAPI disp, 
			float width, float pad, Boolean isAttacker) 
	{
		for (GroundBattlePlugin plugin : getPlugins()) {
			plugin.addModifierEntry(disp, outer, width, pad, isAttacker);
		}
	}
	
	public void generateModifiersDisplay(TooltipMakerAPI info, CustomPanelAPI panel, float width, float pad) 
	{		
		// Holds the display for each faction, added to 'info'
		CustomPanelAPI strPanel = panel.createCustomPanel(width, MODIFIER_PANEL_HEIGHT, null);
		
		float subWidth = width/3;
		try {
			TooltipMakerAPI dispAtk = strPanel.createUIElement(subWidth, MODIFIER_PANEL_HEIGHT, true);
			strPanel.addUIElement(dispAtk).inTL(0, 0);
			TooltipMakerAPI dispCom = strPanel.createUIElement(subWidth, MODIFIER_PANEL_HEIGHT, true);
			strPanel.addUIElement(dispCom).inTMid(0);
			TooltipMakerAPI dispDef = strPanel.createUIElement(subWidth, MODIFIER_PANEL_HEIGHT, true);
			strPanel.addUIElement(dispDef).inTR(0, 0);

			FactionAPI neutral = Global.getSector().getFaction(Factions.NEUTRAL);

			dispAtk.addSectionHeading(getString("intelDesc_headerAttackerMod"), 
					attacker.faction.getBaseUIColor(), attacker.faction.getDarkUIColor(), Alignment.MID, pad);
			dispCom.addSectionHeading(getString("intelDesc_headerCommonMod"), 
					neutral.getBaseUIColor(), neutral.getDarkUIColor(), Alignment.MID, pad);
			dispDef.addSectionHeading(getString("intelDesc_headerDefenderMod"),
					defender.faction.getBaseUIColor(), defender.faction.getDarkUIColor(), Alignment.MID, pad);
			
		
			populateModifiersDisplay(strPanel, dispAtk, subWidth, 3, true);
			populateModifiersDisplay(strPanel, dispCom, subWidth, 3, null);
			populateModifiersDisplay(strPanel, dispDef, subWidth, 3, false);
		} catch (Exception ex) {
			log.error("Failed to display modifiers", ex);
		}
		
		info.addCustom(strPanel, pad);
	}
	
	/**
	 * Draws the subpanel with the player abilities.
	 * @param info
	 * @param outer
	 * @param width
	 * @param pad
	 */
	public void generateAbilityDisplay(TooltipMakerAPI info, CustomPanelAPI outer, float width, float pad) 
	{
		if (playerIsAttacker == null) return;
		
		float opad = 10;
		FactionAPI fc = getFactionForUIColors();
		Color base = fc.getBaseUIColor(), bg = fc.getDarkUIColor();
		info.addSectionHeading(getString("commandPanel_header1"), base, bg, Alignment.MID, opad);
				
		// abilities
		int CARDS_PER_ROW = (int)(width/(AbilityPlugin.PANEL_WIDTH + GroundUnit.PADDING_X));
		List<AbilityPlugin> abilities = getSide(playerIsAttacker).abilities;
		int numCards = abilities.size();
		
		int NUM_ROWS = (int)Math.ceil((float)numCards/CARDS_PER_ROW);
		CustomPanelAPI abilityPanel = outer.createCustomPanel(width, NUM_ROWS * AbilityPlugin.PANEL_HEIGHT, null);
				
		List<CustomPanelAPI> abilityCards = new ArrayList<>();
		try {
			for (AbilityPlugin plugin : abilities) {
				CustomPanelAPI abilityCard = plugin.createAbilityCard(abilityPanel);
				//log.info("Created card for " + unit.name);
				
				int numPrevious = abilityCards.size();
				placeElementInRows(abilityCard, numPrevious, abilityPanel, abilityCards, CARDS_PER_ROW);
				abilityCards.add(abilityCard);
			}			
		} catch (Exception ex) {
			log.error("Failed to display ability cards", ex);
		}
		info.addCustom(abilityPanel, pad);
	}
	
	/**
	 * Draws the subpanel with the list of industries and the forces on them.
	 * @param info
	 * @param panel
	 * @param width
	 */
	public void generateIndustryDisplay(TooltipMakerAPI info, CustomPanelAPI panel, float width) 
	{
		try {
			if (Global.getSettings().getBoolean("nex_useGroundBattleIndustryMap")) {
				// render map
				MarketMapDrawer map = new MarketMapDrawer(this, panel, width - 12);
				map.init();
				CustomPanelAPI mapPanel = map.getPanel();
				info.addCustom(mapPanel, 10);
			}
			else {
				info.beginTable(Global.getSector().getPlayerFaction(), 0,
						getString("industryPanel_header_industry"), IndustryForBattle.COLUMN_WIDTH_INDUSTRY,
						//getString("industryPanel_header_heldBy"), IndustryForBattle.COLUMN_WIDTH_CONTROLLED_BY,
						getString("industryPanel_header_attacker"), IndustryForBattle.COLUMN_WIDTH_TROOP_TOTAL,
						getString("industryPanel_header_defender"), IndustryForBattle.COLUMN_WIDTH_TROOP_TOTAL
				);
				info.addTable("", 0, 10);
				info.addSpacer(4);

				for (IndustryForBattle ifb : industries) {
					ifb.renderPanel(panel, info, width);
				}
			}
		} catch (Exception ex) {
			log.info("Failed to generate industry display", ex);
		}
	}
	
	public void generateLogDisplay(TooltipMakerAPI info, CustomPanelAPI outer, float width) 
	{
		info.addSectionHeading(getString("logHeader"), Alignment.MID, 10);
		try {
			float logPanelHeight = 240;
			//if (this.outcome != null) // endgame display
			//	logPanelHeight = 600;
			CustomPanelAPI logPanel = outer.createCustomPanel(width, logPanelHeight, null);
			TooltipMakerAPI scroll = logPanel.createUIElement(width, logPanelHeight, true);
			for (int i=battleLog.size() - 1; i>=0; i--) {
				battleLog.get(i).writeLog(logPanel, scroll, width - 4);
			}

			logPanel.addUIElement(scroll);
			info.addCustom(logPanel, 3);
		} catch (Exception ex) {
			log.error("Failed to create log display", ex);
		}		
	}
	
	protected void generateHelpDisplay(TooltipMakerAPI info, CustomPanelAPI outer, float width)
	{
		float opad = 10;
		float pad = 3;
		Color h = Misc.getHighlightColor();
		String bullet = " - ";
		
		info.addSectionHeading(getString("helpHeader"), Alignment.MID, opad);
		
		info.setParaInsigniaLarge();
		info.addPara(getString("helpPara1Title"), opad);
		info.setParaFontDefault();
		TooltipMakerAPI section = info.beginImageWithText("graphics/exerelin/icons/intel/invasion.png", 32);
		section.setBulletedListMode(bullet);
		section.addPara(getString("helpPara1-1"), pad);
		section.addPara(getString("helpPara1-2"), pad);
		section.addPara(getString("helpPara1-3"), pad);
		section.addPara(getString("helpPara1-4"), pad);
		info.addImageWithText(pad);
		
		CustomPanelAPI help2Holder = outer.createCustomPanel(width, 123, null);
		TooltipMakerAPI help2Text = help2Holder.createUIElement(500, 123, false);
		help2Text.setParaInsigniaLarge();
		help2Text.addPara(getString("helpPara2Title"), 0);
		help2Text.setParaFontDefault();
		section = help2Text.beginImageWithText("graphics/icons/cargo/supplies.png", 32);
		section.setBulletedListMode(bullet);
		section.addPara(getString("helpPara2-1"), pad);
		section.addPara(getString("helpPara2-2"), pad);
		section.addPara(getString("helpPara2-3"), pad);
		help2Text.addImageWithText(pad);
		help2Holder.addUIElement(help2Text).inTL(0, 0);
		TooltipMakerAPI help2Img = help2Holder.createUIElement(223, 123, false);
		help2Img.addImage(Global.getSettings().getSpriteName("nex_groundbattle", "help_unitCard"), pad * 2);
		help2Holder.addUIElement(help2Img).rightOfTop(help2Text, 8);
		info.addCustom(help2Holder, opad);
		
		info.setParaInsigniaLarge();
		info.addPara(getString("helpPara3Title"), opad);
		info.setParaFontDefault();
		
		for (int i=1; i<=3; i++) {
			ForceType type = ForceType.values()[i - 1];
			info.setBulletedListMode(bullet);
			section = info.beginImageWithText(Global.getSettings().getCommoditySpec(type.commodityId).getIconName(), 32);
			String name = Misc.ucFirst(type.getName());
			section.addPara(getString("helpPara3-" + i), pad, h, name);
			info.addImageWithText(0);
		}
		unindent(info);
		
		info.setParaInsigniaLarge();
		info.addPara(getString("helpPara4Title"), opad);
		info.setParaFontDefault();
		section = info.beginImageWithText("graphics/exerelin/icons/intel/swiss_flag.png", 32);
		section.setBulletedListMode(bullet);
		section.addPara(getString("helpPara4-1"), pad);
		section.addPara(getString("helpPara4-2"), pad);
		section.addPara(getString("helpPara4-3"), pad);
		info.addImageWithText(pad);
		unindent(info);
		
		info.setParaInsigniaLarge();
		info.addPara(getString("helpPara5Title"), opad);
		info.setParaFontDefault();
		section = info.beginImageWithText("graphics/icons/skills/leadership.png", 32);
		section.setBulletedListMode(bullet);
		section.addPara(getString("helpPara5-1"), pad);
		section.addPara(getString("helpPara5-2"), pad);
		info.addImageWithText(pad);
		unindent(info);
	}
	
	public void addLogEvent(GroundBattleLog log) {
		battleLog.add(log);
	}
	
	public void writeTurnBullets(TooltipMakerAPI info) {
		List<Pair<ForceType, Integer>> lossesSortable = new ArrayList<>();
		
		if (!playerData.getLossesLastTurn().isEmpty()) {
			String str = getString("bulletLossesLastTurn");
			for (ForceType type : playerData.getLossesLastTurn().keySet()) {
				int thisLoss = playerData.getLossesLastTurn().get(type);
				lossesSortable.add(new Pair<>(type, thisLoss));
			}
			Collections.sort(lossesSortable, new Comparator<Pair<ForceType, Integer>>() {
				@Override
				public int compare(Pair<ForceType, Integer> obj1, Pair<ForceType, Integer> obj2) {
					return obj1.one.compareTo(obj2.one);
				}
			});

			List<String> strings = new ArrayList<>();
			for (Pair<ForceType, Integer> loss : lossesSortable) {
				strings.add(loss.two + " " + loss.one.getCommodityName().toLowerCase());
			}
			str = String.format(str, StringHelper.writeStringCollection(strings, false, true));
			info.addPara(str, 0);
		}
		if (!abilitiesUsedLastTurn.isEmpty()) {
			for (Pair<Boolean, AbilityPlugin> entry : abilitiesUsedLastTurn) {
				String str = getString("bulletAbilityUsed");
				String user = StringHelper.getString(entry.one ? "attacker" : "defender", true);
				String abilityName = entry.two.getDef().name;
				
				LabelAPI label = info.addPara(str, 0, Misc.getHighlightColor(), user, abilityName);
				label.setHighlightColors(Misc.getHighlightColor(), entry.two.getDef().color);
			}
		}
	}
	
	public void addPostVictoryButtons(CustomPanelAPI outer, TooltipMakerAPI info, float width) 
	{
		float pad = 3, opad = 10;
		String str = StringHelper.substituteToken(getString("intelDesc_postVictoryOptions"), "$market", market.getName());
		info.addPara(str, opad);
		
		CustomPanelAPI buttonRow = outer.createCustomPanel(width, 24, null);
		TooltipMakerAPI btnHolder1 = buttonRow.createUIElement(VIEW_BUTTON_WIDTH * 2, 
				VIEW_BUTTON_HEIGHT, false);
		str = StringHelper.substituteToken(getString("btnAndrada"), "$market", market.getName());
		btnHolder1.addButton(str, BUTTON_ANDRADA, VIEW_BUTTON_WIDTH * 2, VIEW_BUTTON_HEIGHT, 0);
		buttonRow.addUIElement(btnHolder1).inTL(0, 0);
		
		TooltipMakerAPI btnHolder2 = buttonRow.createUIElement(VIEW_BUTTON_WIDTH * 2, 
				VIEW_BUTTON_HEIGHT, false);
		ButtonAPI btn = btnHolder2.addButton(getString("btnGovernorship"), 
				BUTTON_GOVERNORSHIP, VIEW_BUTTON_WIDTH * 2, VIEW_BUTTON_HEIGHT, 0);
		buttonRow.addUIElement(btnHolder2).rightOfTop(btnHolder1, 4);
		info.addCustom(buttonRow, opad);
		
		if (market.getMemoryWithoutUpdate().getBoolean(Nex_BuyColony.MEMORY_KEY_NO_BUY)) {
			btn.setEnabled(false);
			info.addPara(getString("intelDesc_postVictoryOptionsNoBuy"), 3);
		}
		
		str = getString("intelDesc_postVictoryOptionsTime");
		info.addPara(str, opad, Misc.getHighlightColor(), String.format("%.0f", timerForDecision));
	}
	
	public void generatePostBattleDisplay(CustomPanelAPI outer, TooltipMakerAPI info, float width, float height) {
		float pad = 3;
		float opad = 10;
		
		info.addImages(width, 128, pad, pad, attacker.faction.getLogo(), defender.faction.getLogo());
		if (outcome != null) {
			String id = "descOutcome";
			switch (outcome) {
				case ATTACKER_VICTORY:
					id += "AttackerVictory";
					break;
				case DEFENDER_VICTORY:
					id += "DefenderVictory";
					break;
				case PEACE:
					id += "Peace";
					break;
				case DESTROYED:
					id += "Destroyed";
					break;
				case CANCELLED:
					id += "Cancelled";
					break;
				default:
					id += "Other";
					break;
			}
			String str = getString(id);
			str = StringHelper.substituteTokens(str, getFactionSubs());
			info.addPara(str, opad);
		}
		if (outcome == BattleOutcome.ATTACKER_VICTORY) {
			info.addSectionHeading(StringHelper.getString("exerelin_markets", "intelTransferFactionSizeHeader"),
				attacker.getFaction().getBaseUIColor(), attacker.getFaction().getDarkUIColor(), Alignment.MID, opad);
			
			MarketTransferIntel.addFactionCurrentInfoPara(info, attacker.getFaction().getId(), opad);
			MarketTransferIntel.addFactionCurrentInfoPara(info, defender.getFaction().getId(), opad);
		}
		
		info.addSectionHeading(getString("intelDesc_otherNotes"), attacker.getFaction().getBaseUIColor(), 
					attacker.getFaction().getDarkUIColor(), Alignment.MID, opad);
		
		if (outcome != BattleOutcome.CANCELLED && (playerIsAttacker != null || !playerData.getSentToStorage().isEmpty())) 
		{
			SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
			if (storage != null) {
				info.addPara(getString("intelDesc_lootAndSurvivors"), opad);
				if (playerData.getLoot() != null) {
					info.showCargo(playerData.getLoot(), 10, true, opad);
				}					
				else {
					info.addPara(getString("intelDesc_localStorage"), opad);
					info.showCargo(storage.getCargo(), 10, true, opad);
				}
			} else {
				info.addPara(getString("intelDesc_lootAndSurvivorsDirect"), opad);
			}
		}
		FactionAPI commission = Misc.getCommissionFaction();
		if (playerInitiated && outcome == BattleOutcome.ATTACKER_VICTORY && commission != null) 
		{
			// Andrada and governorship buttons here
			if (playerData.andradaRepChange != null) {
				String str = getString("intelDesc_andrada");
				str = StringHelper.substituteToken(str, "$market", market.getName());
				str = StringHelper.substituteFactionTokens(str, commission);
				info.addPara(str, opad);
				CoreReputationPlugin.addAdjustmentMessage(playerData.andradaRepChange.delta, 
						commission, null, null, null, info, Misc.getTextColor(), true, 3);
			} else if (playerData.governorshipPrice != null) {
				String str = getString("intelDesc_governorship");
				str = StringHelper.substituteToken(str, "$market", market.getName());
				info.addPara(str, opad, Misc.getHighlightColor(), Misc.getDGSCredits(playerData.governorshipPrice));
			} else if (timerForDecision != null) {
				addPostVictoryButtons(outer, info, width);
			}
		}
	}
	
	@Override
	public boolean doesButtonHaveConfirmDialog(Object buttonId) {
		return buttonId == BUTTON_ANDRADA || buttonId == BUTTON_GOVERNORSHIP || buttonId instanceof Pair;
	}
	
	@Override
	public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
		String str;
		if (buttonId == BUTTON_ANDRADA) {
			if (wasPlayerMarket()) {
				str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", "takeForSelfNoWarning",
						"$market", market.getName());
			} else {
				str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", "takeForSelfWarning",
						"$market", market.getName());
			}
			prompt.addPara(str, 0);
			return;
		}
		if (buttonId == BUTTON_GOVERNORSHIP) {
			MutableStat cost = Nex_BuyColony.getValue(market, false, true);
			str = getString("btnGovernorshipConfirmPrompt");
			str = StringHelper.substituteToken(str, "$market", market.getName());
			int curr = (int)Global.getSector().getPlayerFleet().getCargo().getCredits().get();
			Color hl = cost.getModifiedValue() > curr ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor();
			prompt.addPara(str, 0, hl, Misc.getDGSCredits(cost.getModifiedValue()), Misc.getDGSCredits(curr));
			prompt.addStatModGrid(480, 80, 100, 10, cost, true, NexUtils.getStatModValueGetter(true, 0));
			return;
		}
		
		if (buttonId instanceof Pair) {
			try {
				Pair pair = (Pair)buttonId;
				String action = (String)pair.one;
				if (pair.two instanceof IndustryForBattle) {
					IndustryForBattle ifb = (IndustryForBattle)pair.two;
					switch (action) {
						case "loot":
							str = getString("btnLootConfirmPrompt");
							prompt.addPara(str, 0);
							String aiCore = ifb.getIndustry().getAICoreId();
							SpecialItemData special = ifb.getIndustry().getSpecialItem();
							if (aiCore != null)
								prompt.addPara(" - " + Global.getSettings().getCommoditySpec(aiCore).getName(), 0);
							if (special != null) {
								str = Global.getSettings().getSpecialItemSpec(special.getId()).getName();
								prompt.addPara(" - " + str, 0);
							}
							break;
					}
				}
			} catch (Exception ex) {
				// do nothing?
			}
		}
	}
		
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		
		if (buttonId == BUTTON_RESOLVE) {
			advanceTurn(true);
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId instanceof ViewMode) {
			viewMode = (ViewMode)buttonId;
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId instanceof GroundUnit) {
			ui.showDialog(market.getPrimaryEntity(), new UnitOrderDialogPlugin(this, (GroundUnit)buttonId, ui));
			return;
		}
		if (buttonId instanceof UnitQuickMoveHax) {
			UnitOrderDialogPlugin dialog = new UnitOrderDialogPlugin(this, ((UnitQuickMoveHax)buttonId).unit, ui);
			dialog.setQuickMove(true);
			ui.showDialog(market.getPrimaryEntity(), dialog);
			return;
		}
		if (buttonId instanceof AbilityPlugin) {
			ui.showDialog(market.getPrimaryEntity(), new AbilityDialogPlugin((AbilityPlugin)buttonId, ui));
		}
		if (buttonId == GroundUnit.BUTTON_NEW_HEAVY) {
			createPlayerUnit(ForceType.HEAVY);
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId == GroundUnit.BUTTON_NEW_MARINE) {
			createPlayerUnit(ForceType.MARINE);
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId == BUTTON_ANDRADA) {
			handleAndradaOption();
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId == BUTTON_GOVERNORSHIP) {
			handleGovernorshipPurchase();
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId == BUTTON_DEBUG_AI) {
			runAI(false, false);
			return;
		}
		if (buttonId == BUTTON_AUTO_MOVE) {
			runAI(playerIsAttacker, true);
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId == BUTTON_AUTO_MOVE_TOGGLE) {
			playerData.autoMoveAtEndTurn = !playerData.autoMoveAtEndTurn;
			return;
		}
		
		if (buttonId instanceof Pair) {
			try {
				Pair pair = (Pair)buttonId;
				String action = (String)pair.one;
				if (pair.two instanceof IndustryForBattle) {
					IndustryForBattle ifb = (IndustryForBattle)pair.two;
					switch (action) {
						case "loot":
							loot(ifb);
							ui.updateUIForItem(this);
							break;
					}
				}
			} catch (Exception ex) {
				log.error("Button press failed", ex);
			}
		}
	}
	
	// adapted from Starship Legends' BattleReport
    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
		float pad = 3;
		float opad = 10;
		FactionAPI faction = market.getFaction();
		
		TooltipMakerAPI outer = panel.createUIElement(width, height, true);
		
		outer.addSectionHeading(getSmallDescriptionTitle(), faction.getBaseUIColor(), 
				faction.getDarkUIColor(), com.fs.starfarer.api.ui.Alignment.MID, opad);
		
		if (outcome != null) {
			generatePostBattleDisplay(panel, outer, width, height);
			generateLogDisplay(outer, panel, width - 14);
			panel.addUIElement(outer).inTL(0, 0);
			return;
		}
		
		if (viewMode == null) viewMode = ViewMode.UNITS;
		
		generateIntro(panel, outer, width, opad);
		
		if (viewMode == ViewMode.HELP) {
			generateHelpDisplay(outer, panel, width);
			panel.addUIElement(outer).inTL(0, 0);
			return;
		}
		
		if (viewMode == ViewMode.UNITS) {
			if (playerIsAttacker != null)
				generateUnitDisplay(outer, panel, width, opad);
		} 
		else if (viewMode == ViewMode.ABILITIES) {
			generateAbilityDisplay(outer, panel, width, opad);
		}
		else if (viewMode == ViewMode.INFO) {
			generateModifiersDisplay(outer, panel, width, opad);
		}
		else if (viewMode == ViewMode.LOG) {
			generateLogDisplay(outer, panel, width - 14);
		}
		
		generateIndustryDisplay(outer, panel, width);
		
		panel.addUIElement(outer).inTL(0, 0);
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		if (true) return super.getFactionForUIColors();
		
		if (Boolean.FALSE.equals(playerIsAttacker)) {
			return defender.getFaction();
		}
		return attacker.getFaction();
	}
	
	@Override
	protected String getName() {
		return getSmallDescriptionTitle();
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		String str = getString("intelTitle");
		str = StringHelper.substituteToken(str, "$market", market.getName());
		if (outcome != null) {
			if (outcome == BattleOutcome.ATTACKER_VICTORY)
				str += " - " + StringHelper.getString("successful", true);
			else if (outcome == BattleOutcome.DEFENDER_VICTORY)
				str += " - " + StringHelper.getString("failed", true);
			else
				str += " - " + StringHelper.getString("over", true);
		}
		return str;
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		return super.getMapLocation(map); //To change body of generated methods, choose Tools | Templates.
	}
	
	@Override
	public String getIcon() {
		return "graphics/icons/markets/mercenaries.png";
	}
	
	@Override
	public String getCommMessageSound() {
		if (listInfoParam == UPDATE_TURN && abilitiesUsedLastTurn.isEmpty())
			return "nex_sfx_combat";
		//if (listInfoParam == UPDATE_VICTORY)
		//	return "nex_sfx_combat";	// maybe different sound?
		
		return getSoundMajorPosting();
	}
	
	@Override
	protected float getBaseDaysAfterEnd() {
		return 30;
	}
		
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_MILITARY);
		//tags.add(StringHelper.getString("exerelin_invasion", "invasions", true));
		if (defender.faction.isPlayerFaction())
			tags.add(Tags.INTEL_COLONIES);
		tags.add(attacker.faction.getId());
		tags.add(defender.faction.getId());
		return tags;
	}
	
	@Override
    public boolean hasSmallDescription() {
        return false;
    }

    @Override
    public boolean hasLargeDescription() { 
		return true; 
	}
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_invasion2", id, ucFirst);
	}
	
	// =========================================================================
	// other stuff
	
	public static GroundBattleIntel getOngoing(MarketAPI market) {
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(GroundBattleIntel.class))
		{
			GroundBattleIntel gbi = (GroundBattleIntel)intel;
			if (gbi.market == market && !gbi.isEnding() && !gbi.isEnded())
				return gbi;
		}
		return null;
	}
	
	public static List<GroundBattleIntel> getOngoing() {
		List<GroundBattleIntel> results = new ArrayList<>();
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(GroundBattleIntel.class))
		{
			GroundBattleIntel gbi = (GroundBattleIntel)intel;
			if (!gbi.isEnding() && !gbi.isEnded() && gbi.getOutcome() == null)
				results.add(gbi);
		}
		return results;
	}
	
	// runcode exerelin.campaign.intel.groundbattle.GroundBattleIntel.createDebugEvent();
	public static void createDebugEvent() {
		MarketAPI market = Global.getSector().getEconomy().getMarket("yesod");
		FactionAPI attacker = Global.getSector().getFaction("hegemony");
		FactionAPI defender = market.getFaction();
		
		new GroundBattleIntel(market, attacker, defender).initDebug();
	}
	
	public enum ViewMode {
		UNITS, ABILITIES, INFO, LOG, HELP
	}
	
	public enum BattleOutcome {
		ATTACKER_VICTORY, DEFENDER_VICTORY, PEACE, DESTROYED, CANCELLED, OTHER
	}
	
	public static final Comparator<Industry> INDUSTRY_COMPARATOR = new Comparator<Industry>() {
		@Override
		public int compare(Industry one, Industry two) {
			return Integer.compare(one.getSpec().getOrder(), two.getSpec().getOrder());
		}
	};
}
