package data.scripts.world.systems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin.MagneticFieldParams;

public class Duzahk {

	public void generate(SectorAPI sector) {
		
		StarSystemAPI system = sector.createStarSystem("Duzahk");
		LocationAPI hyper = Global.getSector().getHyperspace();
		
		system.setBackgroundTextureFilename("graphics/backgrounds/background1.jpg");
		
		// create the star and generate the hyperspace anchor for this system
		PlanetAPI duzahk_star = system.initStar("duzahk", // unique id for this star 
										    "star_orange",  // id in planets.json
										    600f, 		  // radius (in pixels at default zoom)
										    500); // corona radius, from star edge
		duzahk_star.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "banded"));
		duzahk_star.getSpec().setGlowColor(new Color(255,235,50,128));
		duzahk_star.getSpec().setAtmosphereThickness(0.5f);
		duzahk_star.applySpecChanges();
		
		system.setLightColor(new Color(255, 240, 220)); // light color in entire system, affects all entities
		

		// Hot Jupiter, stark blue.
		PlanetAPI duzahk1 = system.addPlanet("duzahk1", duzahk_star, "Aka Mainyu", "gas_giant", 0, 230, 1550, 24);
		duzahk1.getSpec().setPlanetColor(new Color(16,16,255,255));
		duzahk1.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "banded"));
		duzahk1.getSpec().setGlowColor(new Color(235,38,8,145));
		duzahk1.getSpec().setUseReverseLightForGlow(true);
		duzahk1.getSpec().setAtmosphereThickness(0.5f);
		duzahk1.getSpec().setCloudRotation( 15f );
		duzahk1.getSpec().setAtmosphereColor(new Color(138,118,255,245));
		duzahk1.getSpec().setPitch( -5f );
		duzahk1.getSpec().setTilt( 30f );
		duzahk1.applySpecChanges();
		duzahk1.setCustomDescriptionId("planet_aka_mainyu");
		
		system.addCorona(duzahk1, Terrain.CORONA_AKA_MAINYU,
						300f, // radius outside planet
						5f, // burn level of "wind"
						0f, // flare probability
						1f // CR loss mult while in it
						);
		
		// duzahk_star magnetic field
			SectorEntityToken field = system.addTerrain(Terrain.MAGNETIC_FIELD,
			new MagneticFieldParams(500f, // terrain effect band width 
					2000, // terrain effect middle radius
					duzahk_star, // entity that it's around
					1750f, // visual band start
					2250f, // visual band end
					new Color(50, 20, 100, 40), // base color
					1f, // probability to spawn aurora sequence, checked once/day when no aurora in progress
					new Color(50, 20, 110, 130),
					new Color(150, 30, 120, 150), 
					new Color(200, 50, 130, 190),
					new Color(250, 70, 150, 240),
					new Color(200, 80, 130, 255),
					new Color(75, 0, 160), 
					new Color(127, 0, 255)
					));
			field.setCircularOrbit(duzahk_star, 0, 0, 150);
		
		// Asteroid belt!
		system.addRingBand(duzahk_star, "misc", "rings1", 256f, 2, Color.white, 256f, 2420, 34f, null, null);
		system.addAsteroidBelt(duzahk_star, 50, 2400, 100, 30, 40, Terrain.ASTEROID_BELT, "The Daevas");
		
		//system.addRingBand(duzahk_star, "misc", "rings3", 256, 2, new Color(255,245,235,255), 256, 2500, 90f);
		//system.addRingBand(duzahk_star, "misc", "rings4", 512f, 1, new Color(235,38,8,145), 512f, 2500, 90f);
		//system.addRingBand(duzahk_star, "misc", "rings4", 512f, 1, new Color(200,190,140,200), 512f, 2500, 120f);
		//system.addRingBand(duzahk_star, "misc", "rings5", 1024f, 0, new Color(200,190,170,250), 1024f, 2800, 140f);
		
		// Lies!
		PlanetAPI duzahk2 = system.addPlanet("duzahk2", duzahk_star, "Druj", "barren", 130, 60, 4200, 135);
		duzahk2.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "aurorae"));
		duzahk2.getSpec().setGlowColor(new Color(20,255,48,125));
		duzahk2.getSpec().setUseReverseLightForGlow(true);
		duzahk2.applySpecChanges();
		duzahk2.setCustomDescriptionId("planet_druj");
		
		// Druj Relay - L5 (behind)
		SectorEntityToken druj_relay = system.addCustomEntity("druj_relay", // unique id
				 "Druj Relay", // name - if null, defaultName from custom_entities.json will be used
				 "comm_relay", // type of object, defined in custom_entities.json
				 "pirates"); // faction
		druj_relay.setCircularOrbit( duzahk_star, 130 - 60, 4200, 135);
		
		//JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint("penelope_jump_point_alpha", "Penelope's Star Inner System Jump");
		//OrbitAPI orbit = Global.getFactory().createCircularOrbit(penelope_star, 0, 800, 45);
		//jumpPoint.setOrbit(orbit);
		//jumpPoint.setRelatedPlanet(penelope_star);
		//jumpPoint.setStandardWormholeToHyperspaceVisual();
		//system.addEntity(jumpPoint);

		system.autogenerateHyperspaceJumpPoints(true, true);
	}
}
