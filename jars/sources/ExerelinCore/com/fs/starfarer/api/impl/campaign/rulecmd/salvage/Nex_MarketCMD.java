package com.fs.starfarer.api.impl.campaign.rulecmd.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import static com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker.XP_PER_RAID_MULT;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.graid.GroundRaidObjectivePlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import static com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin.getEntityMemory;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.impl.campaign.rulecmd.VIC_MarketCMD;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.BOMBARD;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.ENGAGE;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.GO_BACK;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.HOSTILE_ACTIONS_TIMEOUT_DAYS;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RAID;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RAID_CONFIRM_CONTINUE;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.addBombardVisual;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.applyDefenderIncreaseFromRaid;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.applyRaidStabiltyPenalty;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.getBombardDestroyThreshold;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.getSaturationBombardmentStabilityPenalty;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.getTacticalBombardmentStabilityPenalty;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.InvasionRound.InvasionRoundResult;
import static exerelin.campaign.InvasionRound.getString;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.battle.NexFleetInteractionDialogPluginImpl;
import exerelin.campaign.fleets.ResponseFleetManager;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.campaign.intel.groundbattle.GBUtils;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

public class Nex_MarketCMD extends MarketCMD {
	
	public static final String INVADE = "nex_mktInvade";
	public static final String INVADE_CONFIRM = "nex_mktInvadeConfirm";
	public static final String INVADE_ABORT = "nex_mktInvadeAbort";
	public static final String INVADE_RESULT = "nex_mktInvadeResult";
	public static final String INVADE_RESULT_ANDRADA = "nex_mktInvadeResultAndrada";
	public static final String INVADE_GO_BACK = "nex_mktInvadeGoBack";
	public static final float FAIL_THRESHOLD_INVASION = 0.5f;
	public static final float TACTICAL_BOMBARD_FUEL_MULT = 1;	// 0.5f;
	public static final float TACTICAL_BOMBARD_DISRUPT_MULT = 1f;	// 1/3f;
	public static final float INVASION_XP_MULT = 3;
	public static final String MEMORY_KEY_BP_COOLDOWN = "$nex_raid_blueprints_cooldown";
	public static final String DATA_KEY_BPS_ALREADY_RAIDED = "nex_already_raided_blueprints";
	public static final float BASE_LOOT_SCORE = 3;
	
	public static Logger log = Global.getLogger(Nex_MarketCMD.class);
	
	protected TempDataInvasion tempInvasion = new TempDataInvasion();
	
	public Nex_MarketCMD() {
		
	}
	
	public Nex_MarketCMD(SectorEntityToken entity) {
		super(entity);
		initForInvasion(entity);
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		super.execute(ruleId, dialog, params, memoryMap);
		
		String command = params.get(0).getString(memoryMap);
		if (command == null) return false;
		
		initForInvasion(dialog.getInteractionTarget());
		
		if (command.equals("invadeMenu")) {
			if (NexConfig.legacyInvasions)
				invadeMenu();
			else
				invadeMenuV2();
		} else if (command.equals("invadeConfirm")) {
			if (NexConfig.legacyInvasions)
				invadeRunRound();
			else
				invadeInitV2();
		} else if (command.equals("openIntel")) {
			openInvasionIntel(null);
		} else if (command.equals("invadeAbort")) {
			invadeFinish();
		} else if (command.equals("invadeResult")) {
			invadeResult(false);
		} else if (command.equals("invadeResultAndrada")) {
			invadeResult(true);
		} else if (command.equals("cleanupResponder")) {
			cleanupResponder();
		}else if (hasVIC() && command.equals(VIC_MarketCMD.VBombMenu))
		{
			new VIC_MarketCMD().execute(ruleId, dialog, params, memoryMap);
		}
		
		return true;
	}
	
	protected void initForInvasion(SectorEntityToken entity) {
		String key = "$nex_MarketCMD_tempInvasion";
		MemoryAPI mem = market.getMemoryWithoutUpdate();
		if (mem.contains(key)) {
			tempInvasion = (TempDataInvasion) mem.get(key);
		} else {
			mem.set(key, tempInvasion, 0f);
		}
	}
	
	@Override
	protected void clearTemp() {
		super.clearTemp();
		if (tempInvasion != null) {
			tempInvasion.invasionLoot = null;
			tempInvasion.invasionValuables = null;
		}
	}
	
	protected boolean hasVIC() {
		return Global.getSettings().getModManager().isModEnabled("vic");
	}
	
	protected boolean hasII() {
		return Global.getSettings().getModManager().isModEnabled("Imperium");
	}
	
	protected boolean canVirusBomb() {
		if (!hasVIC()) return false;
		for (FleetMemberAPI shipToCheck : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy())
		{
			if (shipToCheck.getVariant().hasHullMod(VIC_MarketCMD.MOD_TO_CHECK))
			{
				return true;
			}
		}
		return false;
	}
	
