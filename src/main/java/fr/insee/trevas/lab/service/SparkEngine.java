package fr.insee.trevas.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insee.trevas.lab.model.Body;
import fr.insee.trevas.lab.model.EditVisualize;
import fr.insee.trevas.lab.model.ExecutionType;
import fr.insee.trevas.lab.model.QueriesForBindings;
import fr.insee.trevas.lab.model.QueriesForBindingsToSave;
import fr.insee.trevas.lab.model.S3ForBindings;
import fr.insee.trevas.lab.model.User;
import fr.insee.trevas.lab.utils.Utils;
import fr.insee.vtl.model.Structured;
import fr.insee.vtl.spark.SparkDataset;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.SimpleBindings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConfigurationProperties(prefix = "spark")
public class SparkEngine {

    private static final Logger logger = LogManager.getLogger(SparkEngine.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spark.cluster.master.local}")
    private String sparkClusterMasterLocal;

    @Value("${spark.cluster.master.static}")
    private String sparkClusterMasterStatic;

    @Value("${spark.cluster.master.kubernetes}")
    private String sparkClusterMasterKubernetes;

    private SparkSession buildSparkSession(ExecutionType type, Boolean addJars) throws Exception {
        SparkConf conf = Utils.loadSparkConfig(System.getenv("SPARK_CONF_DIR"));
        SparkSession.Builder sparkBuilder = SparkSession.builder()
                .appName("trevas-lab");
        if (addJars) {
            // Note: all the dependencies are required for deserialization.
            // See https://stackoverflow.com/questions/28079307
            conf.set("spark.jars", String.join(",",
                    "/vtl-spark.jar",
                    "/vtl-model.jar",
                    "/vtl-parser.jar",
                    "/vtl-engine.jar",
                    "/vtl-jackson.jar"
            ));
        }
        sparkBuilder.config(conf);
        if (ExecutionType.LOCAL == type) {
            sparkBuilder
                    .master(sparkClusterMasterLocal);
            return sparkBuilder.getOrCreate();
        } else if (ExecutionType.CLUSTER_STATIC == type) {
            sparkBuilder
                    .master(sparkClusterMasterStatic);
        } else if (ExecutionType.CLUSTER_KUBERNETES == type) {
            sparkBuilder
                    .master(sparkClusterMasterKubernetes);
            return sparkBuilder.getOrCreate();
        }
        throw new Exception("Unknow execution type: " + type);
    }

    private SparkDataset readS3Dataset(SparkSession spark, S3ForBindings s3, Integer limit) throws Exception {
        String path = s3.getUrl();
        String fileType = s3.getFiletype();
        Dataset<Row> dataset;
        try {
            if ("csv".equals(fileType))
                dataset = spark.read()
                        .option("delimiter", ";")
                        .option("header", "true")
                        .csv(path);
            else if ("parquet".equals(fileType)) dataset = spark.read().parquet(path);
            else throw new Exception("Unknow S3 file type: " + fileType);
        } catch (Exception e) {
            throw new Exception("An error has occured while loading: " + path);
        }
        // Explore "take" for efficiency (returns rows)
        if (limit != null) dataset = dataset.limit(limit);
        return new SparkDataset(dataset);
    }

    private SparkDataset readJDBCDataset(SparkSession spark, QueriesForBindings queriesForBindings, Integer limit) throws Exception {
        String jdbcPrefix = "";
        try {
            jdbcPrefix = Utils.getJDBCPrefix(queriesForBindings.getDbtype());
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception(e);
        }
        Dataset<Row> dataset = spark.read().format("jdbc")
                .option("url", jdbcPrefix + queriesForBindings.getUrl())
                .option("user", queriesForBindings.getUser())
                .option("password", queriesForBindings.getPassword())
                .option("query", queriesForBindings.getQuery())
                .option("driver", "net.postgis.jdbc.DriverWrapper")
                .option("driver", "org.postgresql.Driver")
                .load();
        // Explore "take" for efficiency (returns rows)
        if (limit != null) dataset = dataset.limit(limit);
        return new SparkDataset(dataset);
    }

