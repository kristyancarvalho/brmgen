package io.github.kristyancarvalho.brmgen.brmodelo;

import io.github.kristyancarvalho.brmgen.model.Entity;
import io.github.kristyancarvalho.brmgen.model.ModelDefinition;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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
      for (Entity entity : model.entities()) {
        Object nativeEntity = api.entityConstructor.newInstance(diagram);
        api.setEntityText.invoke(nativeEntity, entity.name());
        api.setEntityLocation.invoke(
            nativeEntity, entity.position().x().intValue(), entity.position().y().intValue());
        api.addToDiagram.invoke(diagram, nativeEntity);
      }
      Object guard = api.guardConstructor.newInstance(diagram);
      try (ObjectOutputStream stream =
          new ObjectOutputStream(
              Files.newOutputStream(
                  output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
        stream.writeObject(guard);
      }
    }
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
    boolean unsupportedEntities =
        model.entities().stream()
            .anyMatch(entity -> entity.weak() || !entity.attributes().isEmpty());
    if (unsupportedEntities
        || !model.relationships().isEmpty()
        || !model.generalizations().isEmpty()) {
      throw new BrmodeloException(
          "error[E208]: native generation currently supports regular entities without attributes; "
              + "remove unsupported constructs or wait for the extended adapter");
    }
  }

  private static final class RuntimeApi {
    private final Constructor<?> editorConstructor;
    private final Constructor<?> diagramConstructor;
    private final Constructor<?> entityConstructor;
    private final Constructor<?> guardConstructor;
    private final Method setDiagramName;
    private final Method setEntityText;
    private final Method setEntityLocation;
    private final Method addToDiagram;

    private RuntimeApi(ClassLoader loader) throws ReflectiveOperationException {
      Class<?> editor = loader.loadClass("controlador.Editor");
      Class<?> diagram = loader.loadClass("controlador.Diagrama");
      Class<?> conceptual = loader.loadClass("diagramas.conceitual.DiagramaConceitual");
      Class<?> entity = loader.loadClass("diagramas.conceitual.Entidade");
      Class<?> shape = loader.loadClass("desenho.FormaElementar");
      Class<?> guard = loader.loadClass("controlador.apoios.GuardaPadraoBrM");
      editorConstructor = editor.getConstructor();
      diagramConstructor = conceptual.getConstructor(editor);
      entityConstructor = entity.getConstructor(diagram);
      guardConstructor = guard.getConstructor(diagram);
      setDiagramName = diagram.getMethod("setNome", String.class);
      setEntityText = entity.getMethod("setTexto", String.class);
      setEntityLocation = entity.getMethod("setLocation", int.class, int.class);
      addToDiagram = diagram.getMethod("Add", shape);
    }
  }
}
