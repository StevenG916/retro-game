package com.github.retro_game.retro_game.repository;

import com.github.retro_game.retro_game.entity.ItemDefinition;
import com.github.retro_game.retro_game.entity.ItemRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemRequirementRepository extends JpaRepository<ItemRequirement, Long> {
  List<ItemRequirement> findByItem(ItemDefinition item);
}
