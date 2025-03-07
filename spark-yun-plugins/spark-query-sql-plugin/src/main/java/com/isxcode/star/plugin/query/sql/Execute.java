package com.isxcode.star.plugin.query.sql;

import com.alibaba.fastjson.JSON;
import com.isxcode.star.api.agent.req.PluginReq;
import com.isxcode.star.api.func.constants.FuncType;
import org.apache.logging.log4j.util.Strings;
import org.apache.spark.SparkConf;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;

import java.util.*;
import java.util.stream.Collectors;

public class Execute {

    public static void main(String[] args) {

        // 解析插件请求体
        PluginReq pluginReq = parse(args);

        // 过滤注释
        String[] sqls = pluginReq.getSql().split(";");

        // 获取sparkSession
        try (SparkSession sparkSession = initSparkSession(pluginReq)) {

            // 注册自定义函数
            if (pluginReq.getFuncInfoList() != null) {
                pluginReq.getFuncInfoList().forEach(e -> {
                    if (FuncType.UDF.equals(e.getType())) {
                        sparkSession.udf().registerJava(e.getFuncName(), e.getClassName(),
                            getResultType(e.getResultType()));
                    } else if (FuncType.UDAF.equals(e.getType())) {
                        sparkSession.udf().registerJavaUDAF(e.getFuncName(), e.getClassName());
                    }
                });
            }

            // 选择db
            if (!Strings.isEmpty(pluginReq.getDatabase())) {
                sparkSession.sql("use " + pluginReq.getDatabase());
            }

            // 过滤掉所有掉空sql
            List<String> executeSql = Arrays.stream(sqls).filter(Strings::isNotBlank).collect(Collectors.toList());

            // 除了最后一行sql，执行其他所有sql
            for (int i = 0; i < executeSql.size() - 1; i++) {
                sparkSession.sql(executeSql.get(i));
            }

            // 执行最后一行sql并打印输出
            if (executeSql.isEmpty()) {
                exportResult(null);
            } else {
                Dataset<Row> rowDataset =
                    sparkSession.sql(executeSql.get(executeSql.size() - 1)).limit(pluginReq.getLimit());
                exportResult(rowDataset);
            }
        }
    }

    private static DataType getResultType(String resultType) {
        switch (resultType) {
            case "string":
                return DataTypes.StringType;
            case "int":
                return DataTypes.IntegerType;
            case "long":
                return DataTypes.LongType;
            case "double":
                return DataTypes.DoubleType;
            case "boolean":
                return DataTypes.BooleanType;
            case "date":
                return DataTypes.DateType;
            case "timestamp":
                return DataTypes.TimestampType;
            default:
                return DataTypes.StringType;
        }
    }

    public static PluginReq parse(String[] args) {
        if (args.length == 0) {
            throw new RuntimeException("args is empty");
        }
        return JSON.parseObject(Base64.getDecoder().decode(args[0]), PluginReq.class);
    }

    public static SparkSession initSparkSession(PluginReq pluginReq) {

        SparkSession.Builder sparkSessionBuilder = SparkSession.builder();

        SparkConf conf = new SparkConf();
        if (pluginReq.getSparkConfig() != null) {
            for (Map.Entry<String, String> entry : pluginReq.getSparkConfig().entrySet()) {
                conf.set(entry.getKey(), entry.getValue());
            }
        }

        if (pluginReq.getSparkConfig() != null
            && Strings.isEmpty(pluginReq.getSparkConfig().get("hive.metastore.uris"))) {
            return sparkSessionBuilder.config(conf).getOrCreate();
        } else {
            return sparkSessionBuilder.config(conf).enableHiveSupport().getOrCreate();
        }
    }

    public static void exportResult(Dataset<Row> rowDataset) {

        List<List<String>> result = new ArrayList<>();

        if (rowDataset == null) {
            System.out.println("LogType:spark-yun\n" + JSON.toJSONString(result) + "\nEnd of LogType:spark-yun");
            return;
        }

        // 表头
        result.add(Arrays.asList(rowDataset.columns()));

        // 数据
        rowDataset.collectAsList().forEach(e -> {
            List<String> metaData = new ArrayList<>();
            for (int i = 0; i < e.size(); i++) {
                metaData.add(String.valueOf(e.get(i)));
            }
            result.add(metaData);
        });

        System.out.println("LogType:spark-yun\n" + JSON.toJSONString(result) + "\nEnd of LogType:spark-yun");
    }
}