	protected boolean canTitanBomb() {
		if (!hasII()) return false;
		
		if (playerFleet == null) {
			return false;
		}

		for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
			String hullId = member.getHullSpec().getDParentHullId();
			if (hullId == null) hullId = member.getHullSpec().getHullId();
			//log.info("Testing hull id " + hullId);
			
			if (hullId != null && hullId.contentEquals("ii_olympus")) {
				return true;
			}
		}
		return false;
	}
	
	protected CampaignFleetAPI getOrGenerateResponseFleet() {
		CampaignFleetAPI responder = null;
		MemoryAPI memoryMarket = memoryMap.get(MemKeys.MARKET);
		if (memoryMarket.contains(ResponseFleetManager.MEMORY_KEY_FLEET)) {
			responder = memoryMarket.getFleet(ResponseFleetManager.MEMORY_KEY_FLEET);
		} else {
			responder = ResponseFleetManager.generateResponseFleet(market);
			memoryMarket.set(ResponseFleetManager.MEMORY_KEY_FLEET, responder, ResponseFleetManager.RESPONSE_FLEET_TTL);
		}
		return responder;
	}
	
	@Override
	protected CampaignFleetAPI getInteractionTargetForFIDPI() {
		CampaignFleetAPI fleet = super.getInteractionTargetForFIDPI();
		if (fleet == null) fleet = getOrGenerateResponseFleet();
		return fleet;
	}
	
	// same as super method, but adds invade option and response fleets
	@Override
	protected void showDefenses(boolean withText) {
		CampaignFleetAPI primary = getInteractionTargetForFIDPI();
		CampaignFleetAPI station = getStationFleet();
		
		boolean hasNonStation = false;
		boolean hasOtherButInsignificant = true;
		boolean hasStation = station != null;
		boolean otherWantsToFight = false;
		BattleAPI b = null;
		FleetEncounterContext context = null;
		NexFleetInteractionDialogPluginImpl plugin = null;
		
		boolean ongoingBattle = false;
		
		boolean playerOnDefenderSide = false;
		boolean playerCanNotJoin = false;

		String stationType = "station";
		if (station != null) {
			FleetMemberAPI flagship = station.getFlagship();
			if (flagship != null && flagship.getVariant() != null) {
				String name = flagship.getVariant().getDesignation().toLowerCase();
				stationType = name;
			}
		}
		
		StationState state = getStationState();
		
		if (market != null) {
			Global.getSector().getEconomy().tripleStep();
		}
		
		
		ongoingBattle = primary != null && primary.getBattle() != null;
		
		boolean shouldSpawnResponder = !ongoingBattle;
		if (primary != null && primary.getBattle() != null) {
			BattleAPI.BattleSide playerSide = primary.getBattle().pickSide(playerFleet);
			boolean playerWillOpposePrimary = playerSide != BattleAPI.BattleSide.NO_JOIN && playerSide != primary.getBattle().pickSide(primary);
			shouldSpawnResponder = shouldSpawnResponder || playerWillOpposePrimary;
		}
		CampaignFleetAPI responder = shouldSpawnResponder ? getOrGenerateResponseFleet() : null;
		if (responder != null) {
			responder.setLocation(entity.getLocation().x, entity.getLocation().y);
			//responder.setContainingLocation(entity.getContainingLocation());
			//responder.setLocation(99999, 99999);
			entity.getContainingLocation().addEntity(responder);
		}
		
		boolean hasResponder = responder != null;
		
		if (primary == null) {
			if (state == StationState.NONE) {
				text.addPara(StringHelper.getString("nex_militaryOptions", "noStation"));
			} else {
				printStationState();
				text.addPara(StringHelper.getString("nex_militaryOptions", "noFleets"));
			}
		} else {
			ongoingBattle = primary.getBattle() != null;

			CampaignFleetAPI pluginFleet = primary;
			if (ongoingBattle) {
				BattleAPI.BattleSide playerSide = primary.getBattle().pickSide(playerFleet);
				CampaignFleetAPI other = primary.getBattle().getPrimary(primary.getBattle().getOtherSide(playerSide));
				if (other != null) {
					pluginFleet = other;
				}
			}
			
			FleetInteractionDialogPluginImpl.FIDConfig params = new FleetInteractionDialogPluginImpl.FIDConfig();
			params.justShowFleets = true;
			params.showPullInText = withText;
			plugin = new NexFleetInteractionDialogPluginImpl(params);
			//dialog.setInteractionTarget(primary);
			dialog.setInteractionTarget(pluginFleet);
			plugin.init(dialog);
//			if (ongoingBattle) {
//				plugin.setPlayerFleet(primary.getBattle().getPlayerCombined());
//			}
			dialog.setInteractionTarget(entity);
			
			
			context = (FleetEncounterContext)plugin.getContext();
			b = context.getBattle();
			
			BattleAPI.BattleSide playerSide = b.pickSide(playerFleet);
			if (playerSide != BattleAPI.BattleSide.NO_JOIN) {
				if (b.getOtherSideCombined(playerSide).isEmpty()) {
					playerSide = BattleAPI.BattleSide.NO_JOIN;
				}
			}
			playerCanNotJoin = playerSide == BattleAPI.BattleSide.NO_JOIN;
			if (!playerCanNotJoin) {
				playerOnDefenderSide = b.getSide(playerSide) == b.getSideFor(primary);
			}
			if (!ongoingBattle) {
				playerOnDefenderSide = false;
			}

			boolean otherHasStation = false;
			if (playerSide != BattleAPI.BattleSide.NO_JOIN) {
				//for (CampaignFleetAPI fleet : b.getNonPlayerSide()) {
				if (station != null) {
					for (CampaignFleetAPI fleet : b.getSideFor(station)) {
						if (fleet == responder) continue;
						if (!fleet.isStationMode()) {
							hasNonStation = true;
							hasOtherButInsignificant &= Misc.isInsignificant(fleet);
						}
					}
				} else {
					if (b.getNonPlayerSide() != null) {
						for (CampaignFleetAPI fleet : b.getNonPlayerSide()) {
							if (fleet == responder) continue;
							if (!fleet.isStationMode()) {
								hasNonStation = true;
								hasOtherButInsignificant &= Misc.isInsignificant(fleet);
							}
						}
					} else {
						hasNonStation = true;
					}
				}
				
				for (CampaignFleetAPI fleet : b.getOtherSide(playerSide)) {
					if (!fleet.isStationMode()) {
						//hasNonStation = true;
					} else {
						otherHasStation = true;
					}
				}
			}
			
			if (!hasNonStation) hasOtherButInsignificant = false;
			
			//otherWantsToFight = hasStation || plugin.otherFleetWantsToFight(true);
			
			// inaccurate because it doesn't include the station in the "wants to fight" calculation, but, this is tricky
			// and I don't want to break it right now
			otherWantsToFight = otherHasStation || plugin.otherFleetWantsToFight(true);
			
			if (withText) {
				if (hasStation) {
					String name = StringHelper.getString("nex_militaryOptions", "stationNameGeneric");
					if (station != null) {
						FleetMemberAPI flagship = station.getFlagship();
						if (flagship != null) {
							name = flagship.getVariant().getDesignation().toLowerCase();
							stationType = name;
							name = Misc.ucFirst(station.getFaction().getPersonNamePrefixAOrAn()) + " " + 
									station.getFaction().getPersonNamePrefix() + " " + name;
						}
					}
					text.addPara(StringHelper.getStringAndSubstituteToken("nex_militaryOptions", 
							"hasStation", "$stationName", name));
					
					
					if (hasNonStation) {
						if (ongoingBattle) {
							text.addPara(StringHelper.getString("nex_militaryOptions", "hasFleetOngoingBattle"));
						} else {
							if (hasOtherButInsignificant) {
								text.addPara(StringHelper.getString("nex_militaryOptions", "hasFleetTooSmall"));
							} else {
								text.addPara(StringHelper.getString("nex_militaryOptions", "hasFleetWithStation"));
							}
						}
					}
				} else if (hasNonStation && otherWantsToFight) {
					printStationState();
					text.addPara(StringHelper.getString("nex_militaryOptions", "hasFleet"));
				} else if (hasNonStation && !otherWantsToFight) {
					printStationState();
					text.addPara(StringHelper.getString("nex_militaryOptions", "hasFleetTooSmall"));
				}
				if (!hasNonStation && hasResponder) {
					String str = StringHelper.getString("nex_militaryOptions", otherWantsToFight ? "hasResponder" : "hasResponderTooSmall");
					str = StringHelper.substituteToken(str, "$onOrAt", market.getOnOrAt());
					str = StringHelper.substituteToken(str, "$market", market.getName());
					text.addPara(str);
				}
				
				plugin.printOngoingBattleInfo();
			}
		}

		if (!hasNonStation) hasOtherButInsignificant = false;
		
		if (responder != null) {
			//text.addPara("A response fleet is active $onOrAt $market and ");
		}
		
		options.clearOptions();
		
		String engageText = StringHelper.getString("nex_militaryOptions", "optionEngage");
		
		if (playerCanNotJoin) {
			engageText = StringHelper.getString("nex_militaryOptions", "optionEngage");
		} else if (playerOnDefenderSide) {
			if (hasStation && hasNonStation) {
				engageText = StringHelper.getString("nex_militaryOptions", "optionAidStationAndDefenders");
			} else if (hasStation) {
				engageText = StringHelper.getString("nex_militaryOptions", "optionAidStation");
			} else {
				engageText = StringHelper.getString("nex_militaryOptions", "optionAidDefenders");
			}
		} else {
			if (ongoingBattle) {
				engageText = StringHelper.getString("nex_militaryOptions", "optionAidAttackers");
			} else {
				if (hasStation && hasNonStation) {
					engageText = StringHelper.getString("nex_militaryOptions", "optionEngageStationAndDefenders");
				} else if (hasStation) {
					engageText = StringHelper.getString("nex_militaryOptions", "optionEngageStation");
				} else {
					engageText = StringHelper.getString("nex_militaryOptions", "optionEngageDefenders");
				}
			}
		}
		engageText = StringHelper.substituteToken(engageText, "$stationType", stationType);
		
		
		options.addOption(engageText, ENGAGE);
		
		boolean canOpposeBombardment = (hasNonStation || hasResponder) && otherWantsToFight;
		temp.canRaid = ongoingBattle || hasOtherButInsignificant || (hasNonStation && !otherWantsToFight) || !hasNonStation;
		temp.canBombard = (hasOtherButInsignificant || !canOpposeBombardment) && !hasStation;
		//temp.canSurpriseRaid = Misc.getDaysSinceLastRaided(market) < SURPRISE_RAID_TIMEOUT;
		
		boolean couldRaidIfNotDebug = temp.canRaid;
		if (DebugFlags.MARKET_HOSTILITIES_DEBUG) {
			if (!temp.canRaid || !temp.canBombard) {
				text.addPara("(DEBUG mode: can raid and bombard anyway)");
			}
			temp.canRaid = true;
			temp.canBombard = true;
			//temp.canSurpriseRaid = true;
		}
			
//		options.addOption("Launch a raid against the colony", RAID);
//		options.addOption("Consider an orbital bombardment", BOMBARD);
		options.addOption(StringHelper.getStringAndSubstituteToken("nex_militaryOptions", 
							"optionRaid", "$market", market.getName()), RAID);
		options.addOption(StringHelper.getStringAndSubstituteToken("nex_militaryOptions", 
							"optionBombard", "$market", market.getName()), BOMBARD);
		
		if (!temp.canRaid) {
			options.setEnabled(RAID, false);
			options.setTooltip(RAID, StringHelper.getString("nex_militaryOptions", "cannotRaid"));
		}
		
//		if (!temp.canSurpriseRaid) {
////			float surpriseRaidDays = (int) (SURPRISE_RAID_TIMEOUT - Misc.getDaysSinceLastRaided(market));
////			if (surpriseRaidDays > 0) {
////				surpriseRaidDays = (int) Math.round(surpriseRaidDays);
////				if (surpriseRaidDays < 1) surpriseRaidDays = 1;
////				String days = "days";
////				if (surpriseRaidDays == 1) {
////					days = "day";
////				}
////				//text.addPara("Your ground forces commander estimates that");
////			}
//			options.setEnabled(RAID_SURPRISE, false);
//			options.setTooltip(RAID_SURPRISE, "This colony was raided within the last cycle and its ground defenses are on high alert, making a surprise raid impossible.");
//		}
		
		if (!temp.canBombard) {
			options.setEnabled(BOMBARD, false);
			options.setTooltip(BOMBARD, StringHelper.getString("nex_militaryOptions", "cannotBombard"));
		}
		
		
		//DEBUG = false;
		if (temp.canRaid && getRaidCooldown() > 0) {// && couldRaidIfNotDebug) {
			String daysStr = Misc.getStringForDays((int)Math.ceil(getRaidCooldown()));
			String str = StringHelper.getStringAndSubstituteToken("nex_militaryOptions", 
					"raidCooldown", "$cooldown", daysStr); 
			
			if (!DebugFlags.MARKET_HOSTILITIES_DEBUG) {
				options.setEnabled(RAID, false);
				text.addPara(str);
				text.highlightFirstInLastPara(daysStr, Misc.getHighlightColor());
				temp.canRaid = false;
			} else {
				text.addPara(str);
				text.highlightFirstInLastPara(daysStr, Misc.getHighlightColor());
				text.addPara("(DEBUG mode: can do it anyway)");
			}
			//options.setTooltip(RAID, "Need more time to organize another raid.");
		}
		
		//options.addOption("Launch a raid of the colony", RAID);
		
		
		if (context != null && otherWantsToFight && !playerCanNotJoin) {
			boolean knows = context.getBattle() != null && context.getBattle().getNonPlayerSide() != null &&
							context.getBattle().knowsWhoPlayerIs(context.getBattle().getNonPlayerSide());
			boolean lowImpact = context.isLowRepImpact();
			FactionAPI nonHostile = plugin.getNonHostileOtherFaction();
			//if (!playerFleet.getFaction().isHostileTo(otherFleet.getFaction()) && knows && !context.isEngagedInHostilities()) {
			if (nonHostile != null && knows && !lowImpact && !context.isEngagedInHostilities()) {
				String text = StringHelper.getString("nex_militaryOptions", "nonHostileWarning");
				text = StringHelper.substituteToken(text, "$faction", nonHostile.getDisplayNameLong());
				text = StringHelper.substituteToken(text, "$isOrAre", nonHostile.getDisplayNameIsOrAre());
				
				options.addOptionConfirmation(ENGAGE,
						text, 
						StringHelper.getString("yes", true), 
						StringHelper.getString("neverMind", true));
			}
		} else if (context == null || playerCanNotJoin || !otherWantsToFight) {
			options.setEnabled(ENGAGE, false);
			if (!otherWantsToFight) {
				if (ongoingBattle && playerOnDefenderSide && !otherWantsToFight) {
					options.setTooltip(ENGAGE, getString("dialogNoEngage_def"));
				} else {
					if (playerCanNotJoin) {
						options.setTooltip(ENGAGE, getString("dialogNoEngage"));
					} else if (primary == null) {
						options.setTooltip(ENGAGE, getString("dialogNoEngage_noEnemy"));
					} else {
						options.setTooltip(ENGAGE, getString("dialogNoEngage_avoid"));
					}
				}
			}
		}
		
		boolean canVB = canVirusBomb(), canTB = canTitanBomb();
		if (canVB) {
			options.addOption(StringHelper.getStringAndSubstituteToken("nex_militaryOptions", 
							"optionBombardVirus", "$market", market.getName()), VIC_MarketCMD.VBombMenu);
		}
		if (canTB) {
			options.addOption(StringHelper.getStringAndSubstituteToken("nex_militaryOptions", 
							"optionBombardTitan", "$market", market.getName()), "iiTitanStrikeMenu");
		}
		
		if (!temp.canBombard) {
			if (canVB) {
				options.setEnabled(VIC_MarketCMD.VBombMenu, false);
				options.setTooltip(VIC_MarketCMD.VBombMenu, StringHelper.getString("nex_militaryOptions", "cannotBombard"));
			}
			if (canTB) {
				options.setEnabled("iiTitanStrikeMenu", false);
				options.setTooltip("iiTitanStrikeMenu", StringHelper.getString("nex_militaryOptions", "cannotBombard"));
			}
		}
		
		if (NexConfig.enableInvasions && InvasionRound.canInvade(entity))
		{
			options.addOption(StringHelper.getStringAndSubstituteToken("exerelin_invasion", 
					"invadeOpt", "$market", market.getName()), INVADE);
						
			if (getRaidCooldown() > 0) {
				if (!DebugFlags.MARKET_HOSTILITIES_DEBUG) {
					options.setEnabled(INVADE, false);
					tempInvasion.canInvade = false;
				}
			} 
			else if (GroundBattleIntel.getOngoing(market) != null)
			{
				options.setEnabled(INVADE, false);
				options.setTooltip(INVADE, StringHelper.getString("nex_invasion2", "invasionAlreadyOngoing"));
				tempInvasion.canInvade = false;
			}
			else if (!temp.canRaid || !temp.canBombard)
			{
				options.setEnabled(INVADE, false);
				options.setTooltip(INVADE, StringHelper.getString("exerelin_invasion", "invadeBlocked"));
				tempInvasion.canInvade = false;
			}
		}
		
		options.addOption(StringHelper.getString("goBack", true), GO_BACK);
		options.setShortcut(GO_BACK, Keyboard.KEY_ESCAPE, false, false, false, true);
		
		
		if (plugin != null) {
			plugin.cleanUpBattle();
		}
	}
	
	// Changes from vanilla: Add handling of response fleet
	protected void engage() {
		final SectorEntityToken entity = dialog.getInteractionTarget();
		final MemoryAPI memory = getEntityMemory(memoryMap);
		final MemoryAPI memoryMarket = memoryMap.get(MemKeys.MARKET);

		final CampaignFleetAPI primary = getInteractionTargetForFIDPI();
		
		dialog.setInteractionTarget(primary);
		
		final FleetInteractionDialogPluginImpl.FIDConfig config = new FleetInteractionDialogPluginImpl.FIDConfig();
		config.leaveAlwaysAvailable = true;
		config.showCommLinkOption = false;
		config.showEngageText = false;
		config.showFleetAttitude = false;
		config.showTransponderStatus = false;
		config.alwaysAttackVsAttack = true;
		config.impactsAllyReputation = true;
		config.noSalvageLeaveOptionText = StringHelper.getString("continue", true);
		
		config.dismissOnLeave = false;
		config.printXPToDialog = true;
		
		config.straightToEngage = true;
		
		CampaignFleetAPI station = getStationFleet();
		config.playerAttackingStation = station != null;
		
		final NexFleetInteractionDialogPluginImpl plugin = new NexFleetInteractionDialogPluginImpl(config);
		
		final TextPanelAPI text2 = text;	// needed to prevent an IllegalAccessError
		
		final InteractionDialogPlugin originalPlugin = dialog.getPlugin();
		config.delegate = new FleetInteractionDialogPluginImpl.BaseFIDDelegate() {
			@Override
			public void notifyLeave(InteractionDialogAPI dialog) {
				if (primary.isStationMode()) {
					primary.getMemoryWithoutUpdate().clear();
					primary.clearAssignments();
					//primary.deflate();
				}
				
				dialog.setPlugin(originalPlugin);
				dialog.setInteractionTarget(entity);
				
				boolean quickExit = entity.hasTag(Tags.NON_CLICKABLE);
				
				if (!Global.getSector().getPlayerFleet().isValidPlayerFleet() || quickExit) {
					dialog.getOptionPanel().clearOptions();
					dialog.getOptionPanel().addOption(StringHelper.getString("leave", true), "marketLeave");
					dialog.getOptionPanel().setShortcut("marketLeave", Keyboard.KEY_ESCAPE, false, false, false, true);
	
					dialog.showTextPanel();
					dialog.setPromptText("You decide to...");
					dialog.getVisualPanel().finishFadeFast();
					try {
						text2.updateSize();
					} catch (Error ex) {
						log.error("Text panel error", ex);
					}
					
//					dialog.hideVisualPanel();
//					dialog.getVisualPanel().finishFadeFast();
//					dialog.hideTextPanel();
//					dialog.dismiss();
					return;
				}
				
				if (plugin.getContext() instanceof FleetEncounterContext) {
					FleetEncounterContext context = (FleetEncounterContext) plugin.getContext();
					if (context.didPlayerWinMostRecentBattleOfEncounter()) {
						// may need to do something here re: station being defeated & timed out
						//FireBest.fire(null, dialog, memoryMap, "BeatDefendersContinue");
					} else {
						//dialog.dismiss();
					}
					
					if (context.isEngagedInHostilities()) {
						dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$tradeMode", "NONE", 0);
					}
					
					// MODIFIED: Response fleet handling
					// Adapted from SalvageDefenderInteraction
					CampaignFleetAPI responder = memoryMarket.getFleet(ResponseFleetManager.MEMORY_KEY_FLEET);
					
					if (context.didPlayerWinEncounterOutright()) {						
						memoryMarket.set(ResponseFleetManager.MEMORY_KEY_FLEET, null, ResponseFleetManager.RESPONSE_FLEET_TTL);
						if (responder != null) responder.despawn(CampaignEventListener.FleetDespawnReason.OTHER, null);
					} else if (responder != null) {
						//log.info("Running responder cleanup check");
						boolean persistResponders = false;
						if (context.isEngagedInHostilities()) {
							persistResponders |= !Misc.getSnapshotMembersLost(responder).isEmpty();
							for (FleetMemberAPI member : responder.getFleetData().getMembersListCopy()) {
								if (member.getStatus().needsRepairs()) {
									persistResponders = true;
									break;
								}
							}
						}
						if (persistResponders) {
							// push the fleet out into the real world, easier than trying to babysit it
							responder.getMemoryWithoutUpdate().set("$nex_responder_no_cleanup", true);
							responder.addAssignment(FleetAssignment.ORBIT_PASSIVE, entity, ResponseFleetManager.RESPONSE_FLEET_TTL);
							responder.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, entity, 999);
							memoryMarket.set(ResponseFleetManager.MEMORY_KEY_FLEET, null, ResponseFleetManager.RESPONSE_FLEET_TTL);
						} else {
							cleanupResponder(responder);
						}
					}
					
					showDefenses(context.isEngagedInHostilities());
				} else {
					showDefenses(false);
				}
				dialog.getVisualPanel().finishFadeFast();
				
				//dialog.dismiss();
			}
			@Override
			public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
				//bcc.aiRetreatAllowed = false;
				bcc.objectivesAllowed = false;
			}
			@Override
			public void postPlayerSalvageGeneration(InteractionDialogAPI dialog, FleetEncounterContext context, CargoAPI salvage) {
			}
			
		};
		
		dialog.setPlugin(plugin);
		plugin.init(dialog);
	}
	
	/**
	 * Puts the responder fleet back in storage. Do _not_ use this for when the fleet needs to despawn.
	 */
	public void cleanupResponder() {
		CampaignFleetAPI responder = memoryMap.get(MemKeys.MARKET).getFleet(ResponseFleetManager.MEMORY_KEY_FLEET);
		cleanupResponder(responder);
	}
	
	public void cleanupResponder(CampaignFleetAPI responder) {
		if (responder == null) return;
		if (responder.getMemoryWithoutUpdate().getBoolean("$nex_responder_no_cleanup")) return;
		responder.getMemoryWithoutUpdate().clear(); 
		responder.clearAssignments();
		responder.deflate();
		responder.getContainingLocation().removeEntity(responder);
	}
	
	protected GroundBattleIntel prepIntel() {
		GroundBattleIntel intel = new GroundBattleIntel(market, PlayerFactionStore.getPlayerFaction(), market.getFaction());
		intel.setPlayerInitiated(true);
		intel.setPlayerIsAttacker(true);
		intel.init();
		return intel;
	}
	
	protected void invadeMenu() {
		tempInvasion.invasionValuables = computeInvasionValuables();
		CampaignFleetAPI fleet = playerFleet;
		
		float width = 350;
		float opad = 10f;
		float small = 5f;
		
		Color h = Misc.getHighlightColor();
		
//		dialog.getVisualPanel().showPlanetInfo(market.getPrimaryEntity());
//		dialog.getVisualPanel().finishFadeFast();
		dialog.getVisualPanel().showImagePortion("illustrations", "raid_prepare", 640, 400, 0, 0, 480, 300);

		float marines = playerFleet.getCargo().getMarines();
		float support = Misc.getFleetwideTotalMod(playerFleet, Stats.FLEET_GROUND_SUPPORT, 0f);
		if (support > marines) support = marines;
		float mechs = fleet.getCargo().getCommodityQuantity(Commodities.HAND_WEAPONS) * InvasionRound.HEAVY_WEAPONS_MULT;
		if (mechs > marines) mechs = marines;
		
		StatBonus attackerBase = new StatBonus(); 
		StatBonus defenderBase = new StatBonus(); 
		
		//defenderBase.modifyFlatAlways("base", baseDef, "Base value for a size " + market.getSize() + " colony");
		
		attackerBase.modifyFlatAlways("core_marines", marines, getString("marinesOnBoard", true));
		attackerBase.modifyFlatAlways("core_support", support, getString("groundSupportCapability", true));
		attackerBase.modifyFlatAlways("nex_mechs", mechs, getString("heavyWeaponsOnBoard", true));
		
		NexFactionConfig atkConf = NexConfig.getFactionConfig(PlayerFactionStore.getPlayerFactionId());
		String str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", "attackBonus", "$Faction", 
				Misc.ucFirst(fleet.getFaction().getDisplayName()));
		attackerBase.modifyMult("nex_invasionAtkBonus", atkConf.invasionStrengthBonusAttack + 1, str);
		
		StatBonus attacker = playerFleet.getStats().getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD);
		
		StatBonus defender = InvasionRound.getDefenderStrengthStat(market);
		
		float attackerStr = (int) Math.round(attacker.computeEffective(attackerBase.computeEffective(0f)));
		float defenderStr = (int) Math.round(defender.computeEffective(0));
		
		tempInvasion.attackerStr = attackerStr;
		tempInvasion.defenderStr = defenderStr;
		
		TooltipMakerAPI info = text.beginTooltip();
		
		info.setParaSmallInsignia();
		
		String is = faction.getDisplayNameIsOrAre();
		boolean hostile = faction.isHostileTo(Factions.PLAYER);
		float initPad = 0f;
		if (!hostile) {
			str = getString("nonHostileWarning");
			str = StringHelper.substituteToken(str, "$TheFaction", Misc.ucFirst(faction.getDisplayNameWithArticle()));
			str = StringHelper.substituteToken(str, "$isOrAre", is);
			info.addPara(str, initPad, faction.getBaseUIColor(), faction.getDisplayNameWithArticleWithoutArticle());
			initPad = opad;
		}
		
		float sep = small;
		sep = 3f;
		str = Misc.ucFirst(getString("invasionStrength"));
		info.addPara(str + ": %s", initPad, h, "" + (int)attackerStr);
		info.addStatModGrid(width, 50, opad, small, attackerBase, true, statPrinter(false));
		if (!attacker.isUnmodified()) {
			info.addStatModGrid(width, 50, opad, sep, attacker, true, statPrinter(true));
		}
		
		str = Misc.ucFirst(getString("groundDefStrength"));
		info.addPara(str + ": %s", opad, h, "" + (int)defenderStr);
		//info.addStatModGrid(width, 50, opad, small, defenderBase, true, statPrinter());
		//if (!defender.isUnmodified()) {
			info.addStatModGrid(width, 50, opad, small, defender, true, statPrinter(true));
		//}
			
		defender.unmodifyFlat("core_addedDefStr");
		
		text.addTooltip();

		boolean hasForces = true;
		tempInvasion.invasionMult = attackerStr / Math.max(1f, (attackerStr + defenderStr));
		
		if (tempInvasion.invasionMult < 0.25f) {
			text.addPara(getString("insufficientForces"));
			hasForces = false;
		} else {
			Color eColor = h;
			if (tempInvasion.invasionMult < FAIL_THRESHOLD_INVASION) {
				eColor = Misc.getNegativeHighlightColor();
				//temp.canFail = true;
			} else if (tempInvasion.invasionMult >= 0.8f) {
				eColor = Misc.getPositiveHighlightColor();
			}
			text.addPara(Misc.ucFirst(getString("forceBalance")) + ": %s",
					eColor,
					"" + (int)Math.round(tempInvasion.invasionMult * 100f) + "%");
		}
		if (DebugFlags.MARKET_HOSTILITIES_DEBUG) {
		}
		
		if (Misc.isStoryCritical(market)) {
			text.setFontSmallInsignia();
			str = getString("storyCriticalWarning");
			str = StringHelper.substituteToken(str, "$market", market.getName());
			LabelAPI para = text.addPara(str);
			para.setHighlight(market.getName(), getString("storyCriticalWarningHighlight"));
			para.setHighlightColors(market.getFaction().getBaseUIColor(), Global.getSector().getPlayerFaction().getBaseUIColor());
			text.setFontInsignia();
		}
		
		options.clearOptions();
		
		options.addOption(getString("invasionProceed"), INVADE_CONFIRM);
		
		// FIXME: magic number
		if (!hasForces) {
			String pct = 25 + "%";
			str = StringHelper.substituteToken(getString("insufficientForcesTooltip"), "$percent", pct);
			options.setTooltip(INVADE_CONFIRM, str);
			options.setEnabled(INVADE_CONFIRM, false);
			options.setTooltipHighlightColors(INVADE_CONFIRM, h);
		}
			
		options.addOption(Misc.ucFirst(StringHelper.getString("goBack")), INVADE_GO_BACK);
		options.setShortcut(INVADE_GO_BACK, Keyboard.KEY_ESCAPE, false, false, false, true);
	}
	
	protected void invadeMenuV2() {
		CampaignFleetAPI fleet = playerFleet;
		
		float width = 350;
		float opad = 10f;
		float small = 5f;
		
		Color h = Misc.getHighlightColor();
		
		dialog.getVisualPanel().showImagePortion("illustrations", "raid_prepare", 640, 400, 0, 0, 480, 300);

		float marines = playerFleet.getCargo().getMarines();
		float mechs = fleet.getCargo().getCommodityQuantity(Commodities.HAND_WEAPONS) * InvasionRound.HEAVY_WEAPONS_MULT;
		
		if (marines <= 0 && mechs <= 0) {
			
		}
		String str;
		TooltipMakerAPI info = text.beginTooltip();
		// non-hostile faction warning
		String is = faction.getDisplayNameIsOrAre();
		boolean hostile = faction.isHostileTo(Factions.PLAYER);
		float initPad = 0f;
		if (!hostile) {
			info = text.beginTooltip();
			info.setParaSmallInsignia();
			str = getString("nonHostileWarning");
			str = StringHelper.substituteToken(str, "$TheFaction", Misc.ucFirst(faction.getDisplayNameWithArticle()));
			str = StringHelper.substituteToken(str, "$isOrAre", is);
			info.addPara(str, initPad, faction.getBaseUIColor(), faction.getDisplayNameWithArticleWithoutArticle());
			initPad = opad;
			text.addTooltip();
		}
		
		GroundBattleIntel intel = prepIntel();
		
		float[] strEst = GBUtils.estimateDefenderStrength(intel, true);
		int precision = intel.getUnitSize().avgSize/5;
		
		info = text.beginTooltip();
		info.setParaSmallInsignia();
		info.addPara(String.format(GroundBattleIntel.getString("dialogGarrisonEstimate"), market.getName()), 10);
		info.setParaFontDefault();
		info.addPara("  - " + GroundBattleIntel.getString("dialogStrEstimateMilitia"), 3, 
				h, NexUtils.getEstimateNum(strEst[0], precision) + "");
		info.addPara("  - " + GroundBattleIntel.getString("dialogStrEstimateMarine"), 3, 
				h, NexUtils.getEstimateNum(strEst[1], precision) + "");
		info.addPara("  - " + GroundBattleIntel.getString("dialogStrEstimateHeavy"), 3, 
				h, NexUtils.getEstimateNum(strEst[2], precision) + "");
		
		text.addTooltip();

		boolean hasForces = marines > 0;
		if (!hasForces) {
			text.addPara(GroundBattleIntel.getString("dialogNoForces"));
		} else {
			precision /= 10;
			if (precision < 1) precision = 1;
			float[] strEstPlayer = GBUtils.estimatePlayerStrength(intel);
			info = text.beginTooltip();
			info.setParaSmallInsignia();
			info.addPara(String.format(GroundBattleIntel.getString("dialogPlayerEstimate"), 
					market.getName()), 3);
			info.setParaFontDefault();
			info.addPara("  - " + GroundBattleIntel.getString("dialogStrEstimateMarine"), 3,
					h, NexUtils.getEstimateNum(strEstPlayer[0], precision) + "");
			if (strEstPlayer.length > 1) {
				info.addPara("  - " + GroundBattleIntel.getString("dialogStrEstimateHeavy"), 3,
					h, NexUtils.getEstimateNum(strEstPlayer[1], precision) + "");
			}
			text.addTooltip();
		}
		text.addPara(GroundBattleIntel.getString("dialogEstimateHelp"));
		
		
		if (Misc.isStoryCritical(market)) {
			text.setFontSmallInsignia();
			str = getString("storyCriticalWarning");
			str = StringHelper.substituteToken(str, "$market", market.getName());
			LabelAPI para = text.addPara(str);
			para.setHighlight(market.getName(), getString("storyCriticalWarningHighlight"));
			para.setHighlightColors(market.getFaction().getBaseUIColor(), Global.getSector().getPlayerFaction().getBaseUIColor());
			text.setFontInsignia();
		}
		
		options.clearOptions();
		
		options.addOption(getString("invasionProceed"), INVADE_CONFIRM);
		
		// FIXME: magic number
		if (!hasForces) {
			str = GroundBattleIntel.getString("dialogNoForces");
			options.setTooltip(INVADE_CONFIRM, str);
			options.setEnabled(INVADE_CONFIRM, false);
			options.setTooltipHighlightColors(INVADE_CONFIRM, h);
		}
			
		options.addOption(Misc.ucFirst(StringHelper.getString("goBack")), INVADE_GO_BACK);
		options.setShortcut(INVADE_GO_BACK, Keyboard.KEY_ESCAPE, false, false, false, true);
	}
	
	protected void invadeInitV2() {
				
		CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
		impact.delta = market.getSize() * -0.01f * 1f;
		// not now, we also need to look at requested fleets
		//impact.ensureAtBest = tempInvasion.success ? RepLevel.VENGEFUL : RepLevel.HOSTILE;
		impact.ensureAtBest = RepLevel.HOSTILE;
		Global.getSector().adjustPlayerReputation(
				new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.CUSTOM, 
					impact, null, text, true, true),
					faction.getId());
		
		GroundBattleIntel intel = prepIntel();
		
		Global.getSector().getIntelManager().addIntelToTextPanel(intel, text);
		intel.start();
		
		if (!DebugFlags.MARKET_HOSTILITIES_DEBUG) {
			Misc.increaseMarketHostileTimeout(market, 60f);
		}

		Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), "$nex_recentlyInvaded", 
							   Factions.PLAYER, true, 60f);
		dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$tradeMode", "NONE", 0);
		
		openInvasionIntel(intel);
	}
	
	protected void openInvasionIntel(GroundBattleIntel intel) {
		if (intel == null) intel = GroundBattleIntel.getOngoing(market);
		
		final InteractionDialogAPI dialogF = dialog;
		final Map<String, MemoryAPI> memMapF = memoryMap;
		//dialogF.getTextPanel().addPara("Opening invasion intel");
		
		// TODO: in future, use the showCore override that takes an object arg
		//Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.INTEL, intel);
		//FireAll.fire(null, dialogF, memMapF, "PopulateOptions");
		cleanupResponder();
		
		if (true) {
			dialog.getVisualPanel().showCore(CoreUITabId.INTEL, entity, new CoreInteractionListener(){
				@Override
				public void coreUIDismissed() {
					new ShowDefaultVisual().execute(null, dialogF, new ArrayList<Token>(), memMapF);
					FireAll.fire(null, dialogF, memMapF, "PopulateOptions");
				}
			});
		}
	}
	
	protected void invadeRunRound() 
	{
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		dialog.getVisualPanel().showImagePortion("illustrations", "raid_disrupt_result", 640, 400, 0, 0, 480, 300);
		
		tempInvasion.roundNum += 1;
		if (tempInvasion.roundNum == 1)
		{
			if (!DebugFlags.MARKET_HOSTILITIES_DEBUG) {
				Misc.increaseMarketHostileTimeout(market, 30f);
			}

			setRaidCooldown(getRaidCooldownMax());
			
			
			Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), "$nex_recentlyInvaded", 
								   Factions.PLAYER, true, 60f);
		}
		
		InvasionRoundResult result = InvasionRound.execute(playerFleet, market, 
				tempInvasion.attackerStr, tempInvasion.defenderStr, getRandom());
		tempInvasion.stabilityPenalty += InvasionRound.INSTABILITY_PER_ROUND;		
		tempInvasion.attackerStr = Math.max(result.atkStr, 0);
		tempInvasion.defenderStr = Math.max(result.defStr, 0);
				
		//losses = random.nextInt(marines / 2);
		String roundResultMsg = "";
		if (tempInvasion.defenderStr <= 0)
		{
			roundResultMsg = getString("roundResult_win");
			tempInvasion.success = true;
		}
		else if (tempInvasion.attackerStr <= 0)
		{
			roundResultMsg = getString("roundResult_lose");
		}
		else if (result.atkDam > result.defDam * 2)
		{
			roundResultMsg = getString("roundResult_good");
		}
		else if (result.atkDam >= result.defDam)
		{
			roundResultMsg = getString("roundResult_ok");
		}
		else
		{
			roundResultMsg = getString("roundResult_bad");
		}
		roundResultMsg = StringHelper.substituteToken(roundResultMsg, "$market", market.getName());
		text.addPara(roundResultMsg);
		
		// print things that happened during this round
		text.setFontSmallInsignia();
		
		if (result.losses <= 0 && result.lossesMech <= 0) {
			text.addPara(getString("noLosses"));
		}
		if (result.losses > 0) {
			playerFleet.getCargo().removeMarines(result.losses);
			tempInvasion.marinesLost = result.losses;
			AddRemoveCommodity.addCommodityLossText(Commodities.MARINES, result.losses, text);
		}
		if (result.lossesMech > 0) {
			playerFleet.getCargo().removeCommodity(Commodities.HAND_WEAPONS, result.lossesMech);
			tempInvasion.mechsLost = result.lossesMech;
			AddRemoveCommodity.addCommodityLossText(Commodities.HAND_WEAPONS, result.lossesMech, text);
		}
		
		text.setFontSmallInsignia();
		// disruption
		if (result.disrupted != null)
		{
			String name = result.disrupted.getSpec().getName();
			String durStr = Math.round(result.disruptionLength) + "";
			String str = getString("industryDisruption");
			str = StringHelper.substituteToken(str, "$industry", name);
			str = StringHelper.substituteToken(str, "$days", durStr);
			text.addPara(str, Misc.getHighlightColor(), name, durStr);
		}
		
		Color hl = tempInvasion.attackerStr > tempInvasion.defenderStr ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
		Color hl2 = Misc.getHighlightColor();
		String as = (int)tempInvasion.attackerStr + "";
		String ds = (int)tempInvasion.defenderStr + "";
		String ad = (int)-result.atkDam + "";
		String dd = (int)-result.defDam + "";
		
		String str = getString("attackerStrengthRemaining");
		str = StringHelper.substituteToken(str, "$str", as);
		str = StringHelper.substituteToken(str, "$delta", dd);
		text.addPara(str);
		Highlights h = new Highlights();
		h.setColors(hl, result.atkDam >= result.defDam ? hl2 : Misc.getNegativeHighlightColor());
		h.setText(as, dd);
		text.setHighlightsInLastPara(h);
		
		str = getString("defenderStrengthRemaining");
		str = StringHelper.substituteToken(str, "$str", ds);
		str = StringHelper.substituteToken(str, "$delta", ad);
		text.addPara(str, hl2, ds, ad);
		
		String marinesRemaining = playerFleet.getCargo().getMarines() + "";
		str = Misc.ucFirst(getString("marinesRemaining")) + ": " + marinesRemaining;
		text.addPara(str, hl2, marinesRemaining);
		int mechs = (int)playerFleet.getCargo().getCommodityQuantity(Commodities.HAND_WEAPONS);
		if (mechs > 0) {
			str = Misc.ucFirst(getString("mechsRemaining")) + ": " + mechs;
			text.addPara(str, hl2, mechs + "");
		}
		
		text.setFontInsignia();
		
		if (tempInvasion.success || tempInvasion.attackerStr <= 0)
		{
			invadeFinish();
		}
		else
		{
			if (tempInvasion.roundNum == 1)
				Global.getSoundPlayer().playUISound("nex_sfx_combat", 1f, 1f);
			options.clearOptions();
			// options: continue or leave
			options.addOption(getString("invasionContinue"), INVADE_CONFIRM);
			options.addOption(getString("invasionAbort"), INVADE_ABORT);
		}
	}
	
	/**
	 * Finish the invasion, apply final effects
	 */
	protected void invadeFinish() {
		String defenderId = market.getFactionId();
		
		// unrest
		// note that this is for GUI only, actual impact is caused in InvasionRound
		// do this before applying the actual unrest, so it displays the correct value
		int stabilityPenalty = InvasionRound.getStabilityPenalty(market, tempInvasion.roundNum, tempInvasion.success);
		String origOwner = NexUtilsMarket.getOriginalOwner(market);
		if (tempInvasion.success && origOwner != null && defenderId.equals(origOwner)) {
			stabilityPenalty += InvasionRound.CONQUEST_UNREST_BONUS;
		}
		
		if (stabilityPenalty > 0) {
			text.addPara(StringHelper.substituteToken(getString("stabilityReduced"), 
					"$market", market.getName()), Misc.getHighlightColor(), "" + stabilityPenalty);
		}
		
		Random random = getRandom();
		InvasionRound.finishInvasion(playerFleet, null, market, tempInvasion.roundNum, tempInvasion.success);
		
		applyDefenderIncreaseFromRaid(market);
		
		// cooldown
		setRaidCooldown(getRaidCooldownMax());
		
		// reputation impact
		CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
		impact.delta = market.getSize() * -0.01f * 1f;
		// not now, we also need to look at requested fleets
		//impact.ensureAtBest = tempInvasion.success ? RepLevel.VENGEFUL : RepLevel.HOSTILE;
		impact.ensureAtBest = RepLevel.HOSTILE;
		Global.getSector().adjustPlayerReputation(
				new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.CUSTOM, 
					impact, null, text, true, true),
					faction.getId());
		
		// handle loot
		String contText = null;
		
		float targetValue = getBaseInvasionValue();
		CargoAPI result = Global.getFactory().createCargo(true);
		
		// loot
		if (tempInvasion.success)
		{
			WeightedRandomPicker<CommodityOnMarketAPI> picker = new WeightedRandomPicker<CommodityOnMarketAPI>(random);
			for (CommodityOnMarketAPI com : tempInvasion.invasionValuables.keySet()) {
				picker.add(com, tempInvasion.invasionValuables.get(com));
			}

			//float chunks = 10f;
			float chunks = tempInvasion.invasionValuables.size();
			if (chunks > 6) chunks = 6;
			for (int i = 0; i < chunks; i++) {
				float chunkValue = targetValue * 1f / chunks;
				float randMult = StarSystemGenerator.getNormalRandom(random, 0.5f, 1.5f);
				chunkValue *= randMult;

				CommodityOnMarketAPI pick = picker.pick();
				int quantity = (int) (chunkValue / pick.getCommodity().getBasePrice());
				if (quantity <= 0) continue;
				
				// handled in InvasionRound
				//pick.addTradeModMinus("invasion_" + Misc.genUID(), -quantity, BaseSubmarketPlugin.TRADE_IMPACT_DAYS);

				result.addCommodity(pick.getId(), quantity);
			}

			//raidSpecialItems(result, random, true);

			result.sort();

			tempInvasion.invasionLoot = result;

			tempInvasion.invasionCredits = (int)(targetValue * 0.1f * StarSystemGenerator.getNormalRandom(random, 0.5f, 1.5f));
			if (tempInvasion.invasionCredits < 2) tempInvasion.invasionCredits = 2;

			//result.clear();
			if (result.isEmpty()) {
				text.addPara(getString("endMsgNoLoot"));
			} else {
				text.addPara(getString("endMsgLoot"));
				AddRemoveCommodity.addCreditsGainText(tempInvasion.invasionCredits, text);
				playerFleet.getCargo().getCredits().add(tempInvasion.invasionCredits);
				contText = getString("invasionSpoils");
			}

			NexUtilsMarket.reportInvadeLoot(dialog, market, tempInvasion, tempInvasion.invasionLoot);
		}
		else
		{
			text.addPara(getString("endMsgDefeat"));
			contText = StringHelper.substituteToken(getString("invasionFail"), "$market", market.getName());
		}
		
		int marines = Global.getSector().getPlayerFleet().getCargo().getMarines();
		float total = marines + tempInvasion.marinesLost;
		float xpGain = 1f - tempInvasion.invasionMult;
		xpGain *= total;
		xpGain *= XP_PER_RAID_MULT * INVASION_XP_MULT;
		if (xpGain < 0) xpGain = 0;
		PlayerFleetPersonnelTracker.getInstance().getMarineData().addXP(xpGain);
		PlayerFleetPersonnelTracker.getInstance().update();
		
		Global.getSoundPlayer().playUISound("ui_raid_finished", 1f, 1f);
		
		addContinueOptionInvasion(contText);
	}
	
	protected void addContinueOptionInvasion(String text) {
		if (text == null) text = Misc.ucFirst(StringHelper.getString("continue"));
		options.clearOptions();
		options.addOption(text, INVADE_RESULT);
		
		if (tempInvasion.success && playerFaction != PlayerFactionStore.getPlayerFaction())
		{
			String str = getString("invasionTakeForSelf");
			String marketName = market.getName();
			str = StringHelper.substituteToken(str, "$market", marketName);
			options.addOption(str, INVADE_RESULT_ANDRADA);
			
			if (!wasPlayerMarket()) {
				str = getString("takeForSelfWarning");
				str = StringHelper.substituteToken(str, "$market", marketName);
				options.addOptionConfirmation(INVADE_RESULT_ANDRADA, str, 
						Misc.ucFirst(StringHelper.getString("yes")),
						Misc.ucFirst(StringHelper.getString("no")));
			}
			else {
				str = getString("takeForSelfNoWarning");
				str = StringHelper.substituteToken(str, "$market", marketName);
				options.addOptionConfirmation(INVADE_RESULT_ANDRADA, str, 
						Misc.ucFirst(StringHelper.getString("yes")),
						Misc.ucFirst(StringHelper.getString("no")));
			}
		}
	}
	
	// same as computeRaidValuables except writes to different place
	protected Map<CommodityOnMarketAPI, Float> computeInvasionValuables() {
		Map<CommodityOnMarketAPI, Float> result = new HashMap<CommodityOnMarketAPI, Float>();
		float totalDemand = 0f;
		float totalShortage = 0f;
		for (CommodityOnMarketAPI com : market.getAllCommodities()) {
			if (com.isPersonnel()) continue;
			if (com.getCommodity().hasTag(Commodities.TAG_META)) continue;
			
			int a = com.getAvailable();
			if (a > 0) {
				float num = BaseIndustry.getSizeMult(a) * com.getCommodity().getEconUnit() * 0.5f;
				result.put(com, num);
			}
			
			float max = com.getMaxDemand();
			totalDemand += max;
			totalShortage += Math.max(0, max - a);
		}
		
		tempInvasion.shortageMult = 1f;
		if (totalShortage > 0 && totalDemand > 0) {
			tempInvasion.shortageMult = Math.max(0, totalDemand - totalShortage) / totalDemand;
		}
		
		return result;
	}
	
	// same as getBaseRaidValue except reads tempInvasion instead of temp
	protected float getBaseInvasionValue() {
		float targetValue = 0f;
		for (CommodityOnMarketAPI com : tempInvasion.invasionValuables.keySet()) {
			targetValue += tempInvasion.invasionValuables.get(com) * com.getCommodity().getBasePrice();
		}
		targetValue *= 0.1f;
		targetValue *= tempInvasion.invasionMult;
		targetValue *= tempInvasion.shortageMult;
		return targetValue;
	}
	
	@Override
	protected void finishedRaidOrBombard() {
		super.finishedRaidOrBombard();
		cleanupResponder();
	}
	
	// just to allow it to compile
	@Deprecated
	public int getNumPicks(Random random, float f1, float f2) {
		return 1;
	}
	
	/* 
		TODO: Compare with superclass's output from raids and see if the following are still required:
			Drop blueprints known to player
			Only drop each blueprint once
			Filter out NO_BP_DROP blueprints _before_ adding them to picker
			Loot more small and medium weapons
		
		Vanilla no longer has a raidSpecialItems method;
		We're keeping this one around for legacy invasion loot
	*/
	@Deprecated
	protected void raidSpecialItems(CargoAPI cargo, Random random, boolean isInvasion) 
	{
		float mult = isInvasion ? tempInvasion.invasionMult : temp.raidMult;
		float p = mult * 0.2f;
		
		boolean withBP = false;
		boolean heavyIndustry = false;
		
		for (Industry curr : market.getIndustries()) {
			
			if (!isInvasion) {
				String id = curr.getAICoreId();
				if (id != null && random.nextFloat() < p) {
					curr.setAICoreId(null);
					cargo.addCommodity(id, 1);
				}

				SpecialItemData special = curr.getSpecialItem();
				if (special != null && random.nextFloat() < p) {
					curr.setSpecialItem(null);
					cargo.addSpecial(special, 1);
				}
			}
			if (curr.getSpec().hasTag(Industries.TAG_USES_BLUEPRINTS)) {
				withBP = true;
			}
			if (curr.getSpec().hasTag(Industries.TAG_HEAVYINDUSTRY)) {
				heavyIndustry = true;
			}
		}
		if (withBP) {
			boolean bpCooldown = market.getMemoryWithoutUpdate().getBoolean(MEMORY_KEY_BP_COOLDOWN);
			if (bpCooldown)
				withBP = false;
			else
				market.getMemoryWithoutUpdate().set(MEMORY_KEY_BP_COOLDOWN, true,
						Global.getSettings().getFloat("nex_raidBPCooldown"));
		}
		
		market.reapplyIndustries();
		
		boolean military = market.getMemoryWithoutUpdate().getBoolean(MemFlags.MARKET_MILITARY);
		
		String ship =    "MarketCMD_ship____";
		String weapon =  "MarketCMD_weapon__";
		String fighter = "MarketCMD_fighter_";
		
		// blueprints
		if (withBP) {
			Set<String> droppedBefore = getEverRaidedBlueprints();
			boolean allowRepeat = NexConfig.allowRepeatBlueprintsFromRaid;
			boolean onlyUnlearned = Global.getSettings().getBoolean("nex_raidBPOnlyUnlearned");
			FactionAPI player = Global.getSector().getPlayerFaction();
			WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>(random);
			for (String id : market.getFaction().getKnownShips()) {
				if (!allowRepeat && droppedBefore.contains(id)) continue;
				if (Global.getSettings().getHullSpec(id).hasTag(Tags.NO_BP_DROP)) continue;
				if (onlyUnlearned && player.knowsShip(id)) continue;
				if (Global.getSettings().getHullSpec(id).hasTag(Items.TAG_BASE_BP)) continue;
				picker.add(ship + id, 1f);
			}
			for (String id : market.getFaction().getKnownWeapons()) {
				if (!allowRepeat && droppedBefore.contains(id)) continue;
				if (Global.getSettings().getWeaponSpec(id).hasTag(Tags.NO_BP_DROP)) continue;
				if (onlyUnlearned && player.knowsWeapon(id)) continue;
				if (Global.getSettings().getWeaponSpec(id).hasTag(Items.TAG_BASE_BP)) continue;
				picker.add(weapon + id, 1f);
			}
			for (String id : market.getFaction().getKnownFighters()) {
				if (!allowRepeat && droppedBefore.contains(id)) continue;
				if (Global.getSettings().getFighterWingSpec(id).hasTag(Tags.NO_BP_DROP)) continue;
				if (onlyUnlearned && player.knowsFighter(id)) continue;
				if (Global.getSettings().getFighterWingSpec(id).hasTag(Items.TAG_BASE_BP)) continue;
				picker.add(fighter + id, 1f);
			}
			
			//int num = getNumPicks(random, mult * 0.25f, mult * 0.5f);
			int num = getNumPicksDiminishing(random, 
					mult + 0.5f, 
					mult * Global.getSettings().getFloat("nex_raidBPInitialExtraMult"),
					Global.getSettings().getFloat("nex_raidBPIterationMult")
			);
			for (int i = 0; i < num && !picker.isEmpty(); i++) {
				String id = picker.pickAndRemove();
				if (id == null) continue;
				
				if (id.startsWith(ship)) {
					String specId = id.substring(ship.length());
					cargo.addSpecial(new SpecialItemData(Items.SHIP_BP, specId), 1);
					addEverRaidedBlueprint(specId);
				} else if (id.startsWith(weapon)) {
					String specId = id.substring(weapon.length());
					cargo.addSpecial(new SpecialItemData(Items.WEAPON_BP, specId), 1);
					addEverRaidedBlueprint(specId);
				} else if (id.startsWith(fighter)) {
					String specId = id.substring(fighter.length());
					cargo.addSpecial(new SpecialItemData(Items.FIGHTER_BP, specId), 1);
					addEverRaidedBlueprint(specId);
				}
			}
		}
		
		// modspecs
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>(random);
		for (String id : market.getFaction().getKnownHullMods()) {
			if (playerFaction.knowsHullMod(id) && !DebugFlags.ALLOW_KNOWN_HULLMOD_DROPS) continue;
			picker.add(id, 1f);
		}
		
		// more likely to get at least one modspec, but not likely to get many
		int num = getNumPicks(random, mult + 0.5f, mult * 0.25f);
		for (int i = 0; i < num && !picker.isEmpty(); i++) {
			String id = picker.pickAndRemove();
			if (id == null) continue;
			cargo.addSpecial(new SpecialItemData(Items.MODSPEC, id), 1);
		}
		
		
		// weapons and fighters
		picker = new WeightedRandomPicker<String>(random);
		for (String id : market.getFaction().getKnownWeapons()) {
			WeaponSpecAPI w = Global.getSettings().getWeaponSpec(id);
			if (w.hasTag("no_drop")) continue;
			if (w.getAIHints().contains(WeaponAPI.AIHints.SYSTEM)) continue;
			
			if (!military && !heavyIndustry && 
					(w.getTier() > 1 || w.getSize() == WeaponAPI.WeaponSize.LARGE)) continue;
			
			picker.add(weapon + id, w.getRarity());
		}
		for (String id : market.getFaction().getKnownFighters()) {
			FighterWingSpecAPI f = Global.getSettings().getFighterWingSpec(id);
			if (f.hasTag(Tags.WING_NO_DROP)) continue;
			
			if (!military && !heavyIndustry && f.getTier() > 0) continue;
			
			picker.add(fighter + id, f.getRarity());
		}
		
		
		num = getNumPicks(random, mult + 0.5f, mult * 0.25f);
		if (military || heavyIndustry) {
			num += Math.round(market.getCommodityData(Commodities.SHIPS).getAvailable() * mult);
		}
		
		for (int i = 0; i < num && !picker.isEmpty(); i++) {
			String id = picker.pickAndRemove();
			if (id == null) continue;
			
			if (id.startsWith(weapon)) {
				String weaponId = id.substring(weapon.length());
				int count = 1;
				WeaponSpecAPI w = Global.getSettings().getWeaponSpec(weaponId);
				if (w.getSize() == WeaponSize.SMALL)
					count = 2 + random.nextInt(3);
				else if (w.getSize() == WeaponSize.MEDIUM)
					count = 1 + random.nextInt(2);
				
				cargo.addWeapons(weaponId, count);
			} else if (id.startsWith(fighter)) {
				cargo.addFighters(id.substring(fighter.length()), 1);
			}
		}
	}
	
	/**
	 * Show loot if applicable, cleanup and exit
	 * @param tookForSelf True if we decided to take the market for ourselves, 
	 * instead of turning it over to commissioning faction
	 * Note: this is always false if we have no commission
	 */
	protected void invadeResult(boolean tookForSelf)
	{
		tempInvasion.tookForSelf = tookForSelf;
		
		if (tempInvasion.invasionLoot != null) {
			if (tempInvasion.invasionLoot.isEmpty()) {
				finishedInvade();
			} else {
				invadeShowLoot();
			}
			return;
		} else {
			finishedInvade();
		}
	}
	
	protected void finishedInvade() {
		clearTemp();
		//showDefenses(true);
	
		new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
		
		FactionAPI conqueror = PlayerFactionStore.getPlayerFaction();
		if (tempInvasion.tookForSelf && tempInvasion.success)
		{
			conqueror = playerFaction;
			if (!wasPlayerMarket()) {
				CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
				impact.delta = -0.05f * market.getSize();
				//impact.ensureAtBest = RepLevel.SUSPICIOUS;
				impact.limit = RepLevel.INHOSPITABLE;
				Global.getSector().adjustPlayerReputation(new CoreReputationPlugin.RepActionEnvelope(
						CoreReputationPlugin.RepActions.CUSTOM, impact, null, text, true), 
						PlayerFactionStore.getPlayerFactionId());
			}
		}
		if (tempInvasion.success)
			InvasionRound.conquerMarket(market, conqueror, true);
		
		// report rebellion
		RebellionIntel rebel = RebellionIntel.getOngoingEvent(market);
		if (rebel != null) {
			text.addPara(getString("rebellion"));
			Global.getSector().getIntelManager().addIntelToTextPanel(rebel, text);
		}
		
		//FireAll.fire(null, dialog, memoryMap, "MarketPostOpen");
		dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$menuState", "main", 0);
		if (market.isPlanetConditionMarketOnly()) {
			dialog.getInteractionTarget().getMemoryWithoutUpdate().unset("$hasMarket");
		}
		else
			dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$tradeMode", "OPEN", 0);
		
		if (tempInvasion.success) {
			((RuleBasedDialog)dialog.getPlugin()).updateMemory();
			FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
		}
		
		else
			dialog.dismiss();
	}
	
	protected void invadeShowLoot() {
		dialog.getVisualPanel().showLoot(Misc.ucFirst(StringHelper.getString("spoils")),
				tempInvasion.invasionLoot, false, true, true, new CoreInteractionListener() {
			public void coreUIDismissed() {
				//dialog.dismiss();
				finishedInvade();
			}
		});
	}
	
	
	// Differences from vanilla: Modified defender strength (TODO: decide whether it actually matters)
	protected void bombardMenu() {
		StatBonus defender = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD);
		
		//defender.modifyMult("nex_bombardBonus", BASE_LOOT_SCORE);
		super.bombardMenu();
	}
	
	public static int getBombardDisruptDuration(BombardType type) {
		float dur = Global.getSettings().getFloat("bombardDisruptDuration");
		if (type == BombardType.TACTICAL)
			dur *= TACTICAL_BOMBARD_DISRUPT_MULT;
		return (int) dur;
	}
	
	public static int getBombardmentCost(MarketAPI market, CampaignFleetAPI fleet, BombardType type) {
		int result = MarketCMD.getBombardmentCost(market, fleet);
		if (type == BombardType.TACTICAL)
			result *= TACTICAL_BOMBARD_FUEL_MULT;
		
		return result;
	}
	
	// Difference from vanilla: No insta-hostile if third party is vengeful against target faction
	// or market is small
	@Override
	protected void bombardSaturation() {
		temp.bombardType = BombardType.SATURATION;

		temp.willBecomeHostile.clear();
		temp.willBecomeHostile.add(faction);
		
		boolean hidden = market.isHidden();
		
		List<FactionAPI> nonHostile = new ArrayList<FactionAPI>();
		List<FactionAPI> vengeful = new ArrayList<>();
		
		if (!hidden) {
			for (FactionAPI faction : Global.getSector().getAllFactions()) {
				if (temp.willBecomeHostile.contains(faction)) continue;

				if (faction.getCustomBoolean(Factions.CUSTOM_CARES_ABOUT_ATROCITIES)) {
					if (faction.getRelationshipLevel(market.getFaction()) == RepLevel.VENGEFUL)
					{
						vengeful.add(faction);
					}
					else {
						boolean hostile = faction.isHostileTo(Factions.PLAYER);
						temp.willBecomeHostile.add(faction);
						if (!hostile) {
							nonHostile.add(faction);
						}
					}
				}
			}
		}
		
		float opad = 10f;
		float small = 5f;
		
		Color h = Misc.getHighlightColor();
		Color b = Misc.getNegativeHighlightColor();
		
		
		int dur = getBombardDisruptDuration(temp.bombardType);
		
		List<Industry> targets = new ArrayList<Industry>();
		for (Industry ind : market.getIndustries()) {
			if (!ind.getSpec().hasTag(Industries.TAG_NO_SATURATION_BOMBARDMENT)) {
				if (ind.getDisruptedDays() >= dur * 0.8f) continue;
				targets.add(ind);
			}
		}
		temp.bombardmentTargets.clear();
		temp.bombardmentTargets.addAll(targets);
		
		boolean destroy = market.getSize() <= getBombardDestroyThreshold();
		if (Misc.isStoryCritical(market)) destroy = false;
		
		int fuel = (int) playerFleet.getCargo().getFuel();
		if (destroy) {
			text.addPara(StringHelper.getString("nex_bombardment", "satBombDescDestroy"));
		} else {
			text.addPara(StringHelper.getString("nex_bombardment", "satBombDesc"));
		}		
		
		
//		TooltipMakerAPI info = text.beginTooltip();
//		info.setParaFontDefault();
//		
//		info.setBulletedListMode(BaseIntelPlugin.INDENT);
//		float initPad = 0f;
//		for (Industry ind : targets) {
//			//info.addPara(ind.getCurrentName(), faction.getBaseUIColor(), initPad);
//			info.addPara(ind.getCurrentName(), initPad);
//			initPad = 3f;
//		}
//		info.setBulletedListMode(null);
//		
//		text.addTooltip();
		

		if (hidden) {
			text.addPara(StringHelper.getStringAndSubstituteToken("nex_bombardment", 
					"satBombWarningHidden", "$market", market.getName()));
		}
		else if (nonHostile.isEmpty()) {
			text.addPara(StringHelper.getString("nex_bombardment", "satBombWarningAllHostile"));
		} 
		else if (market.getSize() <= 3 || market.getMemoryWithoutUpdate().getBoolean(ColonyExpeditionIntel.MEMORY_KEY_COLONY))
		{
			text.addPara(StringHelper.getStringAndSubstituteToken("nex_bombardment", 
					"satBombWarningSmall", "$market", market.getName()));
		} else {
			text.addPara(StringHelper.getString("nex_bombardment", "satBombWarning"));
		}
		
		if (!nonHostile.isEmpty()) {
			TooltipMakerAPI info = text.beginTooltip();
			info.setParaFontDefault();
			
			info.setBulletedListMode(BaseIntelPlugin.INDENT);
			float initPad = 0f;
			for (FactionAPI fac : nonHostile) {
				info.addPara(Misc.ucFirst(fac.getDisplayName()), fac.getBaseUIColor(), initPad);
				initPad = 3f;
			}
			info.setBulletedListMode(null);
			
			text.addTooltip();
		}
		
		if (!vengeful.isEmpty()) {
			text.addPara(StringHelper.getStringAndSubstituteToken("nex_bombardment", 
					"satBombWarningVengeful", "$theFaction", faction.getDisplayNameWithArticle()));
			
			TooltipMakerAPI info = text.beginTooltip();
			info.setParaFontDefault();
			
			info.setBulletedListMode(BaseIntelPlugin.INDENT);
			float initPad = 0f;
			for (FactionAPI fac : vengeful) {
				info.addPara(Misc.ucFirst(fac.getDisplayName()), fac.getBaseUIColor(), initPad);
				initPad = 3f;
			}
			info.setBulletedListMode(null);
			
			text.addTooltip();
		}
		
		text.addPara(StringHelper.getString("nex_bombardment", "fuelCost"),
					 h, "" + temp.bombardCost, "" + fuel);
		
		addBombardConfirmOptions();
	}
	
	// Changes from vanilla: Custom rep handling for sat bomb;
	// saturation bombardment affects disposition
	@Override
	protected void bombardConfirm() {
		if (temp.bombardType == null) {
			bombardNeverMind();
			return;
		}
		
		if (temp.bombardType == BombardType.TACTICAL) {
			dialog.getVisualPanel().showImagePortion("illustrations", "bombard_tactical_result", 640, 400, 0, 0, 480, 300);
		} else {
			dialog.getVisualPanel().showImagePortion("illustrations", "bombard_saturation_result", 640, 400, 0, 0, 480, 300);
		}
		
		Random random = getRandom();
		
		if (!DebugFlags.MARKET_HOSTILITIES_DEBUG) {
			float timeout = TACTICAL_BOMBARD_TIMEOUT_DAYS;
			if (temp.bombardType == BombardType.SATURATION) {
				timeout = SATURATION_BOMBARD_TIMEOUT_DAYS;
			}
			Misc.increaseMarketHostileTimeout(market, timeout);
			
			timeout *= 0.7f;
			
			for (MarketAPI curr : Global.getSector().getEconomy().getMarkets(market.getContainingLocation())) {
				if (curr == market) continue;
				boolean cares = curr.getFaction().getCustomBoolean(Factions.CUSTOM_CARES_ABOUT_ATROCITIES);
				cares &= temp.bombardType == BombardType.SATURATION;
				
				if (curr.getFaction().isNeutralFaction()) continue;
				if (curr.getFaction().isPlayerFaction()) continue;
				if (curr.getFaction().isHostileTo(market.getFaction()) && !cares) continue;
				
				Misc.increaseMarketHostileTimeout(curr, timeout);
			}
		}
		
		addMilitaryResponse();
		
		playerFleet.getCargo().removeFuel(temp.bombardCost);
		AddRemoveCommodity.addCommodityLossText(Commodities.FUEL, temp.bombardCost, text);
		
		int size = market.getSize();
		for (FactionAPI curr : temp.willBecomeHostile) {
			CustomRepImpact impact = new CustomRepImpact();
			impact.delta = market.getSize() * -0.01f * 1f;
			impact.ensureAtBest = RepLevel.HOSTILE;
			if (temp.bombardType == BombardType.SATURATION) {
				impact.delta = market.getSize() * -0.02f * 1f;
				if (curr == faction) {
					impact.ensureAtBest = RepLevel.VENGEFUL;
					impact.delta *= 2;
				}
				else if (size <= 3) {
					impact.ensureAtBest = RepLevel.NEUTRAL;
				}
				DiplomacyManager.getManager().getDiplomacyBrain(curr.getId()).reportDiplomacyEvent(
						PlayerFactionStore.getPlayerFactionId(), impact.delta);
			}
			Global.getSector().adjustPlayerReputation(
				new RepActionEnvelope(RepActions.CUSTOM, 
					impact, null, text, true, true),
					curr.getId());
		}
	
		if (temp.bombardType == BombardType.SATURATION) {
			int atrocities = (int) Global.getSector().getCharacterData().getMemoryWithoutUpdate().getFloat(MemFlags.PLAYER_ATROCITIES);
			atrocities++;
			Global.getSector().getCharacterData().getMemoryWithoutUpdate().set(MemFlags.PLAYER_ATROCITIES, atrocities);
		}		
		
		int stabilityPenalty = getTacticalBombardmentStabilityPenalty();
		if (temp.bombardType == BombardType.SATURATION) {
			stabilityPenalty = getSaturationBombardmentStabilityPenalty();
		}
		boolean destroy = temp.bombardType == BombardType.SATURATION && market.getSize() <= getBombardDestroyThreshold();
		if (Misc.isStoryCritical(market)) destroy = false;
		
		if (stabilityPenalty > 0 && !destroy) {
			String reason = StringHelper.getString("nex_bombardment", "unrestReason");
			if (Misc.isPlayerFactionSetUp()) {
				reason = StringHelper.getString("nex_bombardment", "unrestReason"); 
				reason = String.format(reason, playerFaction.getDisplayName());
			}
			RecentUnrest.get(market).add(stabilityPenalty, reason);
			String str = StringHelper.getStringAndSubstituteToken("nex_bombardment", 
					"effectStability", "$market", market.getName());
			text.addPara(str, Misc.getHighlightColor(), "" + stabilityPenalty);
		}
		
		if (market.hasCondition(Conditions.HABITABLE) && !market.hasCondition(Conditions.POLLUTION)) {
			market.addCondition(Conditions.POLLUTION);
		}
		
		if (!destroy) {
			for (Industry curr : temp.bombardmentTargets) {
				int dur = getBombardDisruptDuration(temp.bombardType);
				dur *= StarSystemGenerator.getNormalRandom(random, 1f, 1.25f);
				curr.setDisrupted(dur);
			}
		}
		
		
		
		if (temp.bombardType == BombardType.TACTICAL) {
			text.addPara(StringHelper.getString("nex_bombardment", "effectMilitaryDisrupt"));
			
			ListenerUtil.reportTacticalBombardmentFinished(dialog, market, temp);
		} else if (temp.bombardType == BombardType.SATURATION) {
			if (destroy) {
				DecivTracker.decivilize(market, true);
				text.addPara(StringHelper.getStringAndSubstituteToken("nex_bombardment", 
					"effectMarketDestroyed", "$market", market.getName()));
			} else {
				int prevSize = market.getSize();
				CoreImmigrationPluginImpl.reduceMarketSize(market);
				if (prevSize == market.getSize()) {
					text.addPara(StringHelper.getString("nex_bombardment", "effectAllDisrupt"));
				} else {
					text.addPara(StringHelper.getString("nex_bombardment", "effectAllDisruptAndDownsize"), 
							Misc.getHighlightColor()
							, "" + market.getSize());
				}
				
			}
			ListenerUtil.reportSaturationBombardmentFinished(dialog, market, temp);
		}
		
		if (dialog != null && dialog.getPlugin() instanceof RuleBasedDialog) {
			if (dialog.getInteractionTarget() != null &&
					dialog.getInteractionTarget().getMarket() != null) {
				Global.getSector().setPaused(false);
				dialog.getInteractionTarget().getMarket().getMemoryWithoutUpdate().advance(0.0001f);
				Global.getSector().setPaused(true);
			}
			((RuleBasedDialog) dialog.getPlugin()).updateMemory();
		}
		
		Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), MemFlags.RECENTLY_BOMBARDED, 
	   			   			  Factions.PLAYER, true, 30f);

		if (destroy) {
			if (dialog != null && dialog.getPlugin() instanceof RuleBasedDialog) {
				((RuleBasedDialog) dialog.getPlugin()).updateMemory();
//				market.getMemoryWithoutUpdate().unset("$tradeMode");
//				entity.getMemoryWithoutUpdate().unset("$tradeMode");
			}
		}
		
		addBombardVisual(market.getPrimaryEntity());
		
		addBombardContinueOption();
	}
	
	public int applyRaidStabiltyPenaltyNex(MarketAPI target, String desc) {
		int penalty = 0, min = 1;
		RaidDangerLevel highestDanger = RaidDangerLevel.NONE;
		for (GroundRaidObjectivePlugin obj : temp.objectives) {
			penalty += obj.getMarinesAssigned();
			if (obj.getDangerLevel().compareTo(highestDanger) > 0)
				highestDanger = obj.getDangerLevel();
		}
		penalty /= 3;
		switch (highestDanger) {
			case EXTREME:
				min = 3;
				break;
			case HIGH:
				min = 2;
				break;
			case MEDIUM:
			case LOW:
				min = 1;
				break;
		}
		if (penalty < min) penalty = min;
		
		if (penalty > 0) {
			RecentUnrest.get(target).add(penalty, desc);
		}
		return penalty;
	}
	
	// Changes from vanilla: Stability loss not be based on raid effectiveness, rather on number of marine icons and danger level
	protected void raidConfirm(boolean secret) {
		if (temp.raidType == null) {
			raidNeverMind();
			return;
		}
				
		Random random = getRandom();
		
		if (!DebugFlags.MARKET_HOSTILITIES_DEBUG) {
			Misc.increaseMarketHostileTimeout(market, HOSTILE_ACTIONS_TIMEOUT_DAYS);
		}
		
		addMilitaryResponse();		
		
		if (market != null) {
			applyDefenderIncreaseFromRaid(market);
		}
		
		setRaidCooldown(getRaidCooldownMax());
		
		int stabilityPenalty = 0;
		if (!temp.nonMarket) {
			String reason = StringHelper.getString("nex_raidDialog", "recentlyRaided");
			if (Misc.isPlayerFactionSetUp()) {
				reason = String.format(StringHelper.getString("nex_raidDialog", "recentlyRaided"), playerFaction.getDisplayName());
			}
			reason = Misc.ucFirst(reason);
			// MODIFIED
			stabilityPenalty = applyRaidStabiltyPenaltyNex(market, reason);
			Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), MemFlags.RECENTLY_RAIDED, 
								   Factions.PLAYER, true, 30f);
			Misc.setRaidedTimestamp(market);
		}
		
		int marines = playerFleet.getCargo().getMarines();
		float probOfLosses = 1f;
		
		int losses = 0;
		if (random.nextFloat() < probOfLosses) {
			float averageLosses = getAverageMarineLosses(temp.objectives);
			float variance = averageLosses / 4f;
			
			//float randomizedLosses = averageLosses - variance + variance * 2f * random.nextFloat();
			float randomizedLosses = StarSystemGenerator.getNormalRandom(
							random, averageLosses - variance, averageLosses + variance);
			if (randomizedLosses < 1f) {
				randomizedLosses = random.nextFloat() < randomizedLosses ? 1f : 0f;
			}
			randomizedLosses = Math.round(randomizedLosses);
			losses = (int) randomizedLosses;
			
			if (losses < 0) losses = 0;
			if (losses > marines) losses = marines;
		}
		
		//losses = random.nextInt(marines / 2);
		
		if (losses <= 0) {
			text.addPara(StringHelper.getString("nex_raidDialog", "noCasualties"));
			temp.marinesLost = 0;
		} else {
			text.addPara(StringHelper.getString("nex_raidDialog", "casualties"));
			playerFleet.getCargo().removeMarines(losses);
			temp.marinesLost = losses;
			AddRemoveCommodity.addCommodityLossText(Commodities.MARINES, losses, text);
		}
		
		
		if (!secret) {
			boolean tOn = playerFleet.isTransponderOn();
			boolean hostile = faction.isHostileTo(Factions.PLAYER);
			CustomRepImpact impact = new CustomRepImpact();
			if (market != null) {
				impact.delta = market.getSize() * -0.01f * 1f;
			} else {
				impact.delta = -0.01f;
			}
			if (!hostile && tOn) {
				impact.ensureAtBest = RepLevel.HOSTILE;
			}
			if (impact.delta != 0 && !faction.isNeutralFaction()) {
				Global.getSector().adjustPlayerReputation(
						new RepActionEnvelope(RepActions.CUSTOM, 
							impact, null, text, true, true),
							faction.getId());
			}
		}
		
		if (stabilityPenalty > 0) {
			String str = StringHelper.getStringAndSubstituteToken("nex_bombardment", 
					"effectStability", "$market", market.getName());
			text.addPara(str, Misc.getHighlightColor(), "" + stabilityPenalty);
		}
		
