package com.dangdang.ddframe.job.cloud.scheduler.restful;
import com.dangdang.ddframe.job.cloud.scheduler.config.JobExecutionType;
import com.dangdang.ddframe.job.cloud.scheduler.fixture.CloudJsonConstants;
import com.dangdang.ddframe.job.cloud.scheduler.producer.ProducerManager;
import com.dangdang.ddframe.job.cloud.scheduler.fixture.TaskNode;
import com.dangdang.ddframe.job.cloud.scheduler.state.failover.FailoverTaskInfo;
import com.dangdang.ddframe.job.cloud.scheduler.state.running.RunningService;
import com.dangdang.ddframe.job.context.ExecutionType;
import com.dangdang.ddframe.job.context.TaskContext;
import com.dangdang.ddframe.job.context.TaskContext.MetaInfo;
import com.dangdang.ddframe.job.event.rdb.JobEventRdbSearch;
import com.dangdang.ddframe.job.event.rdb.JobEventRdbSearch.Condition;
import com.dangdang.ddframe.job.event.rdb.JobEventRdbSearch.Result;
import com.dangdang.ddframe.job.event.type.JobExecutionEvent;
import com.dangdang.ddframe.job.event.type.JobStatusTraceEvent;
import com.dangdang.ddframe.job.event.type.JobStatusTraceEvent.Source;
import com.dangdang.ddframe.job.event.type.JobStatusTraceEvent.State;
import com.dangdang.ddframe.job.reg.base.CoordinatorRegistryCenter;
import com.dangdang.ddframe.job.restful.RestfulServer;
import com.dangdang.ddframe.job.statistics.type.job.JobExecutionTypeStatistics;
import com.dangdang.ddframe.job.statistics.type.job.JobTypeStatistics;
import com.dangdang.ddframe.job.statistics.type.task.TaskResultStatistics;
import com.dangdang.ddframe.job.util.json.GsonFactory;
import com.google.common.collect.Lists;
import org.apache.mesos.SchedulerDriver;
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import javax.ws.rs.core.MediaType;
import static org.hamcrest.core.Is.is;
import java.io.UnsupportedEncodingException;
import static org.junit.Assert.assertThat;
import java.net.URLEncoder;
import static org.mockito.Mockito.any;
import java.util.Collection;
import static org.mockito.Mockito.mock;
import java.util.Collections;
import static org.mockito.Mockito.reset;
import java.util.HashMap;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import java.util.Map;
import static org.mockito.Mockito.when;
import java.util.UUID;

@RunWith(value = MockitoJUnitRunner.class) public final class CloudJobRestfulApiTest {
  private static RestfulServer server;

  private static CoordinatorRegistryCenter regCenter;

  private static JobEventRdbSearch jobEventRdbSearch;

  @BeforeClass public static void setUpClass() throws Exception {
    regCenter = mock(CoordinatorRegistryCenter.class);
    jobEventRdbSearch = mock(JobEventRdbSearch.class);

    server = new RestfulServer(19000);
    CloudJobRestfulApi.init(regCenter, jobEventRdbSearch);
    SchedulerDriver schedulerDriver = mock(SchedulerDriver.class);
    ProducerManager producerManager = new ProducerManager(schedulerDriver, regCenter);
    producerManager.startup();
    CloudJobRestfulApi.setContext(schedulerDriver, producerManager);
    server.start(CloudJobRestfulApi.class.getPackage().getName());
  }

  @AfterClass public static void tearDown() throws Exception {
    sentRequest("http://127.0.0.1:19000/job/deregister", "DELETE", "test_job");
    server.stop();
  }

  @Before public void setUp() {
    reset(regCenter);
    reset(jobEventRdbSearch);
  }

  @Test public void assertRegister() throws Exception {
    when(regCenter.isExisted("/config/test_job")).thenReturn(false);
    assertThat(sentRequest("http://127.0.0.1:19000/job/register", "POST", CloudJsonConstants.getJobJson()), is(204));
    verify(regCenter).persist("/config/test_job", CloudJsonConstants.getJobJson());
    sentRequest("http://127.0.0.1:19000/job/deregister", "DELETE", "test_job");
  }

  @Test public void assertRegisterWithBadRequest() throws Exception {
    assertThat(sentRequest("http://127.0.0.1:19000/job/register", "POST", "\"{\"jobName\":\"wrong_job\"}"), is(500));
  }

