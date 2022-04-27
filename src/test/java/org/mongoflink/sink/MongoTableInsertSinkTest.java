package org.mongoflink.sink;

import com.google.common.collect.Lists;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;
import org.apache.flink.util.CollectionUtil;
import org.bson.Document;
import org.junit.Assert;
import org.junit.Test;
import org.mongoflink.internal.connection.MongoClientProvider;
import org.mongoflink.internal.connection.MongoColloctionProviders;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class MongoTableInsertSinkTest extends MongoSinkTestBase {


    protected static String DATABASE_NAME = "sink_test";
    protected static String SOURCE_COLLECTION = "batch_source";
    protected static String SINK_COLLECTION = "batch_sink";

    @Test
    public void testTableSink() throws ExecutionException, InterruptedException {

        MongoClientProvider clientProviderSource = MongoColloctionProviders.getBuilder()
                .connectionString(CONNECT_STRING)
                .database(DATABASE_NAME)
                .collection(SOURCE_COLLECTION).build();

        // generate documents
        List<Document> sourceDocs = Lists.newArrayList();

        for (int i = 0; i < 10; i++) {

            sourceDocs.add(
                    new Document()
                            .append("user_id", i)
                            .append("gold", i)
                            .append("level", i)
                            .append("grade", "grade" + (i))
                            .append("class", "class" + i)
                            .append("score", (i) / 1.5)
                            .append("sex", (i) % 2 == 0)
                            .append("hobby", (i) % 10 == 0 ? "football" : null)
            );
        }
        clientProviderSource.getDefaultCollection().insertMany(sourceDocs);

        TableEnvironment env = TableEnvironment.create(EnvironmentSettings.inBatchMode());
        env.executeSql("create table mongo_test_source (" +
                "    user_id int," +
                "    gold int," +
                "    level int," +
                "    grade string," +
                "    class string," +
                "    score double," +
                "    sex boolean," +
                "    hobby string" +
                ") with (" +
                "    'connector'='mongo'," +
                "    'connect_string' = '" + CONNECT_STRING + "'," +
                "    'database' = '" + DATABASE_NAME + "'," +
                "    'collection' = '" + SOURCE_COLLECTION + "'" +
                ")"
        );

        // primary key for upsert.

        env.executeSql("create table mongo_test_sink (" +
                "    user_id int," +
                "    gold int," +
                "    level int," +
                "    grade string," +
                "    class string," +
                "    score double," +
                "    sex boolean," +
                "    hobby string" +
                ") with (" +
                "    'connector'='mongo'," +
                "    'connect_string' = '" + CONNECT_STRING + "'," +
                "    'database' = '" + DATABASE_NAME + "'," +
                "    'collection' = '" + SINK_COLLECTION + "'" +
                ")"
        );

        TableResult tableResult = env.executeSql("insert into mongo_test_sink select * from mongo_test_source");
        tableResult.await();

        env.executeSql("create table mongo_test_source_1 (" +
                "    user_id int," +
                "    gold int," +
                "    level int," +
                "    grade string," +
                "    class string," +
                "    score double," +
                "    sex boolean," +
                "    hobby string" +
                ") with (" +
                "    'connector'='mongo'," +
                "    'connect_string' = '" + CONNECT_STRING + "'," +
                "    'database' = '" + DATABASE_NAME + "'," +
                "    'collection' = '" + SINK_COLLECTION + "'" +
                ")"
        );

        CloseableIterator<Row> collected = env.executeSql("select level from mongo_test_source_1 where user_id = 0").collect();
        List<String> result =
                CollectionUtil.iteratorToList(collected).stream()
                        .map(Row::toString)
                        .sorted()
                        .collect(Collectors.toList());
        Assert.assertEquals("insert failed.", "+I[0]", result.get(0));
    }
}

