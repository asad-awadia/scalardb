package com.scalar.db.api;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.scalar.db.io.Column;
import com.scalar.db.io.Key;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/** A command to insert an entry into the underlying storage. */
@NotThreadSafe
public class Insert extends Mutation {

  private final ImmutableMap<String, Column<?>> columns;

  Insert(
      @Nullable String namespace,
      String tableName,
      Key partitionKey,
      @Nullable Key clusteringKey,
      ImmutableMap<String, String> attributes,
      ImmutableMap<String, Column<?>> columns) {
    super(namespace, tableName, partitionKey, clusteringKey, null, attributes, null);
    this.columns = columns;
  }

  public Map<String, Column<?>> getColumns() {
    return columns;
  }

  /** @deprecated As of release 3.13.0. Will be removed in release 5.0.0. */
  @Deprecated
  @Nonnull
  @Override
  public Optional<MutationCondition> getCondition() {
    throw new UnsupportedOperationException();
  }

  /** @deprecated As of release 3.6.0. Will be removed in release 5.0.0. */
  @Deprecated
  @Override
  public Mutation withCondition(MutationCondition condition) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Consistency getConsistency() {
    throw new UnsupportedOperationException();
  }

  /** @deprecated As of release 3.6.0. Will be removed in release 5.0.0 */
  @Deprecated
  @Override
  public Operation withConsistency(Consistency consistency) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(OperationVisitor v) {
    v.visit(this);
  }

  /**
   * Indicates whether some other object is "equal to" this object. The other object is considered
   * equal if:
   *
   * <ul>
   *   <li>both super class instances are equal and
   *   <li>it is also an {@code Insert} and
   *   <li>both instances have the same values
   * </ul>
   *
   * @param o an object to be tested for equality
   * @return {@code true} if the other object is "equal to" this object otherwise {@code false}
   */
  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    if (o == this) {
      return true;
    }
    if (!(o instanceof Insert)) {
      return false;
    }
    Insert other = (Insert) o;
    return columns.equals(other.columns);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), columns);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("namespace", forNamespace())
        .add("table", forTable())
        .add("partitionKey", getPartitionKey())
        .add("clusteringKey", getClusteringKey())
        .add("attributes", getAttributes())
        .add("columns", getColumns())
        .toString();
  }

  /**
   * Build a {@code Insert} operation using a builder.
   *
   * @return a {@code Insert} operation builder
   */
  public static InsertBuilder.Namespace newBuilder() {
    return new InsertBuilder.Namespace();
  }

  /**
   * Build a {@code Insert} operation from an existing {@code Insert} object using a builder. The
   * builder will be parametrized by default with all the existing {@code Insert} parameters.
   *
   * @param insert an existing {@code Insert} operation
   * @return a {@code Insert} operation builder
   */
  public static InsertBuilder.BuildableFromExisting newBuilder(Insert insert) {
    checkNotNull(insert);
    return new InsertBuilder.BuildableFromExisting(insert);
  }
}
