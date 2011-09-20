package com.hyperic.hudson.plugin;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class S3Descriptor extends BuildStepDescriptor<Publisher> {

    private final CopyOnWriteList<S3Profile> profiles = new CopyOnWriteList<S3Profile>();
    private static final Logger LOGGER = Logger.getLogger(S3Descriptor.class.getName());

    public S3Descriptor(Class<? extends Publisher> clazz) {
        super(clazz);
    }

    public S3Descriptor() {
        super(S3BucketPublisher.class);
    }

    @Override
    public String getDisplayName() {
        return "Publish artifacts to S3 Bucket";
    }

    @Override
    public String getHelpFile() {
        return "/plugin/s3/help.html";
    }

    @Override
    public Publisher newInstance(StaplerRequest req, net.sf.json.JSONObject formData) throws FormException {
        S3BucketPublisher pub = new S3BucketPublisher();
        req.bindParameters(pub, "s3.");
        pub.getEntries().addAll(req.bindParametersToList(Entry.class, "s3.entry."));
        return pub;
    }

    @Override
    public boolean configure(StaplerRequest req, net.sf.json.JSONObject json) throws FormException {
        profiles.replaceBy(req.bindParametersToList(S3Profile.class, "s3."));
        save();
        return true;
    }

    public S3Profile[] getProfiles() {
        return profiles.toArray(new S3Profile[0]);
    }

    public FormValidation doLoginCheck(final StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {

        String name = Util.fixEmpty(req.getParameter("name"));
        if (name == null) {// name is not entered yet
            return FormValidation.ok();

        }
        S3Profile profile = new S3Profile(name, req.getParameter("accessKey"), req.getParameter("secretKey"));

        try {
            profile.check();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            return FormValidation.error("Can't connect to S3 service: " + e.getMessage());
        }
        return FormValidation.ok();
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
        return true;
    }
}
