package com.hyperic.hudson.plugin;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import hudson.tasks.Recorder;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class S3BucketPublisher extends Recorder {

    private String profileName;
    public static final Logger LOGGER = Logger.getLogger(S3BucketPublisher.class.getName());

    private final List<Entry> entries = new ArrayList<Entry>();

    public S3BucketPublisher() {
    }

    public S3BucketPublisher(String profileName) {

        if (profileName == null) {
            // defaults to the first one
            S3Profile[] sites = ((S3Descriptor) getDescriptor()).getProfiles();
            if (sites.length > 0)
                profileName = sites[0].getName();
            }
        }
        this.profileName = profileName;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public S3Profile getProfile() {
        S3Profile[] profiles = ((S3Descriptor) getDescriptor()).getProfiles();

        if (profileName == null && profiles.length > 0)
            // default
            return profiles[0];
        }

        for (S3Profile profile : profiles) {
            if (profile.getName().equals(profileName)) {
                return profile;
            }
        }
        return null;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build,
            Launcher launcher,
            BuildListener listener)
            throws InterruptedException, IOException {

        if (build.getResult() == Result.FAILURE) {
            // build failed. don't post
            return true;
        }

        S3Profile profile = getProfile();
        if (profile == null) {
            log(listener.getLogger(), "No S3 profile is configured.");
            build.setResult(Result.UNSTABLE);
            return true;
        }
        log(listener.getLogger(), "Using S3 profile: " + profile.getName());
        try {
            Map<String, String> envVars = build.getEnvironment(listener);

            log(listener.getLogger(), "Entries: " + entries);

            for (Entry entry : entries) {
                String expanded = Util.replaceMacro(entry.sourceFile, envVars);
                FilePath ws = build.getWorkspace();
                FilePath[] paths = ws.list(expanded);

                if (paths.length == 0) {
                    // try to do error diagnostics
                    log(listener.getLogger(), "No file(s) found: " + expanded);
                    String error = ws.validateAntFileMask(expanded);
                    if (error != null) {
                        log(listener.getLogger(), error);
                    }
                }
                String bucket = Util.replaceMacro(entry.bucket, envVars);
                for (FilePath src : paths) {
                    log(listener.getLogger(), "bucket=" + bucket + ", file=" + src.getName());
                    profile.upload(bucket, src);
                }
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to upload files"));
            build.setResult(Result.UNSTABLE);
        }
        return true;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.STEP; //prevent artifact overrides
    }

    public String getProfileName() {
        return this.profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }


    protected void log(final PrintStream logger, final String message) {
        logger.println(StringUtils.defaultString(getDescriptor().getDisplayName()) + " " + message);
    }
}
