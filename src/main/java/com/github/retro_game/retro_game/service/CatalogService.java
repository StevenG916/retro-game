package com.github.retro_game.retro_game.service;

import com.github.retro_game.retro_game.entity.ItemDefinition;
import com.github.retro_game.retro_game.entity.ItemType;
import com.github.retro_game.retro_game.repository.ItemDefinitionRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory view of the content catalog, used by the game to read item
 * definitions (costs, cost factors, required energy, ...) at runtime.
 *
 * <p>Part of phase 2 of the data-driven content rebuild. The catalog is loaded
 * from the database on first use and refreshed whenever the admin panel edits
 * an item, so edits take effect on the running game.
 *
 * <p>A static accessor is exposed because some long-standing callers — notably
 * {@code ItemCostUtils} — are static utility classes; routing them through this
 * bean avoids converting them, and their many call sites, to Spring beans.
 */
@Component
public class CatalogService {
  private static volatile CatalogService instance;

  private final ItemDefinitionRepository itemDefinitionRepository;
  private volatile Map<String, ItemDefinition> definitionsByKind = Map.of();

  public CatalogService(ItemDefinitionRepository itemDefinitionRepository) {
    this.itemDefinitionRepository = itemDefinitionRepository;
    instance = this;
  }

  /** Returns the singleton instance, for use by static utility code such as ItemCostUtils. */
  public static CatalogService getInstance() {
    return instance;
  }

  /** (Re)loads the catalog from the database. Runs on first use and after an admin edit. */
  public synchronized void reload() {
    var map = new HashMap<String, ItemDefinition>();
    for (var definition : itemDefinitionRepository.findAll()) {
      map.put(definition.getKind(), definition);
    }
    definitionsByKind = Map.copyOf(map);
  }

  /** Returns the catalog definition for the given item kind, e.g. {@code "METAL_MINE"}. */
  public ItemDefinition getDefinition(String kind) {
    var definition = definitions().get(kind);
    if (definition == null) {
      throw new IllegalStateException("The content catalog has no item with kind " + kind);
    }
    return definition;
  }

  /** Returns whether the catalog contains an item with the given kind. */
  public boolean hasDefinition(String kind) {
    return definitions().containsKey(kind);
  }

  /** Returns every catalog item, ordered by id — i.e. the order they were seeded in. */
  public List<ItemDefinition> getAll() {
    return definitions().values().stream()
        .sorted(Comparator.comparingLong(ItemDefinition::getId))
        .toList();
  }

  /** Returns every catalog item of the given type, ordered by id. */
  public List<ItemDefinition> getAllByType(ItemType type) {
    return getAll().stream().filter(definition -> definition.getType() == type).toList();
  }

  /** Returns the catalog map, loading it from the database on first use. */
  private Map<String, ItemDefinition> definitions() {
    var map = definitionsByKind;
    if (map.isEmpty()) {
      reload();
      map = definitionsByKind;
    }
    return map;
  }
}
