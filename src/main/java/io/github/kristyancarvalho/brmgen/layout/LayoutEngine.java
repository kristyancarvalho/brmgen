package io.github.kristyancarvalho.brmgen.layout;

import io.github.kristyancarvalho.brmgen.model.Connection;
import io.github.kristyancarvalho.brmgen.model.Entity;
import io.github.kristyancarvalho.brmgen.model.Generalization;
import io.github.kristyancarvalho.brmgen.model.ModelDefinition;
import io.github.kristyancarvalho.brmgen.model.Position;
import io.github.kristyancarvalho.brmgen.model.Relationship;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LayoutEngine {
  private static final int START_X = 80;
  private static final int START_Y = 80;
  private static final int COLUMN_GAP = 280;
  private static final int ROW_GAP = 200;
  private static final int CLEARANCE_X = 180;
  private static final int CLEARANCE_Y = 120;
  private static final int VERTICAL_SHIFT = 140;

  public ModelDefinition layout(ModelDefinition model) {
    List<Position> occupied = new ArrayList<>();
    model.entities().stream()
        .map(Entity::position)
        .filter(position -> position != null)
        .forEach(occupied::add);

    List<Entity> entities = positionEntities(model.entities(), occupied);
    Map<String, Position> entityPositions = new LinkedHashMap<>();
    entities.forEach(entity -> entityPositions.putIfAbsent(entity.name(), entity.position()));

    List<Relationship> relationships =
        positionRelationships(model.relationships(), entityPositions, occupied);
    List<Generalization> generalizations =
        positionGeneralizations(model.generalizations(), entityPositions, occupied);

    return new ModelDefinition(
        model.version(), model.diagram(), entities, relationships, generalizations);
  }

  private List<Entity> positionEntities(List<Entity> entities, List<Position> occupied) {
    List<Entity> positioned = new ArrayList<>(entities.size());
    int slot = 0;
    for (Entity entity : entities) {
      Position position = entity.position();
      if (position == null) {
        Slot selected = nextGridSlot(slot, occupied);
        slot = selected.nextSlot();
        position = selected.position();
        occupied.add(position);
      }
      positioned.add(new Entity(entity.name(), entity.weak(), entity.attributes(), position));
    }
    return List.copyOf(positioned);
  }

  private List<Relationship> positionRelationships(
      List<Relationship> relationships,
      Map<String, Position> entityPositions,
      List<Position> occupied) {
    List<Relationship> positioned = new ArrayList<>(relationships.size());
    for (Relationship relationship : relationships) {
      Position position = relationship.position();
      if (position == null) {
        List<Position> participants =
            relationship.connections().stream()
                .map(Connection::entity)
                .map(entityPositions::get)
                .filter(candidate -> candidate != null)
                .toList();
        position = findAvailable(average(participants, occupied.size()), occupied);
        occupied.add(position);
      }
      positioned.add(
          new Relationship(
              relationship.name(),
              relationship.identifying(),
              relationship.connections(),
              relationship.attributes(),
              position));
    }
    return List.copyOf(positioned);
  }

  private List<Generalization> positionGeneralizations(
      List<Generalization> generalizations,
      Map<String, Position> entityPositions,
      List<Position> occupied) {
    List<Generalization> positioned = new ArrayList<>(generalizations.size());
    for (Generalization generalization : generalizations) {
      Position position = generalization.position();
      if (position == null) {
        List<Position> participants = new ArrayList<>();
        addPosition(participants, entityPositions.get(generalization.parent()));
        generalization.children().stream()
            .map(entityPositions::get)
            .forEach(candidate -> addPosition(participants, candidate));
        position = findAvailable(average(participants, occupied.size()), occupied);
        occupied.add(position);
      }
      positioned.add(
          new Generalization(
              generalization.parent(),
              generalization.children(),
              generalization.total(),
              generalization.disjoint(),
              position));
    }
    return List.copyOf(positioned);
  }

  private void addPosition(List<Position> positions, Position position) {
    if (position != null) {
      positions.add(position);
    }
  }

  private Position average(List<Position> positions, int fallbackSlot) {
    if (positions.isEmpty()) {
      return gridPosition(fallbackSlot);
    }
    long x = 0;
    long y = 0;
    for (Position position : positions) {
      x += position.x();
      y += position.y();
    }
    return new Position((int) (x / positions.size()), (int) (y / positions.size()));
  }

  private Position findAvailable(Position preferred, List<Position> occupied) {
    Position candidate = preferred;
    int attempts = 0;
    while (overlaps(candidate, occupied)) {
      attempts++;
      long shiftedY = (long) preferred.y() + (long) attempts * VERTICAL_SHIFT;
      if (shiftedY > Integer.MAX_VALUE) {
        return nextGridSlot(occupied.size(), occupied).position();
      }
      candidate = new Position(preferred.x(), (int) shiftedY);
    }
    return candidate;
  }

  private Slot nextGridSlot(int initialSlot, List<Position> occupied) {
    int slot = initialSlot;
    Position candidate = gridPosition(slot);
    while (overlaps(candidate, occupied)) {
      slot++;
      candidate = gridPosition(slot);
    }
    return new Slot(candidate, slot + 1);
  }

  private Position gridPosition(int slot) {
    int columns = 4;
    return new Position(
        START_X + (slot % columns) * COLUMN_GAP, START_Y + (slot / columns) * ROW_GAP);
  }

  private boolean overlaps(Position candidate, List<Position> occupied) {
    return occupied.stream()
        .anyMatch(
            position ->
                Math.abs((long) candidate.x() - position.x()) < CLEARANCE_X
                    && Math.abs((long) candidate.y() - position.y()) < CLEARANCE_Y);
  }

  private record Slot(Position position, int nextSlot) {}
}
