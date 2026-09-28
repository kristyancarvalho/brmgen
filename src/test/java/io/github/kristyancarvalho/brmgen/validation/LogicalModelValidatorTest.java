package io.github.kristyancarvalho.brmgen.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kristyancarvalho.brmgen.model.Column;
import io.github.kristyancarvalho.brmgen.model.Diagram;
import io.github.kristyancarvalho.brmgen.model.ForeignKey;
import io.github.kristyancarvalho.brmgen.model.ForeignKeyReference;
import io.github.kristyancarvalho.brmgen.model.LogicalModel;
import io.github.kristyancarvalho.brmgen.model.PrimaryKey;
import io.github.kristyancarvalho.brmgen.model.Table;
import io.github.kristyancarvalho.brmgen.parser.ModelParser;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogicalModelValidatorTest {
  private final LogicalModelValidator validator = new LogicalModelValidator();

  @Test
  void acceptsParsedForeignKeyModel() throws Exception {
    LogicalModel model = (LogicalModel) new ModelParser().parse(fixture("logical-model.yaml"));

    assertThat(validator.validate(model).isValid()).isTrue();
  }

  @Test
  void acceptsCompositePrimaryKeyAndMultipleForeignKeys() {
    Table author =
        new Table(
            "Author",
            List.of(column("id", "integer")),
            new PrimaryKey(List.of("id")),
            List.of(),
            null);
    Table book =
        new Table(
            "Book",
            List.of(column("id", "integer")),
            new PrimaryKey(List.of("id")),
            List.of(),
            null);
    Table writes =
        new Table(
            "Writes",
            List.of(column("author_id", "integer"), column("book_id", "integer")),
            new PrimaryKey(List.of("author_id", "book_id")),
            List.of(foreignKey("author_id", "Author"), foreignKey("book_id", "Book")),
            null);

    ValidationResult result =
        validator.validate(
            new LogicalModel(1, new Diagram("Library"), List.of(author, book, writes)));

    assertThat(result.isValid()).isTrue();
    assertThat(writes.primaryKeyColumns()).containsExactly("author_id", "book_id");
  }

  @Test
  void reportsDuplicateAndInvalidKeyReferences() {
    Table parent =
        new Table(
            "Parent",
            List.of(column("id", "integer")),
            new PrimaryKey(List.of("missing")),
            List.of(),
            null);
    Table child =
        new Table(
            "Child",
            List.of(column("id", "varchar"), column("id", "integer")),
            null,
            List.of(
                new ForeignKey(
                    "fk_parent",
                    List.of("id"),
                    new ForeignKeyReference("Parent", List.of("id", "missing")),
                    null,
                    null),
                new ForeignKey(
                    "fk_unknown",
                    List.of("missing"),
                    new ForeignKeyReference("Unknown", List.of("id")),
                    null,
                    null)),
            null);
    LogicalModel model =
        new LogicalModel(1, new Diagram("Invalid"), List.of(parent, child, parent));

    ValidationResult result = validator.validate(model);

    assertThat(result.isValid()).isFalse();
    assertThat(result.diagnostics().stream().map(Diagnostic::code))
        .contains("L003", "L004", "L005", "L006", "L007", "L008", "L009", "L010");
  }

  private Column column(String name, String type) {
    return new Column(name, type, false, false);
  }

  private ForeignKey foreignKey(String column, String table) {
    return new ForeignKey(
        null, List.of(column), new ForeignKeyReference(table, List.of("id")), null, null);
  }

  private Path fixture(String name) throws URISyntaxException {
    return Path.of(getClass().getResource("/fixtures/" + name).toURI());
  }
}
