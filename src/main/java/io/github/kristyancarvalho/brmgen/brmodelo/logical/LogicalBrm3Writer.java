package io.github.kristyancarvalho.brmgen.brmodelo.logical;

import io.github.kristyancarvalho.brmgen.brmodelo.BrmodeloException;
import io.github.kristyancarvalho.brmgen.brmodelo.BrmodeloRuntimeInspector;
import io.github.kristyancarvalho.brmgen.model.Cardinality;
import io.github.kristyancarvalho.brmgen.model.Column;
import io.github.kristyancarvalho.brmgen.model.ForeignKey;
import io.github.kristyancarvalho.brmgen.model.LogicalModel;
import io.github.kristyancarvalho.brmgen.model.Table;
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
import java.util.Map;

public final class LogicalBrm3Writer {
  public void write(LogicalModel model, Path jar, Path output) throws BrmodeloException {
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
          "error[E210]: brModelo runtime failed during logical generation: "
              + (cause == null ? exception.getMessage() : cause.getMessage()),
          cause == null ? exception : cause);
    } catch (ReflectiveOperationException | IOException | LinkageError exception) {
      throw new BrmodeloException(
          "error[E210]: cannot generate native logical .brM3 output: " + exception.getMessage(),
          exception);
    } finally {
      System.setProperty("user.dir", previousUserDirectory);
      cleanRuntimeDirectory(runtimeDirectory);
    }
  }

  private void writeWithRuntime(LogicalModel model, Path jar, Path output)
      throws ReflectiveOperationException, IOException, BrmodeloException {
    try (URLClassLoader loader = BrmodeloRuntimeInspector.loader(jar)) {
      RuntimeApi api = new RuntimeApi(loader);
      Object editor = api.editorConstructor.newInstance();
      Object diagram = api.diagramConstructor.newInstance(editor);
      api.setDiagramName.invoke(diagram, model.diagram().name());
      NativeTables nativeTables = createTables(model, diagram, api);
      createForeignKeys(model, nativeTables, diagram, api);
      Object guard = api.guardConstructor.newInstance(diagram);
      try (ObjectOutputStream stream =
          new ObjectOutputStream(
              Files.newOutputStream(
                  output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
        stream.writeObject(guard);
      }
    }
  }

  private NativeTables createTables(LogicalModel model, Object diagram, RuntimeApi api)
      throws ReflectiveOperationException {
    Map<String, Object> tables = new LinkedHashMap<>();
    Map<String, Map<String, Object>> columns = new LinkedHashMap<>();
    Map<String, Object> primaryKeys = new LinkedHashMap<>();
    for (Table definition : model.tables()) {
      Object table = api.tableConstructor.newInstance(diagram);
      api.setText.invoke(table, definition.name());
      Map<String, Object> tableColumns = new LinkedHashMap<>();
      for (Column columnDefinition : definition.columns()) {
        Object column = api.columnConstructor.newInstance(table);
        api.setColumnText.invoke(column, columnDefinition.name());
        api.setColumnType.invoke(
            column, columnDefinition.type() == null ? "" : columnDefinition.type());
        tableColumns.put(columnDefinition.name(), column);
      }
      Object primaryKey = createPrimaryKey(definition, table, tableColumns, api);
      int height = Math.max(90, 48 + definition.columns().size() * 22);
      api.setBounds.invoke(
          table, definition.position().x(), definition.position().y(), 210, height);
      api.reframe.invoke(table);
      tables.put(definition.name(), table);
      columns.put(definition.name(), tableColumns);
      primaryKeys.put(definition.name(), primaryKey);
    }
    return new NativeTables(tables, columns, primaryKeys);
  }

  private Object createPrimaryKey(
      Table definition, Object table, Map<String, Object> columns, RuntimeApi api)
      throws ReflectiveOperationException {
    if (definition.primaryKeyColumns().isEmpty()) {
      return null;
    }
    Object constraint = api.constraintConstructor.newInstance(table);
    api.setConstraintType.invoke(constraint, api.primaryKeyType);
    for (String name : definition.primaryKeyColumns()) {
      Object column = columns.get(name);
      api.setColumnKey.invoke(column, true);
      api.addConstraintPair.invoke(constraint, column, null);
    }
    api.validateConstraint.invoke(constraint);
    return constraint;
  }

  private void createForeignKeys(
      LogicalModel model, NativeTables nativeTables, Object diagram, RuntimeApi api)
      throws ReflectiveOperationException, BrmodeloException {
    for (Table definition : model.tables()) {
      for (ForeignKey foreignKey : definition.foreignKeys()) {
        Object referencedPrimaryKey =
            nativeTables.primaryKeys().get(foreignKey.references().table());
        if (referencedPrimaryKey == null) {
          throw new BrmodeloException(
              "error[E210]: foreign key in table `"
                  + definition.name()
                  + "` references table `"
                  + foreignKey.references().table()
                  + "` without a primary key");
        }
        Object localTable = nativeTables.tables().get(definition.name());
        Object referencedTable = nativeTables.tables().get(foreignKey.references().table());
        Object line =
            createLine(
                localTable,
                referencedTable,
                foreignKey.localCardinality(),
                foreignKey.referencedCardinality(),
                diagram,
                api);
        Object constraint = api.constraintConstructor.newInstance(localTable);
        api.setConstraintType.invoke(constraint, api.foreignKeyType);
        if (foreignKey.name() != null && !foreignKey.name().isBlank()) {
          api.setConstraintName.invoke(constraint, foreignKey.name());
          api.setConstraintNamed.invoke(constraint, true);
        }
        for (int index = 0; index < foreignKey.columns().size(); index++) {
          Object localColumn =
              nativeTables.columns().get(definition.name()).get(foreignKey.columns().get(index));
          Object referencedColumn =
              nativeTables
                  .columns()
                  .get(foreignKey.references().table())
                  .get(foreignKey.references().columns().get(index));
          api.setColumnForeignKey.invoke(localColumn, true);
          if (index == 0) {
            api.addForeignKeyPair.invoke(
                constraint, referencedColumn, localColumn, line, referencedPrimaryKey);
          } else {
            api.addConstraintPair.invoke(constraint, referencedColumn, localColumn);
          }
        }
        api.validateConstraint.invoke(constraint);
      }
    }
  }

  private Object createLine(
      Object localTable,
      Object referencedTable,
      Cardinality localCardinality,
      Cardinality referencedCardinality,
      Object diagram,
      RuntimeApi api)
      throws ReflectiveOperationException {
    Object line = api.lineConstructor.newInstance(diagram);
    api.startLine.invoke(line, 0, center(localTable, api), center(referencedTable, api));
    Object firstPoint = api.getFirstPoint.invoke(line);
    Object secondPoint = api.getSecondPoint.invoke(line);
    api.setPointOwner.invoke(firstPoint, localTable);
    api.setPointOwner.invoke(secondPoint, referencedTable);
    api.addOwnerLink.invoke(localTable, firstPoint);
    api.addOwnerLink.invoke(referencedTable, secondPoint);
    api.positionPoint.invoke(localTable, firstPoint);
    api.positionPoint.invoke(referencedTable, secondPoint);
    api.organizeLine.invoke(line);
    api.prepareCardinality.invoke(line);
    setCardinality(api.getCardA.invoke(line), localCardinality, api);
    setCardinality(api.getCardB.invoke(line), referencedCardinality, api);
    api.adjustArrow.invoke(line);
    return line;
  }

  private void setCardinality(Object nativeCardinality, Cardinality cardinality, RuntimeApi api)
      throws ReflectiveOperationException {
    Object value =
        Enum.valueOf(api.cardinalityEnum.asSubclass(Enum.class), cardinality.nativeName());
    api.setCardinality.invoke(nativeCardinality, value);
  }

  private Point center(Object shape, RuntimeApi api) throws ReflectiveOperationException {
    int left = (int) api.getLeft.invoke(shape);
    int top = (int) api.getTop.invoke(shape);
    int width = (int) api.getWidth.invoke(shape);
    int height = (int) api.getHeight.invoke(shape);
    return new Point(left + width / 2, top + height / 2);
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

  private record NativeTables(
      Map<String, Object> tables,
      Map<String, Map<String, Object>> columns,
      Map<String, Object> primaryKeys) {}

  private static final class RuntimeApi {
    private final Class<? extends Enum> cardinalityEnum;
    private final Object primaryKeyType;
    private final Object foreignKeyType;
    private final Constructor<?> editorConstructor;
    private final Constructor<?> diagramConstructor;
    private final Constructor<?> tableConstructor;
    private final Constructor<?> columnConstructor;
    private final Constructor<?> constraintConstructor;
    private final Constructor<?> lineConstructor;
    private final Constructor<?> guardConstructor;
    private final Method setDiagramName;
    private final Method setText;
    private final Method setBounds;
    private final Method reframe;
    private final Method getLeft;
    private final Method getTop;
    private final Method getWidth;
    private final Method getHeight;
    private final Method setColumnText;
    private final Method setColumnType;
    private final Method setColumnKey;
    private final Method setColumnForeignKey;
    private final Method setConstraintType;
    private final Method setConstraintName;
    private final Method setConstraintNamed;
    private final Method addConstraintPair;
    private final Method addForeignKeyPair;
    private final Method validateConstraint;
    private final Method startLine;
    private final Method getFirstPoint;
    private final Method getSecondPoint;
    private final Method setPointOwner;
    private final Method addOwnerLink;
    private final Method positionPoint;
    private final Method organizeLine;
    private final Method prepareCardinality;
    private final Method getCardA;
    private final Method getCardB;
    private final Method setCardinality;
    private final Method adjustArrow;

    @SuppressWarnings("unchecked")
    private RuntimeApi(ClassLoader loader) throws ReflectiveOperationException {
      Class<?> editor = loader.loadClass("controlador.Editor");
      Class<?> diagram = loader.loadClass("controlador.Diagrama");
      Class<?> logical = loader.loadClass("diagramas.logico.DiagramaLogico");
      Class<?> table = loader.loadClass("diagramas.logico.Tabela");
      Class<?> column = loader.loadClass("diagramas.logico.Campo");
      Class<?> constraint = loader.loadClass("diagramas.logico.Constraint");
      Class<?> constraintType = loader.loadClass("diagramas.logico.Constraint$CONSTRAINT_TIPO");
      Class<?> line = loader.loadClass("diagramas.logico.LogicoLinha");
      Class<?> logicalCardinality = loader.loadClass("diagramas.logico.LogicoCardinalidade");
      Class<?> cardinality = loader.loadClass("desenho.preAnyDiagrama.PreCardinalidade");
      Class<?> element = loader.loadClass("desenho.Elementar");
      Class<?> form = loader.loadClass("desenho.formas.Forma");
      Class<?> point = loader.loadClass("desenho.linhas.PontoDeLinha");
      Class<?> baseLine = loader.loadClass("desenho.linhas.Linha");
      Class<?> guard = loader.loadClass("controlador.apoios.GuardaPadraoBrM");
      cardinalityEnum =
          (Class<? extends Enum>)
              loader.loadClass("desenho.preAnyDiagrama.PreCardinalidade$TiposCard");
      primaryKeyType = Enum.valueOf(constraintType.asSubclass(Enum.class), "tpPK");
      foreignKeyType = Enum.valueOf(constraintType.asSubclass(Enum.class), "tpFK");
      editorConstructor = editor.getConstructor();
      diagramConstructor = logical.getConstructor(editor);
      tableConstructor = table.getConstructor(diagram);
      columnConstructor = column.getConstructor(table);
      constraintConstructor = constraint.getConstructor(table);
      lineConstructor = line.getConstructor(diagram);
      guardConstructor = guard.getConstructor(diagram);
      setDiagramName = diagram.getMethod("setNome", String.class);
      setText = form.getMethod("setTexto", String.class);
      setBounds = element.getMethod("SetBounds", int.class, int.class, int.class, int.class);
      reframe = element.getMethod("Reenquadre");
      getLeft = element.getMethod("getLeft");
      getTop = element.getMethod("getTop");
      getWidth = element.getMethod("getWidth");
      getHeight = element.getMethod("getHeight");
      setColumnText = column.getMethod("setTexto", String.class);
      setColumnType = column.getMethod("setTipo", String.class);
      setColumnKey = column.getMethod("setKey", boolean.class);
      setColumnForeignKey = column.getMethod("setFkey", boolean.class);
      setConstraintType = constraint.getMethod("setTipo", constraintType);
      setConstraintName = constraint.getMethod("setNome", String.class);
      setConstraintNamed = constraint.getMethod("setNomeada", boolean.class);
      addConstraintPair = constraint.getMethod("Add", column, column);
      addForeignKeyPair = constraint.getMethod("Add", column, column, line, constraint);
      validateConstraint = constraint.getMethod("Valide");
      startLine = line.getMethod("SuperInicie", int.class, Point.class, Point.class);
      getFirstPoint = baseLine.getMethod("getPontaA");
      getSecondPoint = baseLine.getMethod("getPontaB");
      setPointOwner = point.getMethod("SetEm", form);
      addOwnerLink = form.getMethod("maisLigacao", point);
      positionPoint = form.getMethod("PosicionePonto", point);
      organizeLine = baseLine.getMethod("OrganizeLinha");
      prepareCardinality = line.getMethod("PrepareCardinalidade");
      getCardA = line.getMethod("getCardA");
      getCardB = line.getMethod("getCardB");
      setCardinality = logicalCardinality.getMethod("setCard", cardinalityEnum);
      adjustArrow = line.getMethod("ajusteSeta");
    }
  }
}
