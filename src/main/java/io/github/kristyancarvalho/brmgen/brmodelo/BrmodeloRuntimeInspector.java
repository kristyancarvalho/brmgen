package io.github.kristyancarvalho.brmgen.brmodelo;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

public final class BrmodeloRuntimeInspector {
  public CompatibilityReport inspect(Path jar) throws BrmodeloException {
    List<String> missing = new ArrayList<>();
    try (URLClassLoader loader = loader(jar)) {
      Class<?> diagram = requiredClass(loader, "controlador.Diagrama", missing);
      Class<?> editor = requiredClass(loader, "controlador.Editor", missing);
      Class<?> conceptual =
          requiredClass(loader, "diagramas.conceitual.DiagramaConceitual", missing);
      Class<?> entity = requiredClass(loader, "diagramas.conceitual.Entidade", missing);
      Class<?> logical = requiredClass(loader, "diagramas.logico.DiagramaLogico", missing);
      Class<?> table = requiredClass(loader, "diagramas.logico.Tabela", missing);
      Class<?> column = requiredClass(loader, "diagramas.logico.Campo", missing);
      Class<?> constraint = requiredClass(loader, "diagramas.logico.Constraint", missing);
      Class<?> logicalLine = requiredClass(loader, "diagramas.logico.LogicoLinha", missing);
      Class<?> shape = requiredClass(loader, "desenho.FormaElementar", missing);
      Class<?> guard = requiredClass(loader, "controlador.apoios.GuardaPadraoBrM", missing);
      requireConstructor(editor, missing);
      requireConstructor(conceptual, missing, editor);
      requireConstructor(entity, missing, diagram);
      requireConstructor(logical, missing, editor);
      requireConstructor(table, missing, diagram);
      requireConstructor(column, missing, table);
      requireConstructor(constraint, missing, table);
      requireConstructor(logicalLine, missing, diagram);
      requireConstructor(guard, missing, diagram);
      requireMethod(diagram, missing, "Add", shape);
      requireMethod(entity, missing, "setTexto", String.class);
      requireMethod(entity, missing, "setLocation", int.class, int.class);
      requireMethod(table, missing, "getCampos");
      requireMethod(table, missing, "getConstraints");
      requireMethod(column, missing, "setKey", boolean.class);
      requireMethod(column, missing, "setFkey", boolean.class);
      requireMethod(constraint, missing, "Add", column, column);
      requireMethod(logicalLine, missing, "PrepareCardinalidade");
      requireMethod(guard, missing, "getDiagrama");
    } catch (IOException | LinkageError exception) {
      throw new BrmodeloException(
          "error[E203]: cannot inspect brModelo runtime `" + jar + "`: " + exception.getMessage(),
          exception);
    }
    return new CompatibilityReport(jar, detectedVersion(jar), missing);
  }

  public void requireCompatible(Path jar) throws BrmodeloException {
    CompatibilityReport report = inspect(jar);
    if (!report.compatible()) {
      throw new BrmodeloException(
          "error[E204]: incompatible brModelo runtime `"
              + jar
              + "`; missing: "
              + String.join(", ", report.missingCapabilities()));
    }
  }

  public static URLClassLoader loader(Path jar) throws IOException {
    return new URLClassLoader(
        new java.net.URL[] {jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
  }

  private Class<?> requiredClass(
      ClassLoader loader, String name, List<String> missingCapabilities) {
    try {
      return Class.forName(name, false, loader);
    } catch (ClassNotFoundException | LinkageError exception) {
      missingCapabilities.add(name);
      return null;
    }
  }

  private void requireConstructor(
      Class<?> type, List<String> missingCapabilities, Class<?>... parameters) {
    if (type == null || containsNull(parameters)) {
      return;
    }
    try {
      type.getConstructor(parameters);
    } catch (NoSuchMethodException exception) {
      missingCapabilities.add(type.getName() + " constructor");
    }
  }

  private void requireMethod(
      Class<?> type, List<String> missingCapabilities, String name, Class<?>... parameters) {
    if (type == null || containsNull(parameters)) {
      return;
    }
    try {
      type.getMethod(name, parameters);
    } catch (NoSuchMethodException exception) {
      missingCapabilities.add(type.getName() + "." + name);
    }
  }

  private boolean containsNull(Class<?>[] parameters) {
    for (Class<?> parameter : parameters) {
      if (parameter == null) {
        return true;
      }
    }
    return false;
  }

  private String detectedVersion(Path jar) {
    try (JarFile file = new JarFile(jar.toFile())) {
      String version = file.getManifest().getMainAttributes().getValue("Implementation-Version");
      return version == null || version.isBlank() ? "unavailable" : version;
    } catch (IOException | NullPointerException exception) {
      return "unavailable";
    }
  }
}