//		if (!temp.nonMarket) {
//			if (temp.raidType == RaidType.VALUABLE || true) {
//				text.addPara("The raid was successful in achieving its objectives.");
//			}
//		}
		
		CargoAPI result = performRaid(random, temp.raidMult);
		
		if (market != null) market.reapplyIndustries();
		
		result.sort();
		result.updateSpaceUsed();
		
		temp.raidLoot = result;
		
//		int raidCredits = (int)result.getCredits().get();
//		if (raidCredits < 0) raidCredits = 0;
//		
//		//result.clear();
//		if (raidCredits > 0) {
//			AddRemoveCommodity.addCreditsGainText(raidCredits, text);
//			playerFleet.getCargo().getCredits().add(raidCredits);
//		}
		
		if (temp.xpGained > 0) {
			Global.getSector().getPlayerStats().addXP(temp.xpGained, dialog.getTextPanel());
		}
		if (temp.raidType == RaidType.VALUABLE) {
			if (result.getTotalCrew() + result.getSpaceUsed() + result.getFuel() < 10) {
				dialog.getVisualPanel().showImagePortion("illustrations", "raid_covert_result", 640, 400, 0, 0, 480, 300);
			} else {
				dialog.getVisualPanel().showImagePortion("illustrations", "raid_valuables_result", 640, 400, 0, 0, 480, 300);
			}
		} else if (temp.raidType == RaidType.DISRUPT) {
			dialog.getVisualPanel().showImagePortion("illustrations", "raid_disrupt_result", 640, 400, 0, 0, 480, 300);
		}
		
		boolean withContinue = false;
		
		for (GroundRaidObjectivePlugin curr : temp.objectives) {
			if (curr.withContinueBeforeResult()) {
				withContinue = true;
				break;
			}
		}
		
