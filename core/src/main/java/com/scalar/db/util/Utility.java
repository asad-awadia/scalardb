package com.scalar.db.util;

import com.google.common.collect.Streams;
import com.scalar.db.api.Operation;
import com.scalar.db.api.Selection;
import com.scalar.db.api.TableMetadata;
import com.scalar.db.io.Value;
import java.util.List;
import java.util.Optional;

public final class Utility {

  public static void setTargetToIfNot(
      List<? extends Operation> operations,
      Optional<String> namespace,
      Optional<String> tableName) {
    operations.forEach(o -> setTargetToIfNot(o, namespace, tableName));
  }

  public static void setTargetToIfNot(
      List<? extends Operation> operations,
      Optional<String> namespacePrefix,
      Optional<String> namespace,
      Optional<String> tableName) {
    operations.forEach(o -> setTargetToIfNot(o, namespacePrefix, namespace, tableName));
  }

  public static void setTargetToIfNot(
      Operation operation, Optional<String> namespace, Optional<String> tableName) {
    setTargetToIfNot(operation, Optional.empty(), namespace, tableName);
  }

  public static void setTargetToIfNot(
      Operation operation,
      Optional<String> namespacePrefix,
      Optional<String> namespace,
      Optional<String> tableName) {
    if (!operation.forNamespacePrefix().isPresent()) {
      operation.forNamespacePrefix(namespacePrefix.orElse(null));
    }
    if (!operation.forNamespace().isPresent()) {
      operation.forNamespace(namespace.orElse(null));
    }
    if (!operation.forTable().isPresent()) {
      operation.forTable(tableName.orElse(null));
    }
    if (!operation.forNamespace().isPresent() || !operation.forTable().isPresent()) {
      throw new IllegalArgumentException("operation has no target namespace and table name");
    }
  }

  public static boolean isSecondaryIndexSpecified(Operation operation, TableMetadata metadata) {
    List<Value<?>> keyValues = operation.getPartitionKey().get();
    if (keyValues.size() == 1) {
      String name = keyValues.get(0).getName();
      return metadata.getSecondaryIndexNames().contains(name);
    }

    return false;
  }

  public static void addProjectionsForKeys(Selection selection, TableMetadata metadata) {
    if (selection.getProjections().isEmpty()) { // meaning projecting all
      return;
    }
    Streams.concat(
            metadata.getPartitionKeyNames().stream(), metadata.getClusteringKeyNames().stream())
        .filter(n -> !selection.getProjections().contains(n))
        .forEach(selection::withProjection);
  }
}