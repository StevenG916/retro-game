package com.github.retro_game.retro_game.unit;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class TemplateSyntaxTest {
  private static final Pattern UNWRAPPED_FRAGMENT = Pattern.compile(
      "th:(?:replace|insert|include)\\s*=\\s*\"(?!~\\{)"
  );

  @Test
  public void allFragmentExpressionsUseWrappedSyntax() throws IOException {
    var templatesRoot = Path.of("src", "main", "resources", "templates");
    var offenders = new ArrayList<String>();

    try (var paths = Files.walk(templatesRoot)) {
      for (var path : paths.filter(path -> path.toString().endsWith(".html")).sorted().toList()) {
        var lines = Files.readAllLines(path);
        for (var i = 0; i < lines.size(); i++) {
          if (UNWRAPPED_FRAGMENT.matcher(lines.get(i)).find()) {
            offenders.add(templatesRoot.relativize(path) + ":" + (i + 1));
          }
        }
      }
    }

    Assert.assertTrue(
        "Deprecated unwrapped Thymeleaf fragment expressions found at: " + String.join(", ", offenders),
        offenders.isEmpty()
    );
  }
}
