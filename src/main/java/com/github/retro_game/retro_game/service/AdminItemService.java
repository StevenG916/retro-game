package com.github.retro_game.retro_game.service;

import com.github.retro_game.retro_game.controller.form.AdminItemForm;
import com.github.retro_game.retro_game.entity.ItemDefinition;
import com.github.retro_game.retro_game.entity.ItemRequirement;
import com.github.retro_game.retro_game.repository.ItemDefinitionRepository;
import com.github.retro_game.retro_game.repository.ItemRequirementRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read/update operations on the content catalog, used by the admin panel.
 *
 * <p>Phase 1 supports listing items and editing an existing item's values;
 * creating brand-new items arrives in a later phase, once player state no
 * longer depends on the fixed enum set.
 */
@Service
public class AdminItemService {
  private final ItemDefinitionRepository itemDefinitionRepository;
  private final ItemRequirementRepository itemRequirementRepository;
  private final CatalogService catalogService;

  public AdminItemService(ItemDefinitionRepository itemDefinitionRepository,
                          ItemRequirementRepository itemRequirementRepository,
                          CatalogService catalogService) {
    this.itemDefinitionRepository = itemDefinitionRepository;
    this.itemRequirementRepository = itemRequirementRepository;
    this.catalogService = catalogService;
  }

  @Transactional(readOnly = true)
  public List<ItemDefinition> getAllItems() {
    return itemDefinitionRepository.findAll(Sort.by("type", "name"));
  }

  @Transactional(readOnly = true)
  public ItemDefinition getItem(long id) {
    return itemDefinitionRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("No catalog item with id " + id));
  }

  @Transactional(readOnly = true)
  public List<ItemRequirement> getRequirements(ItemDefinition item) {
    return itemRequirementRepository.findByItem(item);
  }

  /**
   * Applies the edited values to an existing item. Only the fields relevant to
   * the item's {@link com.github.retro_game.retro_game.entity.ItemType} are
   * touched, so the form's unused fields cannot clobber type-specific data.
   */
  @Transactional
  public void updateItem(AdminItemForm form) {
    var item = itemDefinitionRepository.findById(form.getId())
        .orElseThrow(() -> new IllegalArgumentException("No catalog item with id " + form.getId()));
    item.setName(form.getName());
    item.setMetalCost(form.getMetalCost());
    item.setCrystalCost(form.getCrystalCost());
    item.setDeuteriumCost(form.getDeuteriumCost());
    switch (item.getType()) {
      case BUILDING -> {
        item.setCostFactor(form.getCostFactor());
        item.setBaseEnergy(form.getBaseEnergy());
      }
      case TECHNOLOGY -> item.setCostFactor(form.getCostFactor());
      case UNIT -> {
        item.setCapacity(form.getCapacity());
        item.setWeapons(form.getWeapons());
        item.setShield(form.getShield());
        item.setArmor(form.getArmor());
      }
    }
    itemDefinitionRepository.save(item);
    // Refresh the in-memory catalog so the edit takes effect on the running game.
    catalogService.reload();
  }
}
