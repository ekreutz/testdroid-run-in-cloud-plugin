package com.testdroid.jenkins;

import com.testdroid.api.APIDeviceGroupQueryBuilder;
import com.testdroid.api.APIException;
import com.testdroid.api.APIQueryBuilder;
import com.testdroid.api.model.*;
import com.testdroid.api.model.APITestRunConfig.Scheduler;
import com.testdroid.jenkins.model.TestRunStateCheckMethod;
import com.testdroid.jenkins.remotesupport.MachineIndependentFileUploader;
import com.testdroid.jenkins.remotesupport.MachineIndependentResultsDownloader;
import com.testdroid.jenkins.scheduler.TestRunFinishCheckScheduler;
import com.testdroid.jenkins.scheduler.TestRunFinishCheckSchedulerFactory;
import com.testdroid.jenkins.utils.AndroidLocale;
import com.testdroid.jenkins.utils.EmailHelper;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import jenkins.tasks.SimpleBuildStep;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Worker class to server as a base for:
 *   RunInCloudBuilder.java (the Builder for the UI plugin)
 *   PipelingCloudBuilder.java (the Builder for the Pipeline plugin)
 */

public class CloudBuilder {

    transient static final Logger LOGGER = Logger.getLogger(CloudBuilder.class.getSimpleName());

    transient static final String POST_HOOK_URL = "/plugin/testdroid-run-in-cloud/api/json/cloud-webhook";

    static final List<String> PAID_ROLES = new ArrayList<String>() {
        {
            add("PRIORITY_SILVER");
            add("PRIORITY_GOLD");
            add("PRIORITY_PLATINUM");
            add("PAID_RUN");
        }
    };

    String appPath;

    String clusterId; // device group id

    String dataPath;

    boolean failBuildIfThisStepFailed;

    String keyValuePairs;

    String language;

    String notificationEmail = "";

    String notificationEmailType = String.valueOf(APINotificationEmail.Type.ALWAYS);

    String projectId;

    String scheduler;

    String screenshotsDirectory;

    String testCasesSelect;

    String testCasesValue;

    String testPath;

    String testRunName;

    String testRunner;

    WaitForResultsBlock waitForResultsBlock;

    String withAnnotation;

    String withoutAnnotation;

    String testTimeout;

    public CloudBuilder() {}

    public String getTestRunName() {
        return testRunName;
    }

    public void setTestRunName(String testRunName) {
        this.testRunName = testRunName;
    }

    public String getAppPath() {
        return appPath;
    }

    public void setAppPath(String appPath) {
        this.appPath = appPath;
    }

    public String getTestPath() {
        return testPath;
    }

    public void setTestPath(String testPath) {
        this.testPath = testPath;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public String getTestRunner() {
        return testRunner;
    }

    public void setTestRunner(String testRunner) {
        this.testRunner = testRunner;
    }

    public String getScreenshotsDirectory() {
        return screenshotsDirectory;
    }

    public void setScreenshotsDirectory(String screenshotsDirectory) {
        this.screenshotsDirectory = screenshotsDirectory;
    }

    public String getKeyValuePairs() {
        return keyValuePairs;
    }

    public void setKeyValuePairs(String keyValuePairs) {
        this.keyValuePairs = keyValuePairs;
    }

    public String getWithAnnotation() {
        return withAnnotation;
    }

    public void setWithAnnotation(String withAnnotation) {
        this.withAnnotation = withAnnotation;
    }

    public String getWithoutAnnotation() {
        return withoutAnnotation;
    }

    public void setWithoutAnnotation(String withoutAnnotation) {
        this.withoutAnnotation = withoutAnnotation;
    }

    public String getTestCasesSelect() {
        return testCasesSelect;
    }

    public void setTestCasesSelect(String testCasesSelect) {
        this.testCasesSelect = testCasesSelect;
    }

    public String getTestCasesValue() {
        return testCasesValue;
    }

    public void setTestCasesValue(String testCasesValue) {
        this.testCasesValue = testCasesValue;
    }

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }

