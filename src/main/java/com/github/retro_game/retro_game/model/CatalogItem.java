package com.github.retro_game.retro_game.model;

import com.github.retro_game.retro_game.entity.Body;
import com.github.retro_game.retro_game.entity.BuildingKind;
import com.github.retro_game.retro_game.entity.ItemDefinition;
import com.github.retro_game.retro_game.entity.ItemType;
import com.github.retro_game.retro_game.entity.Resources;
import com.github.retro_game.retro_game.entity.TechnologyKind;
import com.github.retro_game.retro_game.entity.UnitKind;
import com.github.retro_game.retro_game.entity.UnitType;
import com.github.retro_game.retro_game.entity.User;
import com.github.retro_game.retro_game.model.building.BuildingItem;
import com.github.retro_game.retro_game.model.unit.UnitItem;
import com.github.retro_game.retro_game.service.CatalogService;
import org.springframework.lang.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A unified, kind-keyed view of one catalog item — the seam the data-driven
 * content rebuild's later stages code against, in place of the {@code *Kind}
 * enums and the hardcoded {@code BuildingItem}/{@code TechnologyItem}/{@code
 * UnitItem} classes.
 *
 * <p>It joins the two halves of an item's definition:
 * <ul>
 *   <li><b>Data</b> — cost, cost factor, required energy, cargo capacity,
 *       combat stats and build requirements — read from the content catalog
 *       ({@link CatalogService}), so it reflects admin-panel edits.</li>
 *   <li><b>Behavior</b> — special build requirements, propulsion, fuel
 *       consumption and rapid fire — which is still expressed in code, with no
 *       catalog representation. For a built-in item this is delegated to its
 *       legacy model class; an admin-created item has no model class, so it
 *       falls back to neutral defaults (no special requirement, no drive, no
 *       rapid fire). Making that behavior data-driven too is future work.</li>
 * </ul>
 *
 * <p>Instances are cheap, immutable wrappers; build them with the factory
 * methods rather than caching them, so catalog edits are always picked up.
 */
public class CatalogItem {
  private final ItemDefinition definition;
  // The legacy model object, present only for built-in items; null for an item
  // created through the admin panel (it has no enum constant, hence no class).
  @Nullable
  private final Item legacy;

  private CatalogItem(ItemDefinition definition, @Nullable Item legacy) {
    this.definition = definition;
    this.legacy = legacy;
  }

  /** Wraps a catalog definition, resolving its legacy behavior if it is a built-in item. */
  public static CatalogItem of(ItemDefinition definition) {
    return new CatalogItem(definition, resolveLegacy(definition));
  }

  /** Returns the catalog item with the given kind, e.g. {@code "METAL_MINE"}. */
  public static CatalogItem of(String kind) {
    return of(CatalogService.getInstance().getDefinition(kind));
  }

  /** Returns every catalog item of the given type, ordered by id (seed order). */
  public static List<CatalogItem> allOfType(ItemType type) {
    return CatalogService.getInstance().getAllByType(type).stream().map(CatalogItem::of).toList();
  }

  @Nullable
  private static Item resolveLegacy(ItemDefinition definition) {
    // Built-in items share their kind with an enum constant; admin-created ones
    // do not, so valueOf throws and there is no legacy behavior to delegate to.
    try {
      return switch (definition.getType()) {
        case BUILDING -> Item.get(BuildingKind.valueOf(definition.getKind()));
        case TECHNOLOGY -> Item.get(TechnologyKind.valueOf(definition.getKind()));
        case UNIT -> Item.get(UnitKind.valueOf(definition.getKind()));
      };
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  // --- Identity and data, from the content catalog ---

  public String getKind() {
    return definition.getKind();
  }

  public ItemType getType() {
    return definition.getType();
  }

  public String getName() {
    return definition.getName();
  }

  /** The level-1 cost; the cost at higher levels grows by {@link #getCostFactor()}. */
  public Resources getBaseCost() {
    return new Resources(definition.getMetalCost(), definition.getCrystalCost(), definition.getDeuteriumCost());
  }

  /** The per-level cost multiplier. Always 1 for units, which have a flat cost. */
  public double getCostFactor() {
    return definition.getCostFactor();
  }

  /** The energy a building requires at level 1; 0 for technologies and units. */
  public int getBaseEnergy() {
    return definition.getBaseEnergy();
  }

  /** Whether a unit belongs to the fleet or the defense; null for non-units. */
  @Nullable
  public UnitType getUnitType() {
    return definition.getUnitType();
  }

  public long getCapacity() {
    return definition.getCapacity();
  }

  public double getWeapons() {
    return definition.getWeapons();
  }

  public double getShield() {
    return definition.getShield();
  }

  public double getArmor() {
    return definition.getArmor();
  }

  /**
   * The buildings, with their levels, required before this item can be built,
   * read from the content catalog. A required kind that is not a built-in
   * {@link BuildingKind} (a future admin-created building) is skipped.
   */
  public Map<BuildingKind, Integer> getBuildingsRequirements() {
    var requirements = new EnumMap<BuildingKind, Integer>(BuildingKind.class);
    for (var req : CatalogService.getInstance().getRequirements(getKind())) {
      if (req.requiredType() != ItemType.BUILDING) {
        continue;
      }
      try {
        requirements.put(BuildingKind.valueOf(req.requiredKind()), req.requiredLevel());
      } catch (IllegalArgumentException e) {
        // Not a built-in building kind; skip it.
      }
    }
    return requirements;
  }

  /**
   * The technologies, with their levels, required before this item can be
   * built, read from the content catalog. A required kind that is not a
   * built-in {@link TechnologyKind} (a future admin-created technology) is
   * skipped.
   */
  public Map<TechnologyKind, Integer> getTechnologiesRequirements() {
    var requirements = new EnumMap<TechnologyKind, Integer>(TechnologyKind.class);
    for (var req : CatalogService.getInstance().getRequirements(getKind())) {
      if (req.requiredType() != ItemType.TECHNOLOGY) {
        continue;
      }
      try {
        requirements.put(TechnologyKind.valueOf(req.requiredKind()), req.requiredLevel());
      } catch (IllegalArgumentException e) {
        // Not a built-in technology kind; skip it.
      }
    }
    return requirements;
  }

  // --- Behavior, still expressed in code (delegated to the legacy model) ---

  /** Whether a building may be constructed on the given body (e.g. moon-only buildings). */
  public boolean meetsSpecialRequirements(Body body) {
    return !(legacy instanceof BuildingItem building) || building.meetsSpecialRequirements(body);
  }

  /** A unit's deuterium consumption for the given user's drive technology levels. */
  public int getConsumption(User user) {
    return legacy instanceof UnitItem unit ? unit.getConsumption(user) : 0;
  }

  /** The drive technology a unit currently flies on for the given user; null if it has none. */
  @Nullable
  public TechnologyKind getDrive(User user) {
    return legacy instanceof UnitItem unit ? unit.getDrive(user) : null;
  }

  /** A unit's base speed for the given user's drive technology levels. */
  public int getBaseSpeed(User user) {
    return legacy instanceof UnitItem unit ? unit.getBaseSpeed(user) : 0;
  }

  /** How many extra shots this unit gets against each other unit kind. */
  public Map<UnitKind, Integer> getRapidFireAgainst() {
    return legacy instanceof UnitItem unit ? unit.getRapidFireAgainst() : Map.of();
  }
}
