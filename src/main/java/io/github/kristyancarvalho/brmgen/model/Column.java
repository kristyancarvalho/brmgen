package io.github.kristyancarvalho.brmgen.model;

public record Column(String name, String type, boolean primaryKey, boolean nullable) {}
