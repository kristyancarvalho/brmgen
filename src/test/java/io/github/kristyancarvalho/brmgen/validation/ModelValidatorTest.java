package io.github.kristyancarvalho.brmgen.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kristyancarvalho.brmgen.model.Attribute;
import io.github.kristyancarvalho.brmgen.model.ConceptualModel;
import io.github.kristyancarvalho.brmgen.model.Connection;
import io.github.kristyancarvalho.brmgen.model.Diagram;
import io.github.kristyancarvalho.brmgen.model.Entity;
import io.github.kristyancarvalho.brmgen.model.Generalization;
import io.github.kristyancarvalho.brmgen.model.Position;
import io.github.kristyancarvalho.brmgen.model.Relationship;
import io.github.kristyancarvalho.brmgen.parser.ModelParser;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelValidatorTest {
  private final ModelValidator validator = new ModelValidator();

  @Test
  void acceptsCompleteModel() throws Exception {
    ConceptualModel model =
        (ConceptualModel) new ModelParser().parse(fixture("complete-model.yaml"));

    ValidationResult result = validator.validate(model);

    assertThat(result.isValid()).isTrue();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  void reportsDuplicateNamesUnknownReferencesAndIncompleteRelationships() {
    Entity customer = new Entity("Customer", false, List.of(simple("id"), simple("id")), null);
    Relationship relationship =
        new Relationship(
            "Places", false, List.of(new Connection("Missing", null)), List.of(), null);
    ConceptualModel model =
        new ConceptualModel(
            1,
            new Diagram("Invalid"),
            List.of(customer, customer),
            List.of(relationship, relationship),
            List.of());

    ValidationResult result = validator.validate(model);

    assertThat(result.isValid()).isFalse();
    assertThat(codes(result)).contains("E003", "E004", "E005", "E006", "E007", "E008");
  }

  @Test
  void reportsInvalidAttributesWeakEntitiesAndPositions() {
    Attribute contradictory = new Attribute("code", true, true, false, true, false, List.of());
    Attribute emptyComposite =
        new Attribute("address", false, false, false, false, true, List.of());
    Entity weak =
        new Entity(
            "Dependent", true, List.of(contradictory, emptyComposite), new Position(-1, null));
    ConceptualModel model =
        new ConceptualModel(1, new Diagram("Invalid"), List.of(weak), List.of(), List.of());

    ValidationResult result = validator.validate(model);

    assertThat(codes(result)).contains("E009", "E011", "E012", "E015");
    assertThat(result.diagnostics())
        .anySatisfy(
            diagnostic ->
                assertThat(diagnostic.render())
                    .contains("cause:")
                    .contains("suggestion:")
                    .contains("entities[0]"));
  }

  @Test
  void reportsUnknownAndCyclicGeneralizations() {
    Entity parent = new Entity("Parent", false, List.of(), null);
    Entity child = new Entity("Child", false, List.of(), null);
    List<Generalization> generalizations =
        List.of(
            new Generalization("Parent", List.of("Child", "Missing"), false, false, null),
            new Generalization("Child", List.of("Parent"), false, false, null));
    ConceptualModel model =
        new ConceptualModel(
            1, new Diagram("Cycle"), List.of(parent, child), List.of(), generalizations);

    ValidationResult result = validator.validate(model);

    assertThat(codes(result)).contains("E013", "E014");
  }

  @Test
  void rejectsUnsupportedSchemaVersionAndMissingDiagram() {
    ConceptualModel model = new ConceptualModel(2, null, List.of(), List.of(), List.of());

    ValidationResult result = validator.validate(model);

    assertThat(codes(result)).containsExactly("E001", "E002");
  }

  private List<String> codes(ValidationResult result) {
    return result.diagnostics().stream().map(Diagnostic::code).toList();
  }

  private Attribute simple(String name) {
    return new Attribute(name, false, false, false, false, false, List.of());
  }

  private Path fixture(String name) throws URISyntaxException {
    return Path.of(getClass().getResource("/fixtures/" + name).toURI());
  }
}