    public String getLanguage() {
        if (language == null) {
            language = String.format("%s-%s", Locale.ENGLISH.getLanguage(), Locale.ENGLISH.getCountry());
        }
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getScheduler() {
        if (scheduler == null) {
            scheduler = Scheduler.PARALLEL.toString();
        }
        return scheduler;
    }

    public void setScheduler(String scheduler) {
        this.scheduler = scheduler.toLowerCase();
    }

    public String getNotificationEmail() {
        return notificationEmail;
    }

    public void setNotificationEmail(String notificationEmail) {
        this.notificationEmail = notificationEmail;
    }

    public String getNotificationEmailType() {
        return notificationEmailType;
    }

    public void setNotificationEmailType(String notificationEmailType) {
        this.notificationEmailType = notificationEmailType;
    }

    public String getTestTimeout() {
        return testTimeout;
    }

    public void setTestTimeout(String testTimeout) {
        this.testTimeout = testTimeout;
    }

    public WaitForResultsBlock getWaitForResultsBlock() {
        return waitForResultsBlock;
    }

    public void setWaitForResultsBlock(WaitForResultsBlock waitForResultsBlock) {
        this.waitForResultsBlock = waitForResultsBlock;
    }

    public boolean isFailBuildIfThisStepFailed() {
        return failBuildIfThisStepFailed;
    }

    public void setFailBuildIfThisStepFailed(boolean failBuildIfThisStepFailed) {
        this.failBuildIfThisStepFailed = failBuildIfThisStepFailed;
    }

    public boolean isFullTest() {
        return StringUtils.isNotBlank(testPath);
    }

    public boolean isDataFile() {
        return StringUtils.isNotBlank(dataPath);
    }

    boolean verifyParameters(TaskListener listener) {
        boolean result = true;
        if (StringUtils.isBlank(appPath)) {
            listener.getLogger().println(Messages.ERROR_APP_PATH() + "\n");
            result = false;
        }
        if (StringUtils.isBlank(projectId)) {
            listener.getLogger().println(Messages.EMPTY_PROJECT() + "\n");
            result = false;
        }
        return result;
    }

    public boolean isWaitForResults() {
        return waitForResultsBlock != null;
    }

    String evaluateHookUrl() {
        return isWaitForResults() ?
                StringUtils.isNotBlank(waitForResultsBlock.getHookURL()) ? waitForResultsBlock.getHookURL()
                        : String.format("%s%s", Hudson.getInstance().getRootUrl(), POST_HOOK_URL) :
                null;
    }

    String evaluateResultsPath(FilePath workspace) {
        return isWaitForResults() ?
                StringUtils.isNotBlank(waitForResultsBlock.getResultsPath()) ? waitForResultsBlock.getResultsPath()
                        : workspace.getRemote() :
                null;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        listener.getLogger().println(Messages.RUN_TEST_IN_CLOUD_STARTED());

        boolean result = runTest(run, workspace, launcher, listener);
        if (result) {
            listener.getLogger().println(Messages.RUN_TEST_IN_CLOUD_SUCCEEDED());
        } else {
            listener.getLogger().println(Messages.RUN_TEST_IN_CLOUD_FAILED());
        }

        if (!result && failBuildIfThisStepFailed) {
            throw new IOException();
        }
    }

    boolean runTest(Run<?, ?> build, FilePath workspace, Launcher launcher, final TaskListener listener) {
        // rewrite paths to take variables into consideration
        String appPathFinal = applyMacro(build, listener, appPath);
        String testPathFinal = applyMacro(build, listener, testPath);
        String dataPathFinal = applyMacro(build, listener, dataPath);
        String withAnnotationFinal = applyMacro(build, listener, withAnnotation);
        String testRunnerFinal = applyMacro(build, listener, testRunner);
        String withoutAnnotationFinal = applyMacro(build, listener, withoutAnnotation);

        TestdroidCloudSettings.DescriptorImpl descriptor = TestdroidCloudSettings.descriptor();
        TestdroidCloudSettings plugin = TestdroidCloudSettings.getInstance();
        boolean releaseDone = false;

        try {
            // make part update and run project "transactional"
            plugin.getSemaphore().acquire();

            if (!verifyParameters(listener)) {
                return false;
            }
            APIUser user = descriptor.getUser();

            final APIProject project = user.getProject(Long.parseLong(this.projectId.trim()));
            if (project == null) {
                listener.getLogger().println(Messages.CHECK_PROJECT_NAME());
                return false;
            }

            updateUserEmailNotifications(user, project);

            APITestRunConfig config = project.getTestRunConfig();
            config.setAppCrawlerRun(!isFullTest());
            config.setDeviceLanguageCode(this.language);
            config.setScheduler(Scheduler.valueOf(this.scheduler));
            config.setUsedDeviceGroupId(Long.parseLong(this.clusterId));
            config.setHookURL(evaluateHookUrl());
            config.setScreenshotDir(this.screenshotsDirectory);
            config.setInstrumentationRunner(testRunnerFinal);
            config.setWithoutAnnotation(withoutAnnotationFinal);
            config.setWithAnnotation(withAnnotationFinal);
            if (getDescriptor().isPaidUser()) {
                try {
                    config.setTimeout(Long.parseLong(testTimeout));
                } catch (NumberFormatException ignored) {
                    listener.getLogger().println(String.format(Messages.TEST_TIMEOUT_NOT_NUMERIC_VALUE(), testTimeout));
                    config.setTimeout(600L);
                }
            } else {
                // 10 minutes for free users
                config.setTimeout(600L);
            }

            setLimitations(build, listener, config);
            deleteExistingParameters(config);
            createProvidedParameters(config);

            config.update();
            printTestJob(project, config, listener);
            getDescriptor().save();

            final FilePath appFile = new FilePath(launcher.getChannel(), getAbsolutePath(workspace, appPathFinal));

            listener.getLogger().println(String.format(Messages.UPLOADING_NEW_APPLICATION_S(), appPathFinal));

            Long testFileId = null;
            Long dataFileId = null;
            Long appFileId = appFile.act(new MachineIndependentFileUploader(descriptor, project.getId(),
                    MachineIndependentFileUploader.FILE_TYPE.APPLICATION, listener));
            if (appFileId == null) {
                return false;
            }

            if (isFullTest()) {
                FilePath testFile = new FilePath(launcher.getChannel(), getAbsolutePath(workspace, testPathFinal));

                listener.getLogger().println(String.format(Messages.UPLOADING_NEW_INSTRUMENTATION_S(),
                        testPathFinal));

                testFileId = testFile.act(new MachineIndependentFileUploader(descriptor, project.getId(),
                        MachineIndependentFileUploader.FILE_TYPE.TEST, listener));
                if (testFileId == null) {
                    return false;
                }
            }

            if (isDataFile()) {
                FilePath dataFile = new FilePath(launcher.getChannel(), getAbsolutePath(workspace, dataPathFinal));
                listener.getLogger().println(String.format(Messages.UPLOADING_DATA_FILE_S(), dataPathFinal));
                dataFileId = dataFile.act(new MachineIndependentFileUploader(descriptor, project.getId(),
                        MachineIndependentFileUploader.FILE_TYPE.DATA, listener));
                if (dataFileId == null) {
                    return false;
                }
            }
            listener.getLogger().println(Messages.RUNNING_TESTS());

            // run project with proper name set in jenkins if it's set
            String finalTestRunName = applyMacro(build, listener, testRunName);
            finalTestRunName = StringUtils.isBlank(finalTestRunName) || finalTestRunName.trim().startsWith("$") ?
                    null : finalTestRunName;
            APITestRun testRun = project.runWithConfig(finalTestRunName, null, config, appFileId, testFileId,
                    dataFileId);
            String cloudLinkPrefix = descriptor.getPrivateInstanceState() ?
                    StringUtils.isNotBlank(descriptor.getNewCloudUrl()) ?
                            descriptor.getNewCloudUrl() : descriptor
                            .getCloudUrl() : TestdroidCloudSettings.CLOUD_ENDPOINT;
            String cloudLink = String.format("%s/#service/testrun/%s/%s", cloudLinkPrefix, testRun.getProjectId(),
                    testRun.getId());
            build.getActions().add(new CloudLink(build, cloudLink));

            RunInCloudEnvInject variable = new RunInCloudEnvInject("CLOUD_LINK", cloudLink);
            build.addAction(variable);


            plugin.getSemaphore().release();
            releaseDone = true;

            return waitForResults(project, testRun, workspace, launcher, listener);

        } catch (APIException e) {
            listener.getLogger().println(String.format("%s: %s", Messages.ERROR_API(), e.getMessage()));
            LOGGER.log(Level.WARNING, Messages.ERROR_API(), e);
        } catch (IOException e) {
            listener.getLogger().println(String.format("%s: %s", Messages.ERROR_CONNECTION(), e.getLocalizedMessage()));
            LOGGER.log(Level.WARNING, Messages.ERROR_CONNECTION(), e);
        } catch (InterruptedException e) {
            listener.getLogger().println(String.format("%s: %s", Messages.ERROR_TESTDROID(), e.getLocalizedMessage()));
            LOGGER.log(Level.WARNING, Messages.ERROR_TESTDROID(), e);
        } catch (NumberFormatException e) {
            listener.getLogger().println(Messages.NO_DEVICE_GROUP_CHOSEN());
            LOGGER.log(Level.WARNING, Messages.NO_DEVICE_GROUP_CHOSEN());
        } finally {
            if (!releaseDone) {
                plugin.getSemaphore().release();
            }
        }

        return false;
    }

    boolean waitForResults(final APIProject project, final APITestRun testRun, FilePath workspace, Launcher launcher, TaskListener listener) {
        boolean isDownloadOk = true;
        if (isWaitForResults()) {
            TestRunFinishCheckScheduler scheduler = TestRunFinishCheckSchedulerFactory.createTestRunFinishScheduler
                    (waitForResultsBlock.getTestRunStateCheckMethod());
            try {
                boolean testRunToAbort = false;
                listener.getLogger().println("Waiting for results...");
                scheduler.schedule(this, project.getId(), testRun.getId());
                try {
                    synchronized (this) {
                        wait(waitForResultsBlock.getWaitForResultsTimeout() * 1000);
                    }
                    scheduler.cancel(project.getId(), testRun.getId());
                    testRun.refresh();
                    if (testRun.getState() == APITestRun.State.FINISHED) {
                        isDownloadOk = launcher.getChannel().call(
                                new MachineIndependentResultsDownloader(TestdroidCloudSettings.descriptor(), listener,
                                        project.getId(), testRun.getId(), evaluateResultsPath(workspace),
                                        waitForResultsBlock.isDownloadScreenshots()));

                        if (!isDownloadOk) {
                            listener.getLogger().println(Messages.DOWNLOAD_RESULTS_FAILED());
                            LOGGER.log(Level.WARNING, Messages.DOWNLOAD_RESULTS_FAILED());
                        }
                    } else {
                        testRunToAbort = true;
                        isDownloadOk = false;
                        String msg = String.format(Messages.DOWNLOAD_RESULTS_FAILED_WITH_REASON_S(),
                                "Test run is not finished yet!");
                        listener.getLogger().println(msg);
                        LOGGER.log(Level.WARNING, msg);
                    }
                } catch (InterruptedException e) {
                    testRunToAbort = true;

                    listener.getLogger().println(e.getMessage());
                    LOGGER.log(Level.WARNING, e.getMessage(), e);
                }
                if (testRunToAbort && waitForResultsBlock.forceFinishAfterBreak) {
                    String msg = "Force finish test in Cloud";
                    listener.getLogger().println(msg);
                    LOGGER.log(Level.WARNING, msg);
                    testRun.abort();
                }
            } catch (APIException e) {
                listener.getLogger().println(
                        String.format("%s: %s", Messages.ERROR_API(), e.getMessage()));
                LOGGER.log(Level.WARNING, Messages.ERROR_API(), e);
            } catch (IOException e) {
                listener.getLogger().println(
                        String.format("%s: %s", Messages.ERROR_CONNECTION(), e.getLocalizedMessage()));
                LOGGER.log(Level.WARNING, Messages.ERROR_CONNECTION(), e);
            } finally {
                scheduler.cancel(project.getId(), testRun.getId());
            }
        }
        return isDownloadOk;
    }

}
