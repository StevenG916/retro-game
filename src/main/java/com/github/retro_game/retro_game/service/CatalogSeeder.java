package com.github.retro_game.retro_game.service;

import com.github.retro_game.retro_game.entity.BuildingKind;
import com.github.retro_game.retro_game.entity.ItemDefinition;
import com.github.retro_game.retro_game.entity.ItemRequirement;
import com.github.retro_game.retro_game.entity.ItemType;
import com.github.retro_game.retro_game.entity.Resources;
import com.github.retro_game.retro_game.entity.TechnologyKind;
import com.github.retro_game.retro_game.entity.UnitKind;
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
import java.util.HashSet;
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
 *
 * <p>On every start it also verifies the catalog defines every built-in item,
 * failing fast otherwise: the game still resolves those items by their enum
 * name, so a missing row would otherwise surface as a random error mid-game.
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
    } else {
      seedFromHardcodedItems();
    }
    // Whether freshly seeded or pre-existing, the catalog must define every
    // built-in item; the game still resolves those by their enum name.
    verifyCatalogComplete();
  }

  private void seedFromHardcodedItems() {
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

  /**
   * Asserts the catalog has a definition for every built-in item. The game still
   * resolves buildings, technologies and units by their enum name, so a missing
   * row is a fatal misconfiguration — better caught here, at start-up, than as a
   * random failure once a player reaches that item.
   */
  private void verifyCatalogComplete() {
    var existingKinds = new HashSet<String>();
    for (var definition : itemDefinitionRepository.findAll()) {
      existingKinds.add(definition.getKind());
    }

    var missing = new ArrayList<String>();
    for (var kind : BuildingKind.values()) {
      if (!existingKinds.contains(kind.name())) {
        missing.add(kind.name());
      }
    }
    for (var kind : TechnologyKind.values()) {
      if (!existingKinds.contains(kind.name())) {
        missing.add(kind.name());
      }
    }
    for (var kind : UnitKind.values()) {
      if (!existingKinds.contains(kind.name())) {
        missing.add(kind.name());
      }
    }

    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "The content catalog is missing definitions for: " + String.join(", ", missing)
              + ". Every built-in BuildingKind, TechnologyKind and UnitKind must have a matching "
              + "item_definitions row.");
    }
    var builtInCount = BuildingKind.values().length + TechnologyKind.values().length
        + UnitKind.values().length;
    logger.info("Content catalog verified: all {} built-in items are present.", builtInCount);
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
