package com.github.retro_game.retro_game.integration;

import com.github.retro_game.retro_game.repository.ItemDefinitionRepository;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

public class ApplicationSmokeIntegrationTest extends IntegrationTest {
  @Autowired
  private ItemDefinitionRepository itemDefinitionRepository;
  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  public void startsAndSeedsBuiltInCatalog() {
    assertThat(itemDefinitionRepository.count()).isEqualTo(57);
    assertThat(itemDefinitionRepository.findByKind("METAL_MINE")).isPresent();
    assertThat(itemDefinitionRepository.findByKind("BATTLESHIP")).isPresent();
    assertThat(itemDefinitionRepository.findByKind("ASTROPHYSICS")).isPresent();
  }

  @Test
  public void servesSignInPage() {
    var response = restTemplate.getForEntity("/", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("Sign in - Retro Game");
  }
}
