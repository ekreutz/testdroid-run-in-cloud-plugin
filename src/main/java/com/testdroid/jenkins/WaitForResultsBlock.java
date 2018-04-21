package com.testdroid.jenkins;

public static class WaitForResultsBlock {

    private boolean downloadScreenshots;

    private boolean forceFinishAfterBreak;

    private String hookURL = "";

    private String resultsPath = "";

    private TestRunStateCheckMethod testRunStateCheckMethod;

    private Integer waitForResultsTimeout;

    @DataBoundConstructor
    public WaitForResultsBlock(String testRunStateCheckMethod, String hookURL, String waitForResultsTimeout, String resultsPath, boolean downloadScreenshots, boolean forceFinishAfterBreak) {
        this.testRunStateCheckMethod = TestRunStateCheckMethod.valueOf(testRunStateCheckMethod);
        this.hookURL = hookURL;
        this.resultsPath = resultsPath;
        this.downloadScreenshots = downloadScreenshots;
        this.forceFinishAfterBreak = forceFinishAfterBreak;
        this.waitForResultsTimeout = NumberUtils.toInt(waitForResultsTimeout);
    }

    public String getHookURL() {
        return hookURL;
    }

    public void setHookURL(String hookURL) {
        this.hookURL = hookURL;
    }

    public Integer getWaitForResultsTimeout() {
        if (waitForResultsTimeout == null) {
            waitForResultsTimeout = 0;
        }
        return waitForResultsTimeout;
    }

    public void setWaitForResultsTimeout(Integer waitForResultsTimeout) {
        this.waitForResultsTimeout = waitForResultsTimeout;
    }

    public String getResultsPath() {
        return resultsPath;
    }

    public void setResultsPath(String resultsPath) {
        this.resultsPath = resultsPath;
    }

    public boolean isDownloadScreenshots() {
        return downloadScreenshots;
    }

    public void setDownloadScreenshots(boolean downloadScreenshots) {
        this.downloadScreenshots = downloadScreenshots;
    }

    public TestRunStateCheckMethod getTestRunStateCheckMethod() {
        if (testRunStateCheckMethod == null) {
            testRunStateCheckMethod = TestRunStateCheckMethod.HOOK_URL;
        }
        return testRunStateCheckMethod;
    }

    public void setTestRunStateCheckMethod(TestRunStateCheckMethod testRunStateCheckMethod) {
        this.testRunStateCheckMethod = testRunStateCheckMethod;
    }

    public boolean isForceFinishAfterBreak() {
        return forceFinishAfterBreak;
    }

    public void setForceFinishAfterBreak(boolean forceFinishAfterBreak) {
        this.forceFinishAfterBreak = forceFinishAfterBreak;
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(WaitForResultsBlock.class);
        }

        @Override
        public String getFunctionName() {
            return "waitForResults";
        }

        @Override
        public String getDisplayName() {
            return "Wait for Bitbar Cloud results";
        }
    }
}
