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
 * Builder class for the UI plugin functionality
 */

public class RunInCloudBuilder extends AbstractBuilder implements SimpleBuildStep {

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public RunInCloudBuilder(
            String projectId, String appPath, String testPath, String dataPath, String testRunName, String scheduler,
            String testRunner, String clusterId, String language, String notificationEmail, String screenshotsDirectory,
            String keyValuePairs, String withAnnotation, String withoutAnnotation, String testCasesSelect,
            String testCasesValue, String notificationEmailType, Boolean failBuildIfThisStepFailed,
            WaitForResultsBlock waitForResultsBlock, String testTimeout) {
        this.projectId = projectId;
        this.appPath = appPath;
        this.dataPath = dataPath;
        this.testPath = testPath;
        this.testRunName = testRunName;
        this.scheduler = scheduler;
        this.testRunner = testRunner;
        this.screenshotsDirectory = screenshotsDirectory;
        this.keyValuePairs = keyValuePairs;
        this.withAnnotation = withAnnotation;
        this.withoutAnnotation = withoutAnnotation;
        this.testCasesSelect = testCasesSelect;
        this.testCasesValue = testCasesValue;
        this.clusterId = clusterId;
        this.language = language;
        this.notificationEmail = notificationEmail;
        this.notificationEmailType = notificationEmailType;
        this.failBuildIfThisStepFailed = failBuildIfThisStepFailed;
        this.testTimeout = testTimeout;

        this.waitForResultsBlock = new WaitForResultsBlock()
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        listener.getLogger().println(Messages.RUN_TEST_IN_CLOUD_STARTED());

        boolean result = runTest(build, build.getWorkspace(), launcher, listener);
        if (result) {
            listener.getLogger().println(Messages.RUN_TEST_IN_CLOUD_SUCCEEDED());
        } else {
            listener.getLogger().println(Messages.RUN_TEST_IN_CLOUD_FAILED());
        }

        return result || !failBuildIfThisStepFailed;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> implements Serializable {

        private static final long serialVersionUID = 1L;

        public DescriptorImpl() {
            super(RunInCloudBuilder.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.TESTDROID_RUN_TESTS_IN_CLOUD();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> arg0) {
            return true;
        }

        public boolean isAuthenticated() {
            try {
                return TestdroidCloudSettings.descriptor().getUser() != null;
            } catch (APIException e) {
                LOGGER.log(Level.WARNING, Messages.ERROR_API());
                return false;
            }
        }

        public boolean isPaidUser() {
            boolean result = false;
            if (isAuthenticated()) {
                try {
                    Date now = new Date();
                    APIUser user = TestdroidCloudSettings.descriptor().getUser();
                    for (APIRole role : user.getRoles()) {
                        if (PAID_ROLES.contains(role.getName())
                                && (role.getExpireTime() == null || role.getExpireTime().after(now))) {
                            result = true;
                        }
                    }
                } catch (APIException e) {
                    LOGGER.log(Level.WARNING, Messages.ERROR_API());
                }

            }
            return result;
        }

        public ListBoxModel doFillProjectIdItems() {
            ListBoxModel projects = new ListBoxModel();
            try {
                APIUser user = TestdroidCloudSettings.descriptor().getUser();
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

        public ListBoxModel doFillClusterIdItems() {
            ListBoxModel deviceGroups = new ListBoxModel();
            try {
                APIUser user = TestdroidCloudSettings.descriptor().getUser();
                List<APIDeviceGroup> list = user.getDeviceGroupsResource(new APIDeviceGroupQueryBuilder().withPublic()
                        .limit(Integer.MAX_VALUE)).getEntity().getData();
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
                String langDisplay = String.format("%s (%s)", locale.getDisplayLanguage(),
                        locale.getDisplayCountry());
                String langCode = String.format("%s-%s", locale.getLanguage(), locale.getCountry());
                language.add(langDisplay, langCode);
            }
            return language;
        }

        public ListBoxModel doFillNotificationEmailTypeItems() {
            return TestdroidCloudSettings.descriptor().doFillNotificationEmailTypeItems();
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
}
