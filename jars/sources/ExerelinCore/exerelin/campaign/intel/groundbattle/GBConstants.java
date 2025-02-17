package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker.PersonnelData;

public class GBConstants {
	
	public static final String TAG_PREVENT_BOMBARDMENT = "preventBombardment";
	public static final String TAG_PREVENT_EW = "preventEW";
	public static final String TAG_PREVENT_INSPIRE = "preventInspire";
	public static final String MEMKEY_GARRISON_DAMAGE = "$nex_garrisonDamage";
	public static final String MEMKEY_AWAIT_DECISION = "$nex_gbAwaitDecision";
	
	public static final String ACTION_MOVE = "move";
	public static final String ACTION_WITHDRAW = "withdraw";
	
	public static int BASE_MOVEMENT_POINTS_PER_TURN = 10;
	public static float TURN_1_MOVE_POINT_MULT = 2;
	public static float HEAVY_DROP_COST_MULT = 1.25f;
	public static float FLEET_SUPPORT_MOVEMENT_MULT = 0.5f;
	
	public static float BASE_MORALE = 0.8f;
	public static float BASE_DAMAGE_MULT = 0.1f;
	public static float MORALE_ATTACK_MOD = 0.15f;
	public static float MORALE_DAMAGE_FACTOR = 0.7f;	// 70% losses = 100% morale loss
	public static float DEFENDER_MORALE_DMG_MULT = 0.9f;
	public static float MORALE_LOSS_FROM_COMBAT = 0.05f;
	public static float MORALE_RECOVERY_OUT_OF_COMBAT = 0.025f;
	public static float REORGANIZE_AT_MORALE = 0.3f;
	public static float BREAK_AT_MORALE = 0.01f;
	public static float HEAVY_OFFENSIVE_MULT = 1.25f;
	public static float HEAVY_STATION_MULT = 0.75f;
	public static float XP_MORALE_BONUS = 0.2f;	// 20% more morale at 100% XP
	public static float CAPTURE_MORALE = 0.08f;
	public static float REORGANIZING_DMG_MULT = 0.7f;
	public static float REBEL_DAMAGE_MULT = 0.5f;	// both dealt and received;
	
	public static int STABILITY_PENALTY_BASE = 2;
	public static int STABILITY_PENALTY_OCCUPATION = 5;
	public static float DISRUPTED_TROOP_CONTRIB_MULT = 0.5f;
	public static float DISRUPT_WHEN_CAPTURED_TIME = 0.25f;
	
	public static float SUPPLIES_TO_DEPLOY_MULT = 0.25f;
	public static float MAX_SUPPORT_DIST = 250;
	
	public static float XP_MARKET_SIZE_MULT = 4f;
	public static float XP_CASUALTY_MULT = 0.15f;
	
	public static float BASE_GARRISON_SIZE = 25;
	public static float EXTERNAL_BOMBARDMENT_DAMAGE = 0.6f;
	public static float INVASION_HEALTH_MONTHS_TO_RECOVER = 3;
	public static float LIBERATION_REBEL_MULT = 0.25f;
	
	public static PersonnelData DEFENSE_STAT = new PersonnelData("generic_defender");
	static {
		DEFENSE_STAT.num = 100;
		DEFENSE_STAT.xp = 25;
	}
}
