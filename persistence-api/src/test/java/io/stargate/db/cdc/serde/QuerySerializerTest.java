/*
 * Copyright The Stargate Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.stargate.db.cdc.serde;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.stargate.db.cdc.api.MutationEventType;
import io.stargate.db.cdc.serde.avro.SchemaConstants;
import io.stargate.db.query.*;
import io.stargate.db.schema.Column;
import io.stargate.db.schema.Table;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.junit.jupiter.api.Test;

class QuerySerializerTest {

  public static final Table EMPTY_TABLE =
      Table.create("ks", "table", Collections.emptyList(), Collections.emptyList());

  @Test
  @SuppressWarnings("unchecked")
  public void shouldSerializeBoundDMLQueryTable() throws IOException {
    // given
    BoundDMLQuery boundDMLQuery =
        createBoundDMLQuery(
            Table.create(
                "ks_1",
                "table_1",
                Arrays.asList(
                    Column.create(
                        "pk_1", Column.Kind.PartitionKey, Column.Type.Ascii, Column.Order.ASC),
                    Column.create("col_1", Column.Kind.Regular, Column.Type.Int),
                    Column.create("col_2", Column.Kind.Regular),
                    Column.create("col_3", Column.Type.Counter)),
                Collections.emptyList()));

    // when
    ByteBuffer byteBuffer = QuerySerializer.serializeQuery(boundDMLQuery);

    // then
    assertThat(byteBuffer.array().length).isGreaterThan(0);
    GenericRecord result = toGenericRecord(byteBuffer);
    // validate table
    GenericRecord table = (GenericRecord) result.get(SchemaConstants.MUTATION_EVENT_TABLE);
    assertThat(table.get(SchemaConstants.TABLE_KEYSPACE).toString()).isEqualTo("ks_1");
    assertThat(table.get(SchemaConstants.TABLE_NAME).toString()).isEqualTo("table_1");

    // validate columns
    GenericData.Array<GenericData.Record> columns =
        (GenericData.Array) table.get(SchemaConstants.TABLE_COLUMNS);
    assertThat(columns.size()).isEqualTo(4);
    validateColumn(columns.get(0), 1, "ASC", "PartitionKey", "pk_1");
    validateColumn(columns.get(1), 9, null, "Regular", "col_1");
    validateColumn(columns.get(2), null, null, "Regular", "col_2");
    validateColumn(columns.get(3), 5, null, "Regular", "col_3");
  }

  @Test
  public void shouldSerializeBoundDMLQueryTimestampAndTTL() throws IOException {
    // given
    BoundDMLQuery boundDMLQuery = createBoundDMLQuery(OptionalInt.of(100), OptionalLong.of(10000L));

    // when
    ByteBuffer byteBuffer = QuerySerializer.serializeQuery(boundDMLQuery);

    // then
    GenericRecord result = toGenericRecord(byteBuffer);
    assertThat(result.get(SchemaConstants.MUTATION_EVENT_TTL)).isEqualTo(100);
    assertThat(result.get(SchemaConstants.MUTATION_EVENT_TIMESTAMP)).isEqualTo(10000L);
  }

  @Test
  public void shouldSerializeBoundDMLQueryTimestampAndTTLNull() throws IOException {
    // given
    BoundDMLQuery boundDMLQuery = createBoundDMLQuery(OptionalInt.empty(), OptionalLong.empty());

    // when
    ByteBuffer byteBuffer = QuerySerializer.serializeQuery(boundDMLQuery);

    // then
    GenericRecord result = toGenericRecord(byteBuffer);
    assertThat(result.get(SchemaConstants.MUTATION_EVENT_TTL)).isNull();
    assertThat(result.get(SchemaConstants.MUTATION_EVENT_TIMESTAMP)).isNull();
  }

  @Test
  public void shouldSerializeBoundDMLQueryUpdateType() throws IOException {
    // given
    BoundDMLQuery boundDMLQuery = createBoundDMLQuery(QueryType.UPDATE);

    // when
    ByteBuffer byteBuffer = QuerySerializer.serializeQuery(boundDMLQuery);

    // then
    GenericRecord result = toGenericRecord(byteBuffer);
    assertThat(result.get(SchemaConstants.MUTATION_EVENT_TYPE).toString())
        .isEqualTo(MutationEventType.UPDATE.name());
  }

  @Test
  public void shouldSerializeBoundDMLQueryDeleteType() throws IOException {
    // given
    BoundDMLQuery boundDMLQuery = createBoundDMLQuery(QueryType.DELETE);

    // when
    ByteBuffer byteBuffer = QuerySerializer.serializeQuery(boundDMLQuery);

    // then
    GenericRecord result = toGenericRecord(byteBuffer);
    assertThat(result.get(SchemaConstants.MUTATION_EVENT_TYPE).toString())
        .isEqualTo(MutationEventType.DELETE.name());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldSerializePartitionKeys() throws IOException {
    // given
    Column pk1 = Column.create("pk_1", Column.Kind.PartitionKey, Column.Type.Int);
    Column pk2 = Column.create("pk_2", Column.Kind.PartitionKey, Column.Type.Boolean);
    Table table =
        Table.create(
            "ks_1",
            "table_1",
            Arrays.asList(
                pk1,
                pk2,
                Column.create("ck_1", Column.Kind.Clustering, Column.Type.Ascii),
                Column.create("col_1", Column.Kind.Regular, Column.Type.Int),
                Column.create("static", Column.Kind.Static, Column.Type.Int)),
            Collections.emptyList());
    BoundDMLQuery boundDMLQuery =
        createBoundDMLQuery(
            table,
            Arrays.asList(
                new PrimaryKey(
                    table,
                    Arrays.asList(
                        TypedValue.forJavaValue(
                            TypedValue.Codec.testCodec(), "pk_1", Column.Type.Int, 1),
                        TypedValue.forJavaValue(
                            TypedValue.Codec.testCodec(), "pk_2", Column.Type.Boolean, true),
                        TypedValue.forJavaValue(
                            TypedValue.Codec.testCodec(), "ck_1", Column.Type.Ascii, "v")))));

    // when
    ByteBuffer byteBuffer = QuerySerializer.serializeQuery(boundDMLQuery);

    // then
    GenericRecord result = toGenericRecord(byteBuffer);
    GenericData.Array<GenericData.Record> partitionKeys =
        (GenericData.Array) result.get(SchemaConstants.MUTATION_EVENT_PARTITION_KEYS);
    assertThat(partitionKeys.size()).isEqualTo(2);
    // validate PKs columns
    validateColumn(
        (GenericData.Record) partitionKeys.get(0).get(SchemaConstants.CELL_VALUE_COLUMN),
        pk1.type().id(),
        null,
        pk1.kind().name(),
        pk1.name());
    validateColumn(
        (GenericData.Record) partitionKeys.get(1).get(SchemaConstants.CELL_VALUE_COLUMN),
        pk2.type().id(),
        null,
        pk2.kind().name(),
        pk2.name());
    // validate if byte buffers carry correct data
    validateColumnValue(
        (ByteBuffer) partitionKeys.get(0).get(SchemaConstants.CELL_VALUE_VALUE),
        pk1.type().id(),
        1);
    validateColumnValue(
        (ByteBuffer) partitionKeys.get(1).get(SchemaConstants.CELL_VALUE_VALUE),
        pk2.type().id(),
        true);
  }

  private void validateColumnValue(ByteBuffer byteBuffer, int typeId, Object expected) {
    Column.Type type = Column.Type.fromId(typeId);
    TypedValue typedValue =
        TypedValue.forBytesValue(TypedValue.Codec.testCodec(), type, byteBuffer);
    assertThat(typedValue.javaValue()).isEqualTo(expected);
  }

  private void validateColumn(
      GenericData.Record column, Integer typeId, String order, String kind, String name) {
    assertThat(Optional.ofNullable(column.get(SchemaConstants.COLUMN_TYPE_ID)).orElse(null))
        .isEqualTo(typeId);
    assertThat(
            Optional.ofNullable(column.get(SchemaConstants.COLUMN_ORDER))
                .map(Object::toString)
                .orElse(null))
        .isEqualTo(order);
    assertThat(
            Optional.ofNullable(column.get(SchemaConstants.COLUMN_KIND))
                .map(Object::toString)
                .orElse(null))
        .isEqualTo(kind);
    assertThat(column.get(SchemaConstants.COLUMN_NAME).toString()).isEqualTo(name);
  }

  private GenericRecord toGenericRecord(ByteBuffer byteBuffer) throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(byteBuffer.array());
    DecoderFactory decoderFactory = DecoderFactory.get();
    BinaryDecoder decoder = decoderFactory.directBinaryDecoder(in, null);

    return new GenericDatumReader<GenericRecord>(SchemaConstants.MUTATION_EVENT)
        .read(null, decoder);
  }

  private BoundDMLQuery createBoundDMLQuery(Table table, List<PrimaryKey> primaryKeys) {
    return createBoundDMLQuery(
        table, OptionalInt.empty(), OptionalLong.empty(), QueryType.UPDATE, primaryKeys);
  }

  private BoundDMLQuery createBoundDMLQuery(QueryType queryType) {
    return createBoundDMLQuery(EMPTY_TABLE, OptionalInt.empty(), OptionalLong.empty(), queryType);
  }

  private BoundDMLQuery createBoundDMLQuery(OptionalInt ttl, OptionalLong timestamp) {
    return createBoundDMLQuery(EMPTY_TABLE, ttl, timestamp);
  }

  private BoundDMLQuery createBoundDMLQuery(Table table) {
    return createBoundDMLQuery(table, OptionalInt.empty(), OptionalLong.empty());
  }

  private BoundDMLQuery createBoundDMLQuery(Table table, OptionalInt ttl, OptionalLong timestamp) {
    return createBoundDMLQuery(table, ttl, timestamp, QueryType.UPDATE, Collections.emptyList());
  }

  private BoundDMLQuery createBoundDMLQuery(
      Table table, OptionalInt ttl, OptionalLong timestamp, QueryType queryType) {
    return createBoundDMLQuery(table, ttl, timestamp, queryType, Collections.emptyList());
  }

  private BoundDMLQuery createBoundDMLQuery(
      Table table,
      OptionalInt ttl,
      OptionalLong timestamp,
      QueryType queryType,
      List<PrimaryKey> primaryKeys) {
    return new BoundDMLQuery() {
      @Override
      public QueryType type() {
        return queryType;
      }

      @Override
      public Source<?> source() {
        return null;
      }

      @Override
      public List<TypedValue> values() {
        return Collections.emptyList();
      }

      @Override
      public Table table() {
        return table;
      }

      @Override
      public RowsImpacted rowsUpdated() {
        return new RowsImpacted.Keys(primaryKeys);
      }

      @Override
      public List<Modification> modifications() {
        return Collections.emptyList();
      }

      @Override
      public OptionalInt ttl() {
        return ttl;
      }

      @Override
      public OptionalLong timestamp() {
        return timestamp;
      }
    };
  }
}
