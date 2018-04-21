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
 * Builder class for the Pipeline plugin functionality
 */

public class PipelineCloudBuilder extends AbstractBuilder {

    // mandatory parameters for the Pipeline plugin
    @DataBoundConstructor
    public PipelineCloudBuilder(String projectId, String clusterId, String appPath, String testPath) {
        this.projectId = projectId;
        this.appPath = appPath;
        this.clusterId = clusterId;
    }

    // optional params for the plugin
    // defined in DataBoundSetters

    @DataBoundSetter
    public void setTestRunName(String testRunName) {
        this.testRunName = testRunName;
    }

    @DataBoundSetter
    public void setTestRunner(String testRunner) {
        this.testRunner = testRunner;
    }

    @DataBoundSetter
    public void setScreenshotsDirectory(String screenshotsDirectory) {
        this.screenshotsDirectory = screenshotsDirectory;
    }

    @DataBoundSetter
    public void setKeyValuePairs(String keyValuePairs) {
        this.keyValuePairs = keyValuePairs;
    }

    @DataBoundSetter
    public void setWithAnnotation(String withAnnotation) {
        this.withAnnotation = withAnnotation;
    }

    @DataBoundSetter
    public void setWithoutAnnotation(String withoutAnnotation) {
        this.withoutAnnotation = withoutAnnotation;
    }

    @DataBoundSetter
    public void setTestCasesSelect(String testCasesSelect) {
        this.testCasesSelect = testCasesSelect;
    }

    @DataBoundSetter
    public void setTestCasesValue(String testCasesValue) {
        this.testCasesValue = testCasesValue;
    }

    @DataBoundSetter
    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }

    @DataBoundSetter
    public void setLanguage(String language) {
        this.language = language;
    }

    @DataBoundSetter
    public void setScheduler(String scheduler) {
        this.scheduler = scheduler.toLowerCase();
    }

    @DataBoundSetter
    public void setNotificationEmail(String notificationEmail) {
        this.notificationEmail = notificationEmail;
    }

    @DataBoundSetter
    public void setNotificationEmailType(String notificationEmailType) {
        this.notificationEmailType = notificationEmailType;
    }

    @DataBoundSetter
    public void setTestTimeout(String testTimeout) {
        this.testTimeout = testTimeout;
    }

    @DataBoundSetter
    public void setWaitForResultsBlock(WaitForResultsBlock waitForResultsBlock) {
        this.waitForResultsBlock = waitForResultsBlock;
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

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(PipelineCloudBuilder.class);
        }

        @Override
        public String getFunctionName() {
            return "runInCloud";
        }

        @Override
        public String getDisplayName() {
            return "Start a run in the Bitbar Cloud";
        }
    }
}