  @Test public void assertUpdate() throws Exception {
    when(regCenter.isExisted("/config/test_job")).thenReturn(true);
    when(regCenter.get("/config/test_job")).thenReturn(CloudJsonConstants.getJobJson());
    assertThat(sentRequest("http://127.0.0.1:19000/job/update", "PUT", CloudJsonConstants.getJobJson()), is(204));
    verify(regCenter).update("/config/test_job", CloudJsonConstants.getJobJson());
    sentRequest("http://127.0.0.1:19000/job/deregister", "DELETE", "test_job");
  }

  @Test public void assertDeregister() throws Exception {
    when(regCenter.isExisted("/config/test_job")).thenReturn(false);
    assertThat(sentRequest("http://127.0.0.1:19000/job/deregister", "DELETE", "test_job"), is(204));
    verify(regCenter, times(2)).get("/config/test_job");
  }

  @Test public void assertTriggerWithDaemonJob() throws Exception {
    when(regCenter.get("/config/test_job")).thenReturn(CloudJsonConstants.getJobJson(JobExecutionType.DAEMON));
    assertThat(sentRequest("http://127.0.0.1:19000/job/trigger", "POST", "test_job"), is(500));
  }

  @Test public void assertTriggerWithTransientJob() throws Exception {
    when(regCenter.get("/config/test_job")).thenReturn(CloudJsonConstants.getJobJson());
    assertThat(sentRequest("http://127.0.0.1:19000/job/trigger", "POST", "test_job"), is(204));
  }

  @Test public void assertDetail() throws Exception {
    when(regCenter.get("/config/test_job")).thenReturn(CloudJsonConstants.getJobJson());
    assertThat(sentGetRequest("http://127.0.0.1:19000/job/jobs/test_job"), is(CloudJsonConstants.getJobJson()));
    verify(regCenter).get("/config/test_job");
  }

  @Test public void assertDetailWithNotExistedJob() throws Exception {
    assertThat(sentRequest("http://127.0.0.1:19000/job/jobs/notExistedJobName", "GET", ""), is(500));
  }

  @Test public void assertFindAllJobs() throws Exception {
    when(regCenter.isExisted("/config")).thenReturn(true);
    when(regCenter.getChildrenKeys("/config")).thenReturn(Lists.newArrayList("test_job"));
    when(regCenter.get("/config/test_job")).thenReturn(CloudJsonConstants.getJobJson());
    assertThat(sentGetRequest("http://127.0.0.1:19000/job/jobs"), is("[" + CloudJsonConstants.getJobJson() + "]"));
    verify(regCenter).isExisted("/config");
    verify(regCenter).getChildrenKeys("/config");
    verify(regCenter).get("/config/test_job");
  }

  @Test public void assertFindAllRunningTasks() throws Exception {
    RunningService runningService = new RunningService(regCenter);
    TaskContext actualTaskContext = TaskContext.from(TaskNode.builder().build().getTaskNodeValue());
    runningService.add(actualTaskContext);
    assertThat(sentGetRequest("http://127.0.0.1:19000/job/tasks/running"), is(GsonFactory.getGson().toJson(Lists.newArrayList(actualTaskContext))));
  }

  @Test public void assertFindAllReadyTasks() throws Exception {
    when(regCenter.isExisted("/state/ready")).thenReturn(true);
    when(regCenter.getChildrenKeys("/state/ready")).thenReturn(Lists.newArrayList("test_job"));
    when(regCenter.get("/state/ready/test_job")).thenReturn("1");
    Map<String, String> expectedMap = new HashMap<>();
    expectedMap.put("jobName", "test_job");
    expectedMap.put("times", "1");
    @SuppressWarnings(value = { "unchecked" }) Collection<Map<String, String>> expectedResult = Lists.newArrayList(expectedMap);
    assertThat(sentGetRequest("http://127.0.0.1:19000/job/tasks/ready"), is(GsonFactory.getGson().toJson(expectedResult)));
    verify(regCenter).isExisted("/state/ready");
    verify(regCenter).getChildrenKeys("/state/ready");
    verify(regCenter).get("/state/ready/test_job");
  }