    public Bindings executeSpark(User user, Body body, ExecutionType type) throws Exception {
        String script = body.getVtlScript();
        Map<String, QueriesForBindings> queriesForBindings = body.getQueriesForBindings();
        Map<String, S3ForBindings> s3ForBindings = body.getS3ForBindings();

        SparkSession spark = buildSparkSession(type, true);

        Bindings bindings = new SimpleBindings();

        if (queriesForBindings != null) {
            queriesForBindings.forEach((k, v) -> {
                try {
                    SparkDataset sparkDataset = readJDBCDataset(spark, v, null);
                    bindings.put(k, sparkDataset);
                } catch (Exception e) {
                    logger.warn("Query loading failed: ", e);
                }
            });
        }
        if (s3ForBindings != null) {
            s3ForBindings.forEach((k, v) -> {
                try {
                    SparkDataset sparkDataset = readS3Dataset(spark, v, null);
                    bindings.put(k, sparkDataset);
                } catch (Exception e) {
                    logger.warn("S3 loading failed: ", e);

                }
            });
        }

        ScriptEngine engine = Utils.initEngineWithSpark(bindings, spark);

        try {
            engine.eval(script);
        } catch (Exception e) {
            throw new Exception(e);
        }
        Bindings outputBindings = engine.getContext().getBindings(ScriptContext.ENGINE_SCOPE);

        Map<String, QueriesForBindingsToSave> queriesForBindingsToSave = body.getToSave().getJdbcForBindingsToSave();
        if (null != queriesForBindingsToSave) {
            Utils.writeSparkDatasetsJDBC(outputBindings, queriesForBindingsToSave);
        }

        Map<String, S3ForBindings> s3ToSave = body.getToSave().getS3ForBindings();
        if (null != s3ToSave) {
            Utils.writeSparkS3Datasets(outputBindings, s3ToSave, objectMapper, spark);
        }

        return Utils.getSparkBindings(outputBindings, 1000);
    }

    public ResponseEntity<EditVisualize> getJDBC(
            User user,
            QueriesForBindings queriesForBindings,
            ExecutionType type) throws Exception {

        SparkSession spark = buildSparkSession(type, false);

        fr.insee.vtl.model.Dataset trevasDs = readJDBCDataset(spark, queriesForBindings, 1000);

        EditVisualize editVisualize = new EditVisualize();

        List<Map<String, Object>> structure = new ArrayList<>();

        trevasDs.getDataStructure().entrySet().forEach(e -> {
            Structured.Component component = e.getValue();
            Map<String, Object> row = new HashMap<>();
            row.put("name", component.getName());
            row.put("type", component.getType().getSimpleName());
            // Default has to be handled by Trevas
            row.put("role", "MEASURE");
            structure.add(row);
        });
        editVisualize.setDataStructure(structure);

        editVisualize.setDataPoints(trevasDs.getDataAsList());

        return ResponseEntity.status(HttpStatus.OK)
                .body(editVisualize);
    }

    public ResponseEntity<EditVisualize> getS3(
            User user,
            S3ForBindings s3ForBindings,
            ExecutionType type) throws Exception {

        SparkSession spark = buildSparkSession(type, false);

        EditVisualize editVisualize = new EditVisualize();

        fr.insee.vtl.model.Dataset trevasDs = readS3Dataset(spark, s3ForBindings, 1000);

        List<Map<String, Object>> structure = new ArrayList<>();
        trevasDs.getDataStructure().entrySet().forEach(e -> {
            Structured.Component component = e.getValue();
            Map<String, Object> rowMap = new HashMap<>();
            rowMap.put("name", component.getName());
            rowMap.put("type", component.getType().getSimpleName());
            // Default has to be handled by Trevas
            rowMap.put("role", "MEASURE");
            structure.add(rowMap);
        });
        editVisualize.setDataStructure(structure);
        editVisualize.setDataPoints(trevasDs.getDataAsList());
        return ResponseEntity.status(HttpStatus.OK)
                .body(editVisualize);
    }

}