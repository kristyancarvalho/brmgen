package io.github.kristyancarvalho.brmgen.parser;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.kristyancarvalho.brmgen.model.ConceptualModel;
import io.github.kristyancarvalho.brmgen.model.LogicalModel;
import io.github.kristyancarvalho.brmgen.model.ModelDefinition;
import io.github.kristyancarvalho.brmgen.model.ModelType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ModelParser {
  private static final long MAX_INPUT_BYTES = 2 * 1024 * 1024;
  private static final StreamReadConstraints READ_CONSTRAINTS =
      StreamReadConstraints.builder()
          .maxNestingDepth(100)
          .maxStringLength(1_000_000)
          .maxNumberLength(100)
          .build();

  private final ObjectMapper jsonMapper;
  private final ObjectMapper yamlMapper;

  public ModelParser() {
    jsonMapper = configure(new ObjectMapper());
    YAMLFactory yamlFactory = YAMLFactory.builder().streamReadConstraints(READ_CONSTRAINTS).build();
    yamlMapper = configure(new ObjectMapper(yamlFactory));
  }

  public ModelDefinition parse(Path input) throws ModelParseException {
    Path normalized = input.toAbsolutePath().normalize();
    ensureReadableInput(normalized);
    ObjectMapper mapper = mapperFor(normalized);

    try (InputStream stream = Files.newInputStream(normalized)) {
      JsonNode root = mapper.readTree(stream);
      ensureVersion(root, normalized);
      return parseModel(mapper, (ObjectNode) root, normalized);
    } catch (JsonProcessingException exception) {
      throw invalidContent(normalized, exception);
    } catch (IOException exception) {
      throw new ModelParseException(
          "error[E103]: cannot read `" + normalized + "`: " + exception.getMessage(), exception);
    }
  }

  private ModelDefinition parseModel(ObjectMapper mapper, ObjectNode root, Path input)
      throws JsonProcessingException, ModelParseException {
    JsonNode metadata = root.get("model");
    if (metadata == null) {
      return mapper.treeToValue(root, ConceptualModel.class);
    }
    if (!metadata.isObject() || !metadata.hasNonNull("type") || !metadata.hasNonNull("name")) {
      throw new ModelParseException(
          "error[E108]: `model.type` and `model.name` are required in `" + input + "`");
    }
    ModelType type;
    try {
      type = ModelType.parse(metadata.get("type").asText());
    } catch (IllegalArgumentException exception) {
      throw new ModelParseException(
          "error[E108]: unsupported model type `"
              + metadata.get("type").asText()
              + "` in `"
              + input
              + "`; use conceptual or logical");
    }
    ObjectNode normalized = root.deepCopy();
    normalized.remove("model");
    ObjectNode diagram = normalized.putObject("diagram");
    diagram.put("name", metadata.get("name").asText());
    return type == ModelType.CONCEPTUAL
        ? mapper.treeToValue(normalized, ConceptualModel.class)
        : mapper.treeToValue(normalized, LogicalModel.class);
  }

  private ObjectMapper configure(ObjectMapper mapper) {
    mapper.getFactory().setStreamReadConstraints(READ_CONSTRAINTS);
    mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    return mapper;
  }

  private void ensureReadableInput(Path input) throws ModelParseException {
    if (!Files.isRegularFile(input)) {
      throw new ModelParseException("error[E100]: input is not a regular file: `" + input + "`");
    }
    if (!Files.isReadable(input)) {
      throw new ModelParseException("error[E101]: input is not readable: `" + input + "`");
    }
    try {
      if (Files.size(input) > MAX_INPUT_BYTES) {
        throw new ModelParseException(
            "error[E102]: input exceeds the 2 MiB size limit: `" + input + "`");
      }
    } catch (IOException exception) {
      throw new ModelParseException(
          "error[E103]: cannot inspect `" + input + "`: " + exception.getMessage(), exception);
    }
  }

  private ObjectMapper mapperFor(Path input) throws ModelParseException {
    String filename = input.getFileName().toString().toLowerCase(Locale.ROOT);
    if (filename.endsWith(".yaml") || filename.endsWith(".yml")) {
      return yamlMapper;
    }
    if (filename.endsWith(".json")) {
      return jsonMapper;
    }
    throw new ModelParseException(
        "error[E104]: unsupported input format for `" + input + "`; use .yaml, .yml, or .json");
  }

  private void ensureVersion(JsonNode root, Path input) throws ModelParseException {
    if (root == null || !root.isObject()) {
      throw new ModelParseException("error[E105]: model root must be an object in `" + input + "`");
    }
    if (!root.hasNonNull("version") || !root.get("version").canConvertToInt()) {
      throw new ModelParseException(
          "error[E106]: required integer field `version` is missing in `" + input + "`");
    }
  }

  private ModelParseException invalidContent(Path input, JsonProcessingException exception) {
    JsonLocation location = exception.getLocation();
    String at =
        location == null
            ? ""
            : " at line " + location.getLineNr() + ", column " + location.getColumnNr();
    String cause = exception.getOriginalMessage();
    return new ModelParseException(
        "error[E107]: invalid model syntax in `" + input + "`" + at + ": " + cause, exception);
  }
}