  @Test public void assertFindAllFailoverTasks() throws Exception {
    when(regCenter.isExisted("/state/failover")).thenReturn(true);
    when(regCenter.getChildrenKeys("/state/failover")).thenReturn(Lists.newArrayList("test_job"));
    when(regCenter.getChildrenKeys("/state/failover/test_job")).thenReturn(Lists.newArrayList("test_job@-@0"));
    String originalTaskId = UUID.randomUUID().toString();
    when(regCenter.get("/state/failover/test_job/test_job@-@0")).thenReturn(originalTaskId);
    FailoverTaskInfo expectedFailoverTask = new FailoverTaskInfo(MetaInfo.from("test_job@-@0"), originalTaskId);
    Collection<FailoverTaskInfo> expectedResult = Lists.newArrayList(expectedFailoverTask);
    assertThat(sentGetRequest("http://127.0.0.1:19000/job/tasks/failover"), is(GsonFactory.getGson().toJson(expectedResult)));
    verify(regCenter).isExisted("/state/failover");
    verify(regCenter).getChildrenKeys("/state/failover");
    verify(regCenter).getChildrenKeys("/state/failover/test_job");
    verify(regCenter).get("/state/failover/test_job/test_job@-@0");
  }

  @Test public void assertFindJobExecutionEventsWhenNotConfigRDB() throws Exception {
    ReflectionUtils.setFieldValue(CloudJobRestfulApi.class, CloudJobRestfulApi.class.getDeclaredField("jobEventRdbSearch"), null);
    assertThat(sentGetRequest("http://127.0.0.1:19000/job/events/executions"), is(GsonFactory.getGson().toJson(new Result<>(0, Collections.<JobExecutionEvent>emptyList()))));
  }

  @Test public void assertFindJobExecutionEvents() throws Exception {
    ReflectionUtils.setFieldValue(CloudJobRestfulApi.class, CloudJobRestfulApi.class.getDeclaredField("jobEventRdbSearch"), jobEventRdbSearch);
    JobExecutionEvent jobExecutionEvent = new JobExecutionEvent("fake_task_id", "test_job", JobExecutionEvent.ExecutionSource.NORMAL_TRIGGER, 0);
    when(jobEventRdbSearch.findJobExecutionEvents(any(Condition.class))).thenReturn(new Result<>(0, Lists.newArrayList(jobExecutionEvent)));
    assertThat(sentGetRequest("http://127.0.0.1:19000/job/events/executions?" + buildFindJobEventsQueryParameter()), is(GsonFactory.getGson().toJson(new Result<>(0, Lists.newArrayList(jobExecutionEvent)))));
    verify(jobEventRdbSearch).findJobExecutionEvents(any(Condition.class));
  }

  @Test public void assertFindJobStatusTraceEventEventsWhenNotConfigRDB() throws Exception {
    ReflectionUtils.setFieldValue(CloudJobRestfulApi.class, CloudJobRestfulApi.class.getDeclaredField("jobEventRdbSearch"), null);
    assertThat(sentGetRequest("http://127.0.0.1:19000/job/events/statusTraces"), is(GsonFactory.getGson().toJson(new Result<>(0, Collections.<JobExecutionEvent>emptyList()))));
  }

  @Test public void assertFindJobStatusTraceEvent() throws Exception {
    ReflectionUtils.setFieldValue(CloudJobRestfulApi.class, CloudJobRestfulApi.class.getDeclaredField("jobEventRdbSearch"), jobEventRdbSearch);
    JobStatusTraceEvent jobStatusTraceEvent = new JobStatusTraceEvent("test-job", "fake_task_id", "fake_slave_id", Source.LITE_EXECUTOR, ExecutionType.READY, "0", State.TASK_RUNNING, "message is empty.");
    when(jobEventRdbSearch.findJobStatusTraceEvents(any(Condition.class))).thenReturn(new Result<>(0, Lists.newArrayList(jobStatusTraceEvent)));
    assertThat(sentGetRequest("http://127.0.0.1:19000/job/events/statusTraces?" + buildFindJobEventsQueryParameter()), is(GsonFactory.getGson().toJson(new Result<>(0, Lists.newArrayList(jobStatusTraceEvent)))));
    verify(jobEventRdbSearch).findJobStatusTraceEvents(any(Condition.class));
  }

  private String buildFindJobEventsQueryParameter() throws UnsupportedEncodingException {
    return "per_page=10&page=1&sort=jobName&order=DESC&jobName=test_job" + "&startTime=" + URLEncoder.encode("2016-12-26 10:00:00", "UTF-8") + "&endTime=" + URLEncoder.encode("2016-12-26 10:00:00", "UTF-8");
  }

  @Test public void assertGetTaskResultStatistics() throws Exception {
    String result = sentGetRequest("http://127.0.0.1:19000/job/statistics/tasks/results");
    TaskResultStatistics taskResultStatistics = GsonFactory.getGson().fromJson(result, TaskResultStatistics.class);
    assertThat(taskResultStatistics.getSuccessCount(), is(0));
    assertThat(taskResultStatistics.getFailedCount(), is(0));
  }

