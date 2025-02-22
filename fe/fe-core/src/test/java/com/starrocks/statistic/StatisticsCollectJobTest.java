// This file is licensed under the Elastic License 2.0. Copyright 2021 StarRocks Limited.
package com.starrocks.statistic;

import com.google.common.collect.Maps;
import com.starrocks.catalog.Database;
import com.starrocks.catalog.OlapTable;
import com.starrocks.catalog.Partition;
import com.starrocks.common.jmockit.Deencapsulation;
import com.starrocks.server.GlobalStateMgr;
import com.starrocks.sql.plan.PlanTestBase;
import jersey.repackaged.com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatisticsCollectJobTest extends PlanTestBase {
    @BeforeClass
    public static void beforeClass() throws Exception {
        PlanTestBase.beforeClass();
        GlobalStateMgr globalStateMgr = connectContext.getGlobalStateMgr();

        starRocksAssert.withTable("CREATE TABLE `t0_stats` (\n" +
                "  `v1` bigint NULL COMMENT \"\",\n" +
                "  `v2` bigint NULL COMMENT \"\",\n" +
                "  `v3` bigint NULL\n" +
                ") ENGINE=OLAP\n" +
                "DUPLICATE KEY(`v1`, `v2`, v3)\n" +
                "DISTRIBUTED BY HASH(`v1`) BUCKETS 3\n" +
                "PROPERTIES (\n" +
                "\"replication_num\" = \"1\",\n" +
                "\"in_memory\" = \"false\",\n" +
                "\"storage_format\" = \"DEFAULT\"\n" +
                ");");

        OlapTable t0 = (OlapTable) globalStateMgr.getDb("default_cluster:test").getTable("t0_stats");
        Partition partition = new ArrayList<>(t0.getPartitions()).get(0);
        partition.updateVisibleVersion(2, LocalDateTime.of(2022, 1, 1, 1, 1, 1)
                .atZone(Clock.systemDefaultZone().getZone()).toEpochSecond() * 1000);
        setTableStatistics(t0, 20000000);

        starRocksAssert.withTable("CREATE TABLE `t1_stats` (\n" +
                "  `v4` bigint NULL COMMENT \"\",\n" +
                "  `v5` bigint NULL COMMENT \"\",\n" +
                "  `v6` bigint NULL\n" +
                ") ENGINE=OLAP\n" +
                "DUPLICATE KEY(`v4`, `v5`, v6)\n" +
                "DISTRIBUTED BY HASH(`v4`) BUCKETS 3\n" +
                "PROPERTIES (\n" +
                "\"replication_num\" = \"1\",\n" +
                "\"in_memory\" = \"false\",\n" +
                "\"storage_format\" = \"DEFAULT\"\n" +
                ");");

        OlapTable t1 = (OlapTable) globalStateMgr.getDb("default_cluster:test").getTable("t1_stats");
        new ArrayList<>(t1.getPartitions()).get(0).updateVisibleVersion(2);
        setTableStatistics(t1, 20000000);
    }

    @Test
    public void testAnalyzeALLDB() {
        List<StatisticsCollectJob> jobs = StatisticsCollectJobFactory.buildStatisticsCollectJob(
                new AnalyzeJob(StatsConstants.DEFAULT_ALL_ID, StatsConstants.DEFAULT_ALL_ID, null,
                        StatsConstants.AnalyzeType.FULL, StatsConstants.ScheduleType.SCHEDULE,
                        Maps.newHashMap(),
                        StatsConstants.ScheduleStatus.PENDING,
                        LocalDateTime.MIN));
        Assert.assertEquals(2, jobs.size());
        Assert.assertTrue(jobs.get(0) instanceof FullStatisticsCollectJob);
        FullStatisticsCollectJob fullStatisticsCollectJob = (FullStatisticsCollectJob) jobs.get(0);
        Assert.assertEquals("[v1, v2, v3]", fullStatisticsCollectJob.getColumns().toString());
        Assert.assertTrue(jobs.get(1) instanceof FullStatisticsCollectJob);
        fullStatisticsCollectJob = (FullStatisticsCollectJob) jobs.get(1);
        Assert.assertEquals("[v4, v5, v6]", fullStatisticsCollectJob.getColumns().toString());
    }

    @Test
    public void testAnalyzeDB() {
        List<StatisticsCollectJob> jobs = StatisticsCollectJobFactory.buildStatisticsCollectJob(
                new AnalyzeJob(10002, StatsConstants.DEFAULT_ALL_ID, null,
                        StatsConstants.AnalyzeType.FULL, StatsConstants.ScheduleType.SCHEDULE,
                        Maps.newHashMap(),
                        StatsConstants.ScheduleStatus.PENDING,
                        LocalDateTime.MIN));
        Assert.assertEquals(2, jobs.size());
        Assert.assertTrue(jobs.get(0) instanceof FullStatisticsCollectJob);
        FullStatisticsCollectJob fullStatisticsCollectJob = (FullStatisticsCollectJob) jobs.get(0);
        Assert.assertEquals("[v1, v2, v3]", fullStatisticsCollectJob.getColumns().toString());
        Assert.assertTrue(jobs.get(1) instanceof FullStatisticsCollectJob);
        fullStatisticsCollectJob = (FullStatisticsCollectJob) jobs.get(1);
        Assert.assertEquals("[v4, v5, v6]", fullStatisticsCollectJob.getColumns().toString());
    }

    @Test
    public void testAnalyzeTable() {
        List<StatisticsCollectJob> jobs = StatisticsCollectJobFactory.buildStatisticsCollectJob(
                new AnalyzeJob(10002, 16325, null,
                        StatsConstants.AnalyzeType.FULL, StatsConstants.ScheduleType.SCHEDULE,
                        Maps.newHashMap(),
                        StatsConstants.ScheduleStatus.PENDING,
                        LocalDateTime.MIN));
        Assert.assertEquals(1, jobs.size());
        Assert.assertTrue(jobs.get(0) instanceof FullStatisticsCollectJob);
        FullStatisticsCollectJob fullStatisticsCollectJob = (FullStatisticsCollectJob) jobs.get(0);
        Assert.assertEquals("t0_stats", fullStatisticsCollectJob.getTable().getName());
        Assert.assertEquals("[v1, v2, v3]", fullStatisticsCollectJob.getColumns().toString());
    }

    @Test
    public void testAnalyzeColumn() {
        List<StatisticsCollectJob> jobs = StatisticsCollectJobFactory.buildStatisticsCollectJob(
                new AnalyzeJob(10002, 16325, Lists.newArrayList("v2"),
                        StatsConstants.AnalyzeType.FULL, StatsConstants.ScheduleType.SCHEDULE,
                        Maps.newHashMap(),
                        StatsConstants.ScheduleStatus.PENDING,
                        LocalDateTime.MIN));
        Assert.assertEquals(1, jobs.size());
        Assert.assertTrue(jobs.get(0) instanceof FullStatisticsCollectJob);
        FullStatisticsCollectJob fullStatisticsCollectJob = (FullStatisticsCollectJob) jobs.get(0);
        Assert.assertEquals("[v2]", fullStatisticsCollectJob.getColumns().toString());
    }

    @Test
    public void testAnalyzeColumnSample() {
        List<StatisticsCollectJob> jobs = StatisticsCollectJobFactory.buildStatisticsCollectJob(
                new AnalyzeJob(10002, 16325, Lists.newArrayList("v2"),
                        StatsConstants.AnalyzeType.SAMPLE, StatsConstants.ScheduleType.SCHEDULE,
                        Maps.newHashMap(),
                        StatsConstants.ScheduleStatus.PENDING,
                        LocalDateTime.MIN));
        Assert.assertEquals(1, jobs.size());
        Assert.assertTrue(jobs.get(0) instanceof SampleStatisticsCollectJob);
        SampleStatisticsCollectJob sampleStatisticsCollectJob = (SampleStatisticsCollectJob) jobs.get(0);
        Assert.assertEquals("[v2]", sampleStatisticsCollectJob.getColumns().toString());
    }

    @Test
    public void testAnalyzeColumnSample2() {
        Database db = GlobalStateMgr.getCurrentState().getDb(10002);
        OlapTable olapTable = (OlapTable) db.getTable("t0_stats");

        BasicStatsMeta basicStatsMeta = new BasicStatsMeta(10002, olapTable.getId(), StatsConstants.AnalyzeType.SAMPLE,
                LocalDateTime.of(2020, 1, 1, 1, 1, 1), Maps.newHashMap());
        basicStatsMeta.increaseUpdateRows(10000000L);
        GlobalStateMgr.getCurrentAnalyzeMgr().addBasicStatsMeta(basicStatsMeta);

        List<StatisticsCollectJob> jobs = StatisticsCollectJobFactory.buildStatisticsCollectJob(
                new AnalyzeJob(10002, olapTable.getId(), Lists.newArrayList("v2"),
                        StatsConstants.AnalyzeType.SAMPLE, StatsConstants.ScheduleType.SCHEDULE,
                        Maps.newHashMap(),
                        StatsConstants.ScheduleStatus.PENDING,
                        LocalDateTime.MIN));
        Assert.assertEquals(1, jobs.size());

        jobs = StatisticsCollectJobFactory.buildStatisticsCollectJob(
                new AnalyzeJob(10002, olapTable.getId(), Lists.newArrayList("v2"),
                        StatsConstants.AnalyzeType.FULL, StatsConstants.ScheduleType.SCHEDULE,
                        Maps.newHashMap(),
                        StatsConstants.ScheduleStatus.PENDING,
                        LocalDateTime.MIN));
        Assert.assertEquals(1, jobs.size());

        BasicStatsMeta basicStatsMeta2 = new BasicStatsMeta(10002, olapTable.getId(), StatsConstants.AnalyzeType.SAMPLE,
                LocalDateTime.of(2022, 1, 1, 1, 1, 1), Maps.newHashMap());
        GlobalStateMgr.getCurrentAnalyzeMgr().addBasicStatsMeta(basicStatsMeta2);

        List<StatisticsCollectJob> jobs2 = StatisticsCollectJobFactory.buildStatisticsCollectJob(
                new AnalyzeJob(10002, olapTable.getId(), Lists.newArrayList("v2"),
                        StatsConstants.AnalyzeType.SAMPLE, StatsConstants.ScheduleType.SCHEDULE,
                        Maps.newHashMap(),
                        StatsConstants.ScheduleStatus.PENDING,
                        LocalDateTime.MIN));
        Assert.assertEquals(0, jobs2.size());

        jobs2 = StatisticsCollectJobFactory.buildStatisticsCollectJob(
                new AnalyzeJob(10002, olapTable.getId(), Lists.newArrayList("v2"),
                        StatsConstants.AnalyzeType.FULL, StatsConstants.ScheduleType.SCHEDULE,
                        Maps.newHashMap(),
                        StatsConstants.ScheduleStatus.PENDING,
                        LocalDateTime.MIN));
        Assert.assertEquals(1, jobs2.size());
        GlobalStateMgr.getCurrentAnalyzeMgr().getBasicStatsMetaMap().remove(olapTable.getId());
    }

    @Test
    public void testAnalyzeHistogram() {
        Database db = GlobalStateMgr.getCurrentState().getDb(10002);
        OlapTable olapTable = (OlapTable) db.getTable("t0_stats");

        Map<String, String> properties = new HashMap<>();
        properties.put(StatsConstants.HISTOGRAM_SAMPLE_RATIO, "0.1");
        properties.put(StatsConstants.HISTOGRAM_BUCKET_NUM, "64");
        properties.put(StatsConstants.HISTOGRAM_TOPN_SIZE, "100");
        HistogramStatisticsCollectJob histogramStatisticsCollectJob = new HistogramStatisticsCollectJob(
                db, olapTable, Lists.newArrayList("v2"),
                StatsConstants.AnalyzeType.HISTOGRAM, StatsConstants.ScheduleType.ONCE,
                properties);

        String sql = Deencapsulation.invoke(histogramStatisticsCollectJob, "buildCollectHistogram",
                db, olapTable, 0.1, 64L, Maps.newHashMap(), "v2");
        Assert.assertEquals("INSERT INTO histogram_statistics SELECT 16325, 'v2', 10002, 'test.t0_stats'," +
                " histogram(v2, 64, 0.1),  NULL, NOW() FROM " +
                "(SELECT v2 FROM test.t0_stats where rand() <= 0.1 and v2 is not null  " +
                "ORDER BY v2 LIMIT 9223372036854775807) t", sql);

        Map<String, String> mostCommonValues = new HashMap<>();
        mostCommonValues.put("1", "10");
        mostCommonValues.put("2", "20");
        sql = Deencapsulation.invoke(histogramStatisticsCollectJob, "buildCollectHistogram",
                db, olapTable, 0.1, 64L, mostCommonValues, "v2");
        Assert.assertEquals("INSERT INTO histogram_statistics SELECT 16325, 'v2', 10002, 'test.t0_stats', " +
                "histogram(v2, 64, 0.1),  '[[\"1\",\"10\"],[\"2\",\"20\"]]', NOW() " +
                "FROM (SELECT v2 FROM test.t0_stats where rand() <= 0.1 and v2 is not null  and v2 not in (1,2) " +
                "ORDER BY v2 LIMIT 9223372036854775807) t", sql);

        sql = Deencapsulation.invoke(histogramStatisticsCollectJob, "buildCollectMCV",
                db, olapTable, 100L, "v2");
        Assert.assertEquals("select cast(version as INT), cast(db_id as BIGINT), cast(table_id as BIGINT), " +
                "cast(column_key as varchar), cast(column_value as varchar) " +
                "from (select 2 as version, 10002 as db_id, 16325 as table_id, `v2` as column_key, " +
                "count(`v2`) as column_value from test.t0_stats group by `v2` order by count(`v2`) desc limit 100 ) t", sql);
    }
}
