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
import com.testdroid.jenkins.utils.TestdroidApiUtil;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import com.google.inject.Inject;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.StaplerRequest;
import net.sf.json.JSONObject;

import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import java.util.Locale;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Pipeline build step for Bitbar Cloud's Jenkins plugin.
 *
 * Can be invoked from pipeline like eg:
 * ...
 * steps {
 *     runInCloud(
 *         projectId: "144314736",
 *         deviceGroupId: "36085",
 *         appPath: "application.ipa",
 *         testPath: "tests.zip"
 *     )
 * }
 * ...
 */

public class PipelineCloudStep extends AbstractStepImpl {

    transient private static final Logger LOGGER = Logger.getLogger(PipelineCloudStep.class.getSimpleName());

    private String appPath;
    private String deviceGroupId;
    private String dataPath;
    private String testPath;
    private boolean failBuildIfThisStepFailed;
    private String keyValuePairs;
    private String language;
    private String notificationEmail = "";
    private String notificationEmailType = String.valueOf(APINotificationEmail.Type.ALWAYS);
    private String projectId;
    private String scheduler;
    private String screenshotsDirectory;
    private String testCasesSelect;
    private String testCasesValue;
    private String testRunName;
    private String testRunner;
    private String withAnnotation;
    private String withoutAnnotation;
    private String testTimeout;
    private String credentialsId;
    private String cloudUrl;

    // these variables are used to create a WaitForResultsBlock
    private boolean waitForResults;
    private String testRunStateCheckMethod; // API_CALL, HOOK_URL,
    private String hookURL;
    private String waitForResultsTimeout;
    private String resultsPath;
    private boolean downloadScreenshots;
    private boolean forceFinishAfterBreak;

