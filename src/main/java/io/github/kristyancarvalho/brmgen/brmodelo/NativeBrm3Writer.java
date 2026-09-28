package io.github.kristyancarvalho.brmgen.brmodelo;

import io.github.kristyancarvalho.brmgen.model.Attribute;
import io.github.kristyancarvalho.brmgen.model.Cardinality;
import io.github.kristyancarvalho.brmgen.model.Connection;
import io.github.kristyancarvalho.brmgen.model.Entity;
import io.github.kristyancarvalho.brmgen.model.Generalization;
import io.github.kristyancarvalho.brmgen.model.ModelDefinition;
import io.github.kristyancarvalho.brmgen.model.Position;
import io.github.kristyancarvalho.brmgen.model.Relationship;
import java.awt.Point;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class NativeBrm3Writer {
  public void write(ModelDefinition model, Path jar, Path output) throws BrmodeloException {
    ensureSupported(model);
    new BrmodeloRuntimeInspector().requireCompatible(jar);
    System.setProperty("java.awt.headless", "true");
    Path runtimeDirectory = null;
    String previousUserDirectory = System.getProperty("user.dir");
    try {
      runtimeDirectory = Files.createTempDirectory("brmgen-brmodelo-");
      System.setProperty("user.dir", runtimeDirectory.toString());
      writeWithRuntime(model, jar, output);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      throw new BrmodeloException(
          "error[E205]: brModelo runtime failed during native generation: "
              + (cause == null ? exception.getMessage() : cause.getMessage()),
          cause == null ? exception : cause);
    } catch (ReflectiveOperationException | IOException | LinkageError exception) {
      throw new BrmodeloException(
          "error[E205]: cannot generate native .brM3 output: " + exception.getMessage(), exception);
    } finally {
      System.setProperty("user.dir", previousUserDirectory);
      cleanRuntimeDirectory(runtimeDirectory);
    }
  }

  private void writeWithRuntime(ModelDefinition model, Path jar, Path output)
      throws ReflectiveOperationException, IOException {
    try (URLClassLoader loader = BrmodeloRuntimeInspector.loader(jar)) {
      RuntimeApi api = new RuntimeApi(loader);
      Object editor = api.editorConstructor.newInstance();
      Object diagram = api.diagramConstructor.newInstance(editor);
      api.setDiagramName.invoke(diagram, model.diagram().name());
      Map<String, Object> entities = createEntities(model.entities(), diagram, api);
      Set<String> weakEntities =
          model.entities().stream()
              .filter(Entity::weak)
              .map(Entity::name)
              .collect(Collectors.toSet());
      createRelationships(model.relationships(), entities, weakEntities, diagram, api);
      createGeneralizations(model.generalizations(), entities, diagram, api);
      Object guard = api.guardConstructor.newInstance(diagram);
      try (ObjectOutputStream stream =
          new ObjectOutputStream(
              Files.newOutputStream(
                  output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
        stream.writeObject(guard);
      }
    }
  }

  private Map<String, Object> createEntities(
      List<Entity> definitions, Object diagram, RuntimeApi api)
      throws ReflectiveOperationException {
    Map<String, Object> entities = new LinkedHashMap<>();
    for (Entity definition : definitions) {
      Object entity = api.entityConstructor.newInstance(diagram);
      api.setText.invoke(entity, definition.name());
      setBounds(entity, definition.position(), 120, 58, api);
      entities.put(definition.name(), entity);
      createAttributes(definition.attributes(), entity, diagram, definition.position(), api);
    }
    return entities;
  }

  private void createRelationships(
      List<Relationship> definitions,
      Map<String, Object> entities,
      Set<String> weakEntities,
      Object diagram,
      RuntimeApi api)
      throws ReflectiveOperationException {
    for (Relationship definition : definitions) {
      Object relationship = api.relationshipConstructor.newInstance(diagram);
      api.setText.invoke(relationship, definition.name());
      setBounds(relationship, definition.position(), 150, 50, api);
      for (Connection connection : definition.connections()) {
        Object line =
            link(
                entities.get(connection.entity()),
                relationship,
                diagram,
                connection.cardinality(),
                api);
        if (definition.identifying() && weakEntities.contains(connection.entity())) {
          api.setDoubleLine.invoke(line, true);
        }
      }
      createAttributes(definition.attributes(), relationship, diagram, definition.position(), api);
    }
  }

  private void createGeneralizations(
      List<Generalization> definitions,
      Map<String, Object> entities,
      Object diagram,
      RuntimeApi api)
      throws ReflectiveOperationException {
    for (Generalization definition : definitions) {
      if (definition.disjoint()) {
        createSpecialization(
            definition.parent(),
            definition.children(),
            definition.position(),
            definition.total(),
            entities,
            diagram,
            api);
      } else {
        int offset = 0;
        for (String child : definition.children()) {
          Position position =
              new Position(definition.position().x() + offset, definition.position().y());
          createSpecialization(
              definition.parent(),
              List.of(child),
              position,
              definition.total(),
              entities,
              diagram,
              api);
          offset += 60;
        }
      }
    }
  }

  private void createSpecialization(
      String parent,
      List<String> children,
      Position position,
      boolean total,
      Map<String, Object> entities,
      Object diagram,
      RuntimeApi api)
      throws ReflectiveOperationException {
    Object specialization = api.specializationConstructor.newInstance(diagram);
    setBounds(specialization, position, 40, 32, api);
    api.setTotal.invoke(specialization, total);
    link(entities.get(parent), specialization, diagram, null, api);
    for (String child : children) {
      link(entities.get(child), specialization, diagram, null, api);
    }
  }

  private void createAttributes(
      List<Attribute> definitions,
      Object owner,
      Object diagram,
      Position ownerPosition,
      RuntimeApi api)
      throws ReflectiveOperationException {
    int index = 0;
    for (Attribute definition : definitions) {
      Position position = attributePosition(ownerPosition, index++);
      Object attribute = api.attributeConstructor.newInstance(diagram);
      api.setText.invoke(attribute, definition.name());
      setBounds(attribute, position, 100, 22, api);
      api.setIdentifier.invoke(attribute, definition.key() || definition.partialKey());
      api.setMultivalued.invoke(attribute, definition.multivalued());
      link(owner, attribute, diagram, null, api);
      createAttributeComponents(definition.components(), attribute, diagram, position, api);
    }
  }

  private void createAttributeComponents(
      List<Attribute> definitions,
      Object parent,
      Object diagram,
      Position parentPosition,
      RuntimeApi api)
      throws ReflectiveOperationException {
    int index = 0;
    for (Attribute definition : definitions) {
      Position position = new Position(parentPosition.x() + 150, parentPosition.y() + index++ * 34);
      Object attribute = api.attributeConstructor.newInstance(diagram);
      api.setText.invoke(attribute, definition.name());
      setBounds(attribute, position, 100, 22, api);
      api.setIdentifier.invoke(attribute, definition.key() || definition.partialKey());
      api.setMultivalued.invoke(attribute, definition.multivalued());
      link(parent, attribute, diagram, null, api);
      createAttributeComponents(definition.components(), attribute, diagram, position, api);
    }
  }

  private Object link(
      Object first, Object second, Object diagram, Cardinality cardinality, RuntimeApi api)
      throws ReflectiveOperationException {
    Object link = api.linkConstructor.newInstance(diagram);
    api.startLink.invoke(link, 0, center(first, api), center(second, api));
    Object firstPoint = api.getFirstPoint.invoke(link);
    Object secondPoint = api.getSecondPoint.invoke(link);
    api.setPointOwner.invoke(firstPoint, first);
    api.setPointOwner.invoke(secondPoint, second);
    api.addOwnerLink.invoke(first, firstPoint);
    api.addOwnerLink.invoke(second, secondPoint);
    api.positionPoint.invoke(first, firstPoint);
    api.positionPoint.invoke(second, secondPoint);
    api.organizeLink.invoke(link);
    api.prepareCardinality.invoke(link);
    if (cardinality != null) {
      Object nativeCardinality = api.getCardinality.invoke(link);
      Object nativeValue =
          Enum.valueOf(api.cardinalityEnum.asSubclass(Enum.class), cardinality.nativeName());
      api.setCardinality.invoke(nativeCardinality, nativeValue);
    }
    return link;
  }

  private Point center(Object shape, RuntimeApi api) throws ReflectiveOperationException {
    int left = (int) api.getLeft.invoke(shape);
    int top = (int) api.getTop.invoke(shape);
    int width = (int) api.getWidth.invoke(shape);
    int height = (int) api.getHeight.invoke(shape);
    return new Point(left + width / 2, top + height / 2);
  }

  private void setBounds(Object shape, Position position, int width, int height, RuntimeApi api)
      throws ReflectiveOperationException {
    api.setBounds.invoke(shape, position.x(), position.y(), width, height);
    api.reframe.invoke(shape);
  }

  private Position attributePosition(Position owner, int index) {
    int row = index / 4;
    int column = index % 4;
    return new Position(owner.x() + column * 120 - 20, owner.y() - 70 - row * 40);
  }

  private void cleanRuntimeDirectory(Path runtimeDirectory) {
    if (runtimeDirectory == null) {
      return;
    }
    try {
      Files.deleteIfExists(runtimeDirectory.resolve("config.chc"));
      Files.deleteIfExists(runtimeDirectory);
    } catch (IOException exception) {
      runtimeDirectory.toFile().deleteOnExit();
    }
  }

  private void ensureSupported(ModelDefinition model) throws BrmodeloException {
    boolean derived =
        model.entities().stream()
                .flatMap(entity -> entity.attributes().stream())
                .anyMatch(this::derived)
            || model.relationships().stream()
                .flatMap(relationship -> relationship.attributes().stream())
                .anyMatch(this::derived);
    if (derived) {
      throw new BrmodeloException(
          "error[E208]: brModelo 3.3.x has no native derived-attribute property");
    }
  }

  private boolean derived(Attribute attribute) {
    return attribute.derived() || attribute.components().stream().anyMatch(this::derived);
  }

  private static final class RuntimeApi {
    private final Class<?> formClass;
    private final Class<? extends Enum> cardinalityEnum;
    private final Constructor<?> editorConstructor;
    private final Constructor<?> diagramConstructor;
    private final Constructor<?> entityConstructor;
    private final Constructor<?> relationshipConstructor;
    private final Constructor<?> attributeConstructor;
    private final Constructor<?> specializationConstructor;
    private final Constructor<?> linkConstructor;
    private final Constructor<?> guardConstructor;
    private final Method setDiagramName;
    private final Method setText;
    private final Method setBounds;
    private final Method reframe;
    private final Method getLeft;
    private final Method getTop;
    private final Method getWidth;
    private final Method getHeight;
    private final Method setIdentifier;
    private final Method setMultivalued;
    private final Method setTotal;
    private final Method startLink;
    private final Method getFirstPoint;
    private final Method getSecondPoint;
    private final Method setPointOwner;
    private final Method addOwnerLink;
    private final Method positionPoint;
    private final Method organizeLink;
    private final Method prepareCardinality;
    private final Method getCardinality;
    private final Method setCardinality;
    private final Method setDoubleLine;

    @SuppressWarnings("unchecked")
    private RuntimeApi(ClassLoader loader) throws ReflectiveOperationException {
      Class<?> editor = loader.loadClass("controlador.Editor");
      Class<?> diagram = loader.loadClass("controlador.Diagrama");
      Class<?> conceptual = loader.loadClass("diagramas.conceitual.DiagramaConceitual");
      Class<?> entity = loader.loadClass("diagramas.conceitual.Entidade");
      Class<?> relationship = loader.loadClass("diagramas.conceitual.Relacionamento");
      Class<?> attribute = loader.loadClass("diagramas.conceitual.Atributo");
      Class<?> specialization = loader.loadClass("diagramas.conceitual.Especializacao");
      Class<?> link = loader.loadClass("diagramas.conceitual.Ligacao");
      Class<?> cardinality = loader.loadClass("desenho.preAnyDiagrama.PreCardinalidade");
      Class<?> element = loader.loadClass("desenho.Elementar");
      Class<?> line = loader.loadClass("desenho.linhas.Linha");
      Class<?> point = loader.loadClass("desenho.linhas.PontoDeLinha");
      Class<?> guard = loader.loadClass("controlador.apoios.GuardaPadraoBrM");
      formClass = loader.loadClass("desenho.formas.Forma");
      cardinalityEnum =
          (Class<? extends Enum>)
              loader.loadClass("desenho.preAnyDiagrama.PreCardinalidade$TiposCard");
      editorConstructor = editor.getConstructor();
      diagramConstructor = conceptual.getConstructor(editor);
      entityConstructor = entity.getConstructor(diagram);
      relationshipConstructor = relationship.getConstructor(diagram);
      attributeConstructor = attribute.getConstructor(diagram);
      specializationConstructor = specialization.getConstructor(diagram);
      linkConstructor = link.getConstructor(diagram);
      guardConstructor = guard.getConstructor(diagram);
      setDiagramName = diagram.getMethod("setNome", String.class);
      setText = formClass.getMethod("setTexto", String.class);
      setBounds = element.getMethod("SetBounds", int.class, int.class, int.class, int.class);
      reframe = element.getMethod("Reenquadre");
      getLeft = element.getMethod("getLeft");
      getTop = element.getMethod("getTop");
      getWidth = element.getMethod("getWidth");
      getHeight = element.getMethod("getHeight");
      setIdentifier = attribute.getMethod("setIdentificador", boolean.class);
      setMultivalued = attribute.getMethod("setMultivalorado", boolean.class);
      setTotal = specialization.getMethod("setTotal", boolean.class);
      startLink = link.getMethod("SuperInicie", int.class, Point.class, Point.class);
      getFirstPoint = line.getMethod("getPontaA");
      getSecondPoint = line.getMethod("getPontaB");
      setPointOwner = point.getMethod("SetEm", formClass);
      addOwnerLink = formClass.getMethod("maisLigacao", point);
      positionPoint = formClass.getMethod("PosicionePonto", point);
      organizeLink = line.getMethod("OrganizeLinha");
      prepareCardinality = link.getMethod("PrepareCardinalidade");
      getCardinality = link.getMethod("getCard");
      setCardinality = cardinality.getMethod("setCard", cardinalityEnum);
      setDoubleLine = line.getMethod("setDuplaLinha", boolean.class);
    }
  }
}
