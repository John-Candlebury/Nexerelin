package exerelin.world.landmarks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.ExerelinUtilsAstro;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class BeholderStation extends BaseLandmarkDef {

	public BeholderStation(Random random) {
		super(random);
	}
	
	@Override
	public List<SectorEntityToken> getRandomLocations() {
		WeightedRandomPicker<SectorEntityToken> picker = new WeightedRandomPicker<>(random);
		
		Set<StarSystemAPI> luddicSystems = new HashSet<>();
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
		{
			if (ExerelinUtilsFaction.isLuddicFaction(market.getFactionId()))
				luddicSystems.add(market.getStarSystem());
		}
		
		for (StarSystemAPI system : luddicSystems)
		{
			for (PlanetAPI planet : system.getPlanets())
			{
				if (!planet.isGasGiant()) continue;
				picker.add(planet);
			}
		}
		List<SectorEntityToken> results = new ArrayList<>();
		int count = getCount();
		for (int i=0; i<count; i++)
		{
			if (picker.isEmpty()) break;
			results.add(picker.pickAndRemove());
		}
		
		return results;
	}
		
	@Override
	public void createAt(SectorEntityToken entity)
	{
		float orbitRadius = entity.getRadius() + 200;
		float orbitPeriod = ExerelinUtilsAstro.getOrbitalPeriod(entity, orbitRadius);
		SectorEntityToken beholder_station = entity.getContainingLocation().addCustomEntity("beholder_station", 
				"Beholder Station", "station_side05", Factions.LUDDIC_CHURCH);
		beholder_station.setCircularOrbitPointingDown(entity, ExerelinUtilsAstro.getRandomAngle(random), orbitRadius, orbitPeriod);		
		beholder_station.setCustomDescriptionId("station_beholder");
		beholder_station.setInteractionImage("illustrations", "luddic_shrine");
		beholder_station.addTag("luddicShrine");
		
		log.info("Spawning Beholder Station around " + entity.getName() + ", " + entity.getContainingLocation().getName());
	}
}
