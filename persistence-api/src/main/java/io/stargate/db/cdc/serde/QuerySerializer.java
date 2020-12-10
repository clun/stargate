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

import io.stargate.db.cdc.api.CellValue;
import io.stargate.db.cdc.api.MutationEvent;
import io.stargate.db.cdc.api.MutationEventBuilder;
import io.stargate.db.cdc.serde.avro.SchemaConstants;
import io.stargate.db.query.BoundDMLQuery;
import io.stargate.db.schema.Column;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;

public class QuerySerializer {
  public static ByteBuffer serializeQuery(BoundDMLQuery boundDMLQuery) {
    MutationEvent mutationEvent = toMutationEvent(boundDMLQuery);
    Record mutationEventRecord = constructMutationEventGenericRecord(mutationEvent);
    return serializeRecord(mutationEventRecord);
  }

  private static Record constructMutationEventGenericRecord(MutationEvent mutationEvent) {
    List<Record> columns = constructColumns(mutationEvent);
    Record table = constructTable(columns, mutationEvent);
    List<Record> partitionKeys = constructPartitionKeys(mutationEvent);

    Record mutationEventRecord = new Record(SchemaConstants.MUTATION_EVENT);
    mutationEventRecord.put(SchemaConstants.MUTATION_EVENT_TABLE, table);
    mutationEventRecord.put(
        SchemaConstants.MUTATION_EVENT_TTL,
        mutationEvent.ttl().isPresent() ? mutationEvent.ttl().getAsInt() : null);
    mutationEventRecord.put(
        SchemaConstants.MUTATION_EVENT_TIMESTAMP,
        mutationEvent.timestamp().isPresent() ? mutationEvent.timestamp().getAsLong() : null);
    mutationEventRecord.put(
        SchemaConstants.MUTATION_EVENT_TYPE, mutationEvent.mutationEventType().name());
    mutationEventRecord.put(SchemaConstants.MUTATION_EVENT_PARTITION_KEYS, partitionKeys);

    return mutationEventRecord;
  }

  private static List<Record> constructPartitionKeys(MutationEvent mutationEvent) {
    List<Record> partitionKeys = new ArrayList<>();

    for (CellValue cellValue : mutationEvent.getPartitionKeys()) {
      Record cellValueRecord = new Record(SchemaConstants.CELL_VALUE);
      cellValueRecord.put(
          SchemaConstants.CELL_VALUE_COLUMN, constructColumn(cellValue.getColumn()));
      cellValueRecord.put(SchemaConstants.CELL_VALUE_VALUE, cellValue.getValue());
      partitionKeys.add(cellValueRecord);
    }
    return partitionKeys;
  }

  private static ByteBuffer serializeRecord(Record mutationEventRecord) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    EncoderFactory encoderFactory = EncoderFactory.get();
    BinaryEncoder encoder = encoderFactory.directBinaryEncoder(out, null);
    try {
      new GenericDatumWriter<GenericRecord>(SchemaConstants.MUTATION_EVENT)
          .write(mutationEventRecord, encoder);
      return ByteBuffer.wrap(out.toByteArray());
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Problem when serializing mutation event: " + mutationEventRecord, e);
    }
  }

  private static Record constructTable(List<Record> columns, MutationEvent mutationEvent) {
    Record tableRecord = new Record(SchemaConstants.TABLE);
    tableRecord.put(SchemaConstants.TABLE_KEYSPACE, mutationEvent.table().keyspace());
    tableRecord.put(SchemaConstants.TABLE_NAME, mutationEvent.table().name());
    tableRecord.put(SchemaConstants.TABLE_COLUMNS, columns);
    return tableRecord;
  }

  private static List<Record> constructColumns(MutationEvent mutationEvent) {
    List<Record> columns = new ArrayList<>();
    for (Column column : mutationEvent.table().columns()) {
      Record columnRecord = constructColumn(column);
      columns.add(columnRecord);
    }
    return columns;
  }

  private static Record constructColumn(Column column) {
    Record columnRecord = new Record(SchemaConstants.COLUMN);
    columnRecord.put(SchemaConstants.COLUMN_NAME, column.name());
    columnRecord.put(
        SchemaConstants.COLUMN_ORDER,
        Optional.ofNullable(column.order()).map(Enum::name).orElse(null));
    columnRecord.put(
        SchemaConstants.COLUMN_KIND,
        Optional.ofNullable(column.kind()).map(Enum::name).orElse(null));
    columnRecord.put(
        SchemaConstants.COLUMN_TYPE_ID,
        Optional.ofNullable(column.type()).map(Column.ColumnType::id).orElse(null));
    return columnRecord;
  }

  private static MutationEvent toMutationEvent(BoundDMLQuery boundDMLQuery) {
    return new MutationEventBuilder().fromBoundDMLQuery(boundDMLQuery).build();
  }
}