//		if (market.getMemoryWithoutUpdate().getBoolean("$raid_showContinueBeforeResult"))
//		withContinue = true;
		
		if (withContinue) {
			options.clearOptions();
			options.addOption(StringHelper.getString("continue", true), RAID_CONFIRM_CONTINUE);
		} else {
			raidConfirmContinue();
		}
	}
	
	@Override
	public void doGenericRaid(FactionAPI faction, float attackerStr, float maxPenalty) {
		super.doGenericRaid(faction, attackerStr, maxPenalty);
		NexUtilsMarket.reportNPCGenericRaid(market, temp);
	}
	
	@Override
	public boolean doIndustryRaid(FactionAPI faction, float attackerStr, Industry industry, float durMult) {
		boolean result = super.doIndustryRaid(faction, attackerStr, industry, durMult);
		NexUtilsMarket.reportNPCIndustryRaid(market, temp, industry);
		return result;
	}
	
	@Override
	public void doBombardment(FactionAPI faction, BombardType type) {
		super.doBombardment(faction, type);
		if (type == BombardType.TACTICAL) {
			NexUtilsMarket.reportNPCTacticalBombardment(market, temp);
		} else {
			NexUtilsMarket.reportNPCSaturationBombardment(market, temp);
		}
	}
	
	/**
	 * Like {@code getNumPicks}, but with stronger falloff effect.
	 * This allows a higher initial pMore to start with.
	 * @param random
	 * @param pAny
	 * @param pMore
	 * @param diminishFactor
	 * @return
	 */
	protected int getNumPicksDiminishing(Random random, float pAny, float pMore, 
			float diminishFactor) 
	{
		if (random.nextFloat() >= pAny) return 0;
		
		int result = 1;
		for (int i = 0; i < 10; i++) {
			if (random.nextFloat() >= pMore) break;
			result++;
			pMore *= diminishFactor;
		}
		return result;
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
	
	public static void applyDefenderIncreaseFromRaid(MarketAPI market, float mult) {
		float e = market.getMemoryWithoutUpdate().getExpire(DEFENDER_INCREASE_KEY);
		e += getRaidDefenderIncreasePerRaid() * mult;
		float max = getRaidDefenderIncreaseMax();
		if (e > max) e = max;
		
		market.getMemoryWithoutUpdate().set(DEFENDER_INCREASE_KEY, true);
		market.getMemoryWithoutUpdate().expire(DEFENDER_INCREASE_KEY, e);
	}
	
	public static Set<String> getEverRaidedBlueprints() {
		Map<String, Object> persistent = Global.getSector().getPersistentData();
		if (!persistent.containsKey(DATA_KEY_BPS_ALREADY_RAIDED))
		{
			persistent.put(DATA_KEY_BPS_ALREADY_RAIDED, new HashSet<String>());
		}
		return (Set<String>)persistent.get(DATA_KEY_BPS_ALREADY_RAIDED);
	}
	
	public static void addEverRaidedBlueprint(String bp) {
		log.info("Adding ever-raided blueprint: " + bp);
		getEverRaidedBlueprints().add(bp);
	}
	
	public static class TempDataInvasion {
		public boolean canInvade;
		public int marinesLost = 0;
		public int mechsLost = 0;
		public int roundNum = 0;
		public float stabilityPenalty = 0;
		public boolean success = false;
		public boolean tookForSelf = true;
		
		public float invasionMult;
		public float shortageMult;
		
		public float attackerStr;
		public float defenderStr;
		
		public Map<CommodityOnMarketAPI, Float> invasionValuables;
		public CargoAPI invasionLoot;
		public int invasionCredits;
	}
}
