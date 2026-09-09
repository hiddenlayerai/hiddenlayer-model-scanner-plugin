package io.jenkins.plugins.hiddenlayer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hiddenlayer.api.models.scans.results.ScanReport;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.Collections;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class HLScanModelBuilderTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    // Builder parameters
    final String modelName = "model-name";
    final String hlClientId = "client-id";
    final String hlClientSecret = "client-secret";
    final String folderToScan = "folder-to-scan";
    final boolean failUnsupported = true;
    final FailOnDetectionSeverityEnum failSeverity = FailOnDetectionSeverityEnum.NONE;

    // This text is printed to the console when the plugin is run.
    final String scanMessage = String.format("Scanning model %s in folder %s", modelName, folderToScan);

    // Test objects
    private ScannerService mockScannerService;
    private String modelVersion = "1.0.0";
    private String modelId = "model-id";
    private String scanId = "scan-id";

    @Before
    public void setUp() throws IOException, URISyntaxException, InterruptedException, Exception {
        mockScannerService = mock(ScannerService.class);

        OffsetDateTime offsetDateTime = OffsetDateTime.parse("2021-01-01T00:00:00Z");

        ScanReport.Inventory inventory = ScanReport.Inventory.builder()
                .modelId(modelId)
                .modelName(modelName)
                .modelVersionId("version-id")
                .requestedScanLocation("/path/to/model")
                .modelVersion(modelVersion)
                .build();

        ScanReport.Summary summary = ScanReport.Summary.builder()
                .detectionCategories(Collections.emptyList())
                .detectionCount(0L)
                .fileCount(1L)
                .filesFailedToScan(0L)
                .filesWithDetectionsCount(0L)
                .highestSeverity(ScanReport.Summary.HighestSeverity.NONE)
                .severity(ScanReport.Summary.Severity.SAFE)
                .unknownFiles(0L)
                .build();

        ScanReport scanReport = ScanReport.builder()
                .detectionCount(0L)
                .fileCount(1L)
                .filesWithDetectionsCount(0L)
                .inventory(inventory)
                .scanId(scanId)
                .startTime(offsetDateTime)
                .status(ScanReport.Status.DONE)
                .summary(summary)
                .version("24.10.2")
                .endTime(offsetDateTime)
                .severity(ScanReport.Severity.SAFE)
                .build();

        when(mockScannerService.scanFolder(eq(modelName), anyString())).thenReturn(scanReport);
        ModelScanServiceFactory.setTestInstance(mockScannerService);
    }

    @After
    public void tearDown() {
        ModelScanServiceFactory.setTestInstance(null);
    }

    // Test that the builder can be created and configured
    @Test
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        HLScanModelBuilder builder = new HLScanModelBuilder(
                modelName, hlClientId, hlClientSecret, folderToScan, failUnsupported, failSeverity);
        project.getBuildersList().add(builder);

        // Save and reload the project configuration
        project = jenkins.configRoundtrip(project);

        HLScanModelBuilder gotBuilder =
                (HLScanModelBuilder) project.getBuildersList().get(0);

        jenkins.assertEqualDataBoundBeans(builder, gotBuilder);
    }

    // Test that the builder can be created and run
    @Test
    public void testBuild() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        HLScanModelBuilder builder = createBuilder();
        project.getBuildersList().add(builder);

        FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);
        jenkins.assertLogContains(scanMessage, build);
    }

    // Test that the builder can be created and run in a scripted pipeline
    @Test
    public void testScriptedPipeline() throws Exception {
        String agentLabel = "my-agent";
        jenkins.createOnlineSlave(Label.get(agentLabel)); // this Jenkins method name needs updating
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "test-scripted-pipeline");
        String pipelineScript = "node {hlScanModel modelName: '" + modelName
                + "', hlClientId: '" + hlClientId
                + "', hlClientSecret: '" + hlClientSecret
                + "', folderToScan: '" + folderToScan
                + "', failOnUnsupported: " + failUnsupported
                + ", failOnSeverity: 'NONE"
                + "'}";
        job.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun completedBuild = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
        jenkins.assertLogContains(scanMessage, completedBuild);

        // Add verification that mock was called with expected parameters
        verify(mockScannerService).scanFolder(eq(modelName), anyString());
    }

    private HLScanModelBuilder createBuilder() {
        return new HLScanModelBuilder(
                modelName, hlClientId, hlClientSecret, folderToScan, failUnsupported, failSeverity);
    }
}
