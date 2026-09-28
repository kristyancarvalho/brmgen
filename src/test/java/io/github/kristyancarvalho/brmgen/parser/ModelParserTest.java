package io.github.kristyancarvalho.brmgen.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kristyancarvalho.brmgen.model.Cardinality;
import io.github.kristyancarvalho.brmgen.model.ConceptualModel;
import io.github.kristyancarvalho.brmgen.model.LogicalModel;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelParserTest {
  private final ModelParser parser = new ModelParser();

  @TempDir Path temporaryDirectory;

  @Test
  void parsesEquivalentYamlAndJsonModels() throws Exception {
    ConceptualModel yaml = (ConceptualModel) parser.parse(fixture("complete-model.yaml"));
    ConceptualModel json = (ConceptualModel) parser.parse(fixture("complete-model.json"));

    assertThat(yaml).isEqualTo(json);
    assertThat(yaml.version()).isEqualTo(1);
    assertThat(yaml.diagram().name()).isEqualTo("Commerce");
    assertThat(yaml.entities()).hasSize(3);
    assertThat(yaml.entities().get(0).attributes().get(1).components()).hasSize(2);
    assertThat(yaml.entities().get(2).weak()).isTrue();
    assertThat(yaml.relationships().getFirst().connections().getFirst().cardinality())
        .isEqualTo(Cardinality.ONE);
    assertThat(yaml.generalizations().getFirst().disjoint()).isTrue();
  }

  @Test
  void parsesEquivalentLogicalYamlAndJsonModels() throws Exception {
    LogicalModel yaml = (LogicalModel) parser.parse(fixture("logical-model.yaml"));
    LogicalModel json = (LogicalModel) parser.parse(fixture("logical-model.json"));

    assertThat(yaml).isEqualTo(json);
    assertThat(yaml.diagram().name()).isEqualTo("Library");
    assertThat(yaml.tables()).hasSize(2);
    assertThat(yaml.tables().get(1).primaryKeyColumns()).containsExactly("id");
    assertThat(yaml.tables().get(1).foreignKeys().getFirst().references().table())
        .isEqualTo("Author");
  }

  @Test
  void parsesExplicitConceptualModelMetadata() throws Exception {
    Path input =
        write(
            "conceptual.yaml",
            "version: 1\nmodel:\n  type: conceptual\n  name: Explicit\nentities: []\n");

    ConceptualModel model = (ConceptualModel) parser.parse(input);

    assertThat(model.diagram().name()).isEqualTo("Explicit");
  }

  @Test
  void rejectsUnknownModelType() throws Exception {
    Path input =
        write("unknown-type.yaml", "version: 1\nmodel:\n  type: physical\n  name: Invalid\n");

    assertThatThrownBy(() -> parser.parse(input))
        .isInstanceOf(ModelParseException.class)
        .hasMessageContaining("error[E108]")
        .hasMessageContaining("conceptual or logical");
  }

  @Test
  void reportsMissingSchemaVersion() throws Exception {
    Path input = write("missing-version.yaml", "diagram:\n  name: Missing\n");

    assertThatThrownBy(() -> parser.parse(input))
        .isInstanceOf(ModelParseException.class)
        .hasMessageContaining("error[E106]")
        .hasMessageContaining("version");
  }

  @Test
  void rejectsUnknownFields() throws Exception {
    Path input =
        write(
            "unknown.json", "{\"version\":1,\"diagram\":{\"name\":\"Unknown\"},\"execute\":true}");

    assertThatThrownBy(() -> parser.parse(input))
        .isInstanceOf(ModelParseException.class)
        .hasMessageContaining("error[E107]")
        .hasMessageContaining("execute");
  }

  @Test
  void reportsSyntaxLocation() throws Exception {
    Path input = write("broken.yaml", "version: 1\nentities:\n  - name: [\n");

    assertThatThrownBy(() -> parser.parse(input))
        .isInstanceOf(ModelParseException.class)
        .hasMessageContaining("error[E107]")
        .hasMessageContaining("line");
  }

  @Test
  void rejectsUnsupportedExtensions() throws Exception {
    Path input = write("model.txt", "version: 1");

    assertThatThrownBy(() -> parser.parse(input))
        .isInstanceOf(ModelParseException.class)
        .hasMessageContaining("error[E104]")
        .hasMessageContaining(".yaml");
  }

  @Test
  void rejectsMissingFiles() {
    Path input = temporaryDirectory.resolve("missing.yaml");

    assertThatThrownBy(() -> parser.parse(input))
        .isInstanceOf(ModelParseException.class)
        .hasMessageContaining("error[E100]");
  }

  private Path fixture(String name) throws URISyntaxException {
    return Path.of(getClass().getResource("/fixtures/" + name).toURI());
  }

  private Path write(String name, String content) throws Exception {
    return Files.writeString(temporaryDirectory.resolve(name), content);
  }
}