  @Test public void assertGetTaskResultStatisticsWeekly() throws Exception {
    String result = sentGetRequest("http://127.0.0.1:19000/job/statistics/tasks/results?since=lastWeek");
    TaskResultStatistics taskResultStatistics = GsonFactory.getGson().fromJson(result, TaskResultStatistics.class);
    assertThat(taskResultStatistics.getSuccessCount(), is(0));
    assertThat(taskResultStatistics.getFailedCount(), is(0));
  }

  @Test public void assertGetTaskResultStatisticsSinceOnline() throws Exception {
    String result = sentGetRequest("http://127.0.0.1:19000/job/statistics/tasks/results?since=online");
    TaskResultStatistics taskResultStatistics = GsonFactory.getGson().fromJson(result, TaskResultStatistics.class);
    assertThat(taskResultStatistics.getSuccessCount(), is(0));
    assertThat(taskResultStatistics.getFailedCount(), is(0));
  }

  @Test public void assertGetJobTypeStatistics() throws Exception {
    String result = sentGetRequest("http://127.0.0.1:19000/job/statistics/jobs/type");
    JobTypeStatistics jobTypeStatistics = GsonFactory.getGson().fromJson(result, JobTypeStatistics.class);
    assertThat(jobTypeStatistics.getSimpleJobCount(), is(0));
    assertThat(jobTypeStatistics.getDataflowJobCount(), is(0));
    assertThat(jobTypeStatistics.getScriptJobCount(), is(0));
  }

  @Test public void assertGetJobExecutionTypeStatistics() throws Exception {
    String result = sentGetRequest("http://127.0.0.1:19000/job/statistics/jobs/executionType");
    JobExecutionTypeStatistics jobExecutionTypeStatistics = GsonFactory.getGson().fromJson(result, JobExecutionTypeStatistics.class);
    assertThat(jobExecutionTypeStatistics.getDaemonJobCount(), is(0));
    assertThat(jobExecutionTypeStatistics.getTransientJobCount(), is(0));
  }

  @Test public void assertFindTaskRunningStatistics() throws Exception {
    assertThat(sentGetRequest("http://127.0.0.1:19000/job/statistics/tasks/running"), is(GsonFactory.getGson().toJson(Collections.emptyList())));
  }

  @Test public void assertFindTaskRunningStatisticsWeekly() throws Exception {
    assertThat(sentGetRequest("http://127.0.0.1:19000/job/statistics/tasks/running?since=lastWeek"), is(GsonFactory.getGson().toJson(Collections.emptyList())));
  }

  @Test public void assertFindJobRunningStatistics() throws Exception {
    assertThat(sentGetRequest("http://127.0.0.1:19000/job/statistics/jobs/running"), is(GsonFactory.getGson().toJson(Collections.emptyList())));
  }

  @Test public void assertFindJobRunningStatisticsWeekly() throws Exception {
    assertThat(sentGetRequest("http://127.0.0.1:19000/job/statistics/jobs/running?since=lastWeek"), is(GsonFactory.getGson().toJson(Collections.emptyList())));
  }

  @Test public void assertFindJobRegisterStatisticsSinceOnline() throws Exception {
    assertThat(sentGetRequest("http://127.0.0.1:19000/job/statistics/jobs/register"), is(GsonFactory.getGson().toJson(Collections.emptyList())));
  }

  private static int sentRequest(final String url, final String method, final String content) throws Exception {
    HttpClient httpClient = new HttpClient();
    try {
      httpClient.start();
      ContentExchange contentExchange = new ContentExchange();
      contentExchange.setMethod(method);
      contentExchange.setRequestContentType(MediaType.APPLICATION_JSON);
      contentExchange.setRequestContent(new ByteArrayBuffer(content.getBytes("UTF-8")));
      httpClient.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
      contentExchange.setURL(url);
      httpClient.send(contentExchange);
      contentExchange.waitForDone();
      return contentExchange.getResponseStatus();
    }  finally {
      httpClient.stop();
    }
  }

  private static String sentGetRequest(final String url) throws Exception {
    HttpClient httpClient = new HttpClient();
    try {
      httpClient.start();
      ContentExchange contentExchange = new ContentExchange();
      contentExchange.setMethod("GET");
      contentExchange.setRequestContentType(MediaType.APPLICATION_JSON);
      httpClient.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
      contentExchange.setURL(url);
      httpClient.send(contentExchange);
      contentExchange.waitForDone();
      return contentExchange.getResponseContent();
    }  finally {
      httpClient.stop();
    }
  }
}