    /**
     * Constructor; defined the mandatory parameters to be passed in Pipeline.
     *
     * @param projectId: Bitbar Cloud project Id
     * @param deviceGroupId: Bitbar Cloud device group Id
     * @param appPath: local path of app (.ipa, .apk) to be uploaded
     */
    @DataBoundConstructor
    public PipelineCloudStep(String projectId, String deviceGroupId, String appPath) {
        this.projectId = projectId;
        this.appPath = appPath;
        this.deviceGroupId = deviceGroupId;
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
    public void setTestPath(String testPath) {
        this.testPath = testPath;
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
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @DataBoundSetter
    public void setCloudUrl(String cloudUrl) {
        this.cloudUrl = cloudUrl;
    }

    @DataBoundSetter
    public void setFailBuildIfThisStepFailed(boolean failBuildIfThisStepFailed) {
        this.failBuildIfThisStepFailed = failBuildIfThisStepFailed;
    }

    @DataBoundSetter
    public void setWaitForResults(boolean waitForResults) {
        this.waitForResults = waitForResults;
    }

    @DataBoundSetter
    public void setTestRunStateCheckMethod(String testRunStateCheckMethod) {
        this.testRunStateCheckMethod = testRunStateCheckMethod;
    }

    @DataBoundSetter
    public void setHookURL(String hookURL) {
        this.hookURL = hookURL;
    }

    @DataBoundSetter
    public void setWaitForResultsTimeout(String waitForResultsTimeout) {
        this.waitForResultsTimeout = waitForResultsTimeout;
    }

    @DataBoundSetter
    public void setResultsPath(String resultsPath) {
        this.resultsPath = resultsPath;
    }

    @DataBoundSetter
    public void setDownloadScreenshots(boolean downloadScreenshots) {
        this.downloadScreenshots = downloadScreenshots;
    }

    @DataBoundSetter
    public void setForceFinishAfterBreak(boolean forceFinishAfterBreak) {
        this.forceFinishAfterBreak = forceFinishAfterBreak;
    }


    public String getTestRunName() {
        return testRunName;
    }

    public String getAppPath() {
        return appPath;
    }

    public String getTestPath() {
        return testPath;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getDeviceGroupId() {
        return deviceGroupId;
    }

    public String getTestRunner() {
        return testRunner;
    }

    public String getScreenshotsDirectory() {
        return screenshotsDirectory;
    }

    public String getKeyValuePairs() {
        return keyValuePairs;
    }

    public String getWithAnnotation() {
        return withAnnotation;
    }

    public String getWithoutAnnotation() {
        return withoutAnnotation;
    }

    public String getTestCasesSelect() {
        return testCasesSelect;
    }

    public String getTestCasesValue() {
        return testCasesValue;
    }

    public String getDataPath() {
        return dataPath;
    }

    public String getLanguage() {
        if (language == null) {
            language = String.format("%s-%s", Locale.ENGLISH.getLanguage(), Locale.ENGLISH.getCountry());
        }
        return language;
    }

    public String getScheduler() {
        if (scheduler == null) {
            scheduler = Scheduler.PARALLEL.toString();
        }
        return scheduler;
    }

    public String getNotificationEmail() {
        return notificationEmail;
    }

    public String getNotificationEmailType() {
        return notificationEmailType;
    }

    public boolean isFailBuildIfThisStepFailed() {
        return failBuildIfThisStepFailed;
    }

    public String getTestTimeout() {
        return testTimeout;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getCloudUrl() {
        return cloudUrl;
    }

    public boolean isWaitForResults() {
        return waitForResults;
    }

    public String getTestRunStateCheckMethod() {
        return testRunStateCheckMethod;
    }

    public String getHookURL() {
        return hookURL;
    }

    public String getWaitForResultsTimeout() {
        return waitForResultsTimeout;
    }

    public String getResultsPath() {
        return resultsPath;
    }

    public boolean isDownloadScreenshots() {
        return downloadScreenshots;
    }

    public boolean isForceFinishAfterBreak() {
        return forceFinishAfterBreak;
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        private TestdroidApiUtil api;
        
        private TestdroidCloudSettings.DescriptorImpl cloudSettings;

        public DescriptorImpl() {
            super(CloudStepExecution.class);
            load();

            // cloud settings load the shared global settings when it's created
            cloudSettings = new TestdroidCloudSettings.DescriptorImpl();
            api = new TestdroidApiUtil(cloudSettings);
        }
        
        @Override
        public String getFunctionName() {
            return "runInBitbarCloud";
        }

        @Override
        public String getDisplayName() {
            return "Start a run in Bitbar Cloud";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }

        public boolean isAuthenticated() {
            return api.isAuthenticated();
        }

        public ListBoxModel doFillProjectIdItems() {
            ListBoxModel projects = new ListBoxModel();
            try {
                APIUser user = api.getUser();
                List<APIProject> list = user.getProjectsResource(new APIQueryBuilder().limit(Integer.MAX_VALUE))
                        .getEntity().getData();
                for (APIProject project : list) {
                    projects.add(project.getName(), project.getId().toString());
                }
            } catch (APIException e) {
                LOGGER.log(Level.WARNING, Messages.ERROR_API());
            }
            return projects;
        }

        public ListBoxModel doFillSchedulerItems() {
            ListBoxModel schedulers = new ListBoxModel();
            schedulers.add(Messages.SCHEDULER_PARALLEL(), Scheduler.PARALLEL.toString());
            schedulers.add(Messages.SCHEDULER_SERIAL(), Scheduler.SERIAL.toString());
            schedulers.add(Messages.SCHEDULER_SINGLE(), Scheduler.SINGLE.toString());
            return schedulers;
        }

        public ListBoxModel doFillDeviceGroupIdItems() {
            ListBoxModel deviceGroups = new ListBoxModel();
            try {
                APIUser user = api.getUser();
                List<APIDeviceGroup> list = user
                        .getDeviceGroupsResource(new APIDeviceGroupQueryBuilder().withPublic().limit(Integer.MAX_VALUE))
                        .getEntity().getData();
                for (APIDeviceGroup deviceGroup : list) {
                    deviceGroups.add(String.format("%s (%d device(s))", deviceGroup.getDisplayName(),
                            deviceGroup.getDeviceCount()), deviceGroup.getId().toString());
                }
            } catch (APIException e) {
                LOGGER.log(Level.WARNING, Messages.ERROR_API());
            }

            return deviceGroups;
        }

        public ListBoxModel doFillLanguageItems() {
            ListBoxModel language = new ListBoxModel();
            for (Locale locale : AndroidLocale.LOCALES) {
                String langDisplay = String.format("%s (%s)", locale.getDisplayLanguage(), locale.getDisplayCountry());
                String langCode = String.format("%s-%s", locale.getLanguage(), locale.getCountry());
                language.add(langDisplay, langCode);
            }
            return language;
        }

        public ListBoxModel doFillNotificationEmailTypeItems() {
            return cloudSettings.doFillNotificationEmailTypeItems();
        }

        public ListBoxModel doFillTestCasesSelectItems() {
            ListBoxModel testCases = new ListBoxModel();
            String value;
            for (APITestRunConfig.LimitationType limitationType : APITestRunConfig.LimitationType.values()) {
                value = limitationType.name();
                testCases.add(value.toLowerCase(), value);
            }
            return testCases;
        }

        public ListBoxModel doFillTestRunStateCheckMethodItems() {
            ListBoxModel items = new ListBoxModel();
            for (TestRunStateCheckMethod method : TestRunStateCheckMethod.values()) {
                items.add(method.name(), method.name());
            }
            return items;
        }

    }


    public static final class CloudStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1;

        @Inject
        private transient PipelineCloudStep step;

        @StepContextParameter
        private transient TaskListener listener;

        @StepContextParameter
        private transient Launcher launcher;

        @StepContextParameter
        private transient Run<?,?> run;

        @StepContextParameter
        private transient FilePath workspace;

        @Override
        protected Void run() throws Exception {
            WaitForResultsBlock waitForResultsBlock = null;
            if (step.isWaitForResults()) {
                waitForResultsBlock = new WaitForResultsBlock(
                        step.getTestRunStateCheckMethod(),
                        step.getHookURL(),
                        step.getWaitForResultsTimeout(),
                        step.getResultsPath(),
                        step.isDownloadScreenshots(),
                        step.isForceFinishAfterBreak()
                );
            }

            RunInCloudBuilder builder = new RunInCloudBuilder(
                    step.getProjectId(),
                    step.getAppPath(),
                    step.getTestPath(),
                    step.getDataPath(),
                    step.getTestRunName(),
                    step.getScheduler(),
                    step.getTestRunner(),
                    step.getDeviceGroupId(),
                    step.getLanguage(),
                    step.getNotificationEmail(),
                    step.getScreenshotsDirectory(),
                    step.getKeyValuePairs(),
                    step.getWithAnnotation(),
                    step.getWithoutAnnotation(),
                    step.getTestCasesSelect(),
                    step.getTestCasesValue(),
                    step.getNotificationEmailType(),
                    step.isFailBuildIfThisStepFailed(),
                    waitForResultsBlock,
                    step.getTestTimeout(),
                    step.getCredentialsId(),
                    step.getCloudUrl()
            );

            if(!builder.completeRun(run, workspace, launcher, listener)) {
                throw new Exception("runInTests Jenkins run failed!");
            }

            return null;
        }
    }
}
