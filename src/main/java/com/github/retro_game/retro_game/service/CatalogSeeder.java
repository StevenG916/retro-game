package com.github.retro_game.retro_game.service;

import com.github.retro_game.retro_game.entity.ItemDefinition;
import com.github.retro_game.retro_game.entity.ItemRequirement;
import com.github.retro_game.retro_game.entity.ItemType;
import com.github.retro_game.retro_game.entity.Resources;
import com.github.retro_game.retro_game.entity.UnitType;
import com.github.retro_game.retro_game.model.Item;
import com.github.retro_game.retro_game.model.building.BuildingItem;
import com.github.retro_game.retro_game.model.technology.TechnologyItem;
import com.github.retro_game.retro_game.model.unit.UnitItem;
import com.github.retro_game.retro_game.repository.ItemDefinitionRepository;
import com.github.retro_game.retro_game.repository.ItemRequirementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.MessageSource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Populates the content catalog ({@code item_definitions} / {@code item_requirements})
 * from the legacy hardcoded item classes, the first time the application starts
 * against an empty catalog.
 *
 * <p>This is part of phase 1 of the data-driven content rebuild: the catalog
 * tables exist and are filled, but the game still computes from the hardcoded
 * {@code model/} classes. Seeding from those classes — rather than a hand-written
 * SQL script — keeps the catalog automatically in step with the current game
 * values until a later phase makes the catalog authoritative.
 */
@Component
public class CatalogSeeder {
  private static final Logger logger = LoggerFactory.getLogger(CatalogSeeder.class);

  private final ItemDefinitionRepository itemDefinitionRepository;
  private final ItemRequirementRepository itemRequirementRepository;
  private final MessageSource messageSource;

  public CatalogSeeder(ItemDefinitionRepository itemDefinitionRepository,
                       ItemRequirementRepository itemRequirementRepository,
                       MessageSource messageSource) {
    this.itemDefinitionRepository = itemDefinitionRepository;
    this.itemRequirementRepository = itemRequirementRepository;
    this.messageSource = messageSource;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void seedCatalog() {
    if (itemDefinitionRepository.count() > 0) {
      logger.info("Content catalog already populated; skipping seed.");
      return;
    }
    logger.info("Seeding the content catalog from the hardcoded item definitions...");

    // Pass 1: every item definition. Indexed by kind so pass 2 can resolve requirements.
    var definitions = new ArrayList<ItemDefinition>();
    BuildingItem.getAll().forEach((kind, item) -> {
      var def = newDefinition(ItemType.BUILDING, kind.name());
      applyCost(def, item.getBaseCost());
      def.setCostFactor(item.getCostFactor());
      def.setBaseEnergy(item.getBaseRequiredEnergy());
      definitions.add(def);
    });
    TechnologyItem.getAll().forEach((kind, item) -> {
      var def = newDefinition(ItemType.TECHNOLOGY, kind.name());
      applyCost(def, item.getBaseCost());
      def.setCostFactor(item.getCostFactor());
      def.setBaseEnergy(item.getBaseRequiredEnergy());
      definitions.add(def);
    });
    UnitItem.getFleet().forEach((kind, item) ->
        definitions.add(newUnitDefinition(kind.name(), UnitType.FLEET, item)));
    UnitItem.getDefense().forEach((kind, item) ->
        definitions.add(newUnitDefinition(kind.name(), UnitType.DEFENSE, item)));

    var byKind = new HashMap<String, ItemDefinition>();
    for (var def : itemDefinitionRepository.saveAll(definitions)) {
      byKind.put(def.getKind(), def);
    }

    // Pass 2: requirements, now that every definition has a persisted id.
    var requirements = new ArrayList<ItemRequirement>();
    BuildingItem.getAll().forEach((kind, item) -> collectRequirements(byKind, kind.name(), item, requirements));
    TechnologyItem.getAll().forEach((kind, item) -> collectRequirements(byKind, kind.name(), item, requirements));
    UnitItem.getAll().forEach((kind, item) -> collectRequirements(byKind, kind.name(), item, requirements));
    itemRequirementRepository.saveAll(requirements);

    logger.info("Seeded {} item definitions and {} requirements.", definitions.size(), requirements.size());
  }

  private ItemDefinition newDefinition(ItemType type, String kind) {
    var def = new ItemDefinition();
    def.setType(type);
    def.setKind(kind);
    def.setName(resolveName(kind));
    // Units override this with a flat cost; buildings and technologies set their own factor.
    def.setCostFactor(1.0);
    return def;
  }

  private ItemDefinition newUnitDefinition(String kind, UnitType unitType, UnitItem item) {
    var def = newDefinition(ItemType.UNIT, kind);
    def.setUnitType(unitType);
    applyCost(def, item.getCost());
    def.setCapacity(item.getCapacity());
    def.setWeapons(item.getBaseWeapons());
    def.setShield(item.getBaseShield());
    def.setArmor(item.getBaseArmor());
    return def;
  }

  private static void applyCost(ItemDefinition def, Resources cost) {
    def.setMetalCost(cost.getMetal());
    def.setCrystalCost(cost.getCrystal());
    def.setDeuteriumCost(cost.getDeuterium());
  }

  private void collectRequirements(Map<String, ItemDefinition> byKind, String kind, Item item,
                                   List<ItemRequirement> out) {
    var def = byKind.get(kind);
    // A building/technology prerequisite can be either another building or a technology.
    var required = new HashMap<String, Integer>();
    item.getBuildingsRequirements().forEach((reqKind, level) -> required.put(reqKind.name(), level));
    item.getTechnologiesRequirements().forEach((reqKind, level) -> required.put(reqKind.name(), level));
    required.forEach((reqKind, level) -> {
      var requiredItem = byKind.get(reqKind);
      if (requiredItem == null) {
        logger.warn("Skipping requirement of {} on unknown item {}.", kind, reqKind);
        return;
      }
      var requirement = new ItemRequirement();
      requirement.setItem(def);
      requirement.setRequiredItem(requiredItem);
      requirement.setRequiredLevel(level);
      out.add(requirement);
    });
  }

  private String resolveName(String kind) {
    // Item display names live in the i18n bundles as items.<KIND>.name; fall back to the kind.
    return messageSource.getMessage("items." + kind + ".name", null, kind, Locale.ENGLISH);
  }
}
