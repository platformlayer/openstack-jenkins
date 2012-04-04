package hudson.plugins.openstack;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import com.google.common.collect.Sets;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OpenstackBucketPublisher extends Recorder implements Describable<Publisher> {

    private String profileName;
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private final List<Entry> entries = new ArrayList<Entry>();


    @DataBoundConstructor
    public OpenstackBucketPublisher() {
        super();
    }

    public OpenstackBucketPublisher(String profileName) {
        super();
        if (profileName == null) {
            // defaults to the first one
            OpenstackProfile[] sites = DESCRIPTOR.getProfiles();
            if (sites.length > 0)
                profileName = sites[0].getName();
        }
        this.profileName = profileName;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public OpenstackProfile getProfile() {
        OpenstackProfile[] profiles = DESCRIPTOR.getProfiles();

        if (profileName == null && profiles.length > 0)
            // default
            return profiles[0];

        for (OpenstackProfile profile : profiles) {
            if (profile.getName().equals(profileName))
                return profile;
        }
        return null;
    }

    public String getName() {
        return this.profileName;
    }

    public void setName(String profileName) {
        this.profileName = profileName;
    }

    protected void log(final PrintStream logger, final String message) {
        logger.println(StringUtils.defaultString(getDescriptor().getDisplayName()) + " " + message);
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

        OpenstackProfile profile = getProfile();
        if (profile == null) {
            log(listener.getLogger(), "No OpenStack profile is configured.");
            build.setResult(Result.UNSTABLE);
            return true;
        }
        log(listener.getLogger(), "Using OpenStack profile: " + profile.getName());
        try {
            Map<String, String> envVars = build.getEnvironment(listener);

            Set<String> knownExistsBuckets = Sets.newHashSet();
            
            for (Entry entry : entries) {
                String expanded = Util.replaceMacro(entry.sourceFile, envVars);
                FilePath ws = build.getWorkspace();
                FilePath[] paths = ws.list(expanded);

                if (paths.length == 0) {
                    // try to do error diagnostics
                    log(listener.getLogger(), "No file(s) found: " + expanded);
                    String error = ws.validateAntFileMask(expanded);
                    if (error != null)
                        log(listener.getLogger(), error);
                }
                String bucket = Util.replaceMacro(entry.bucket, envVars);
                for (FilePath src : paths) {
                    if (!knownExistsBuckets.contains(bucket)) {
                    	log(listener.getLogger(), " checking container: " + bucket);
                        profile.ensureBucket(bucket);
                        knownExistsBuckets.add(bucket);
                    }

                    log(listener.getLogger(), " container: " + bucket + ", file: " + src.getName());
                    profile.upload(bucket, src);
                }
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to upload files"));
            build.setResult(Result.UNSTABLE);
        }
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.STEP;
    }

    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private final CopyOnWriteList<OpenstackProfile> profiles = new CopyOnWriteList<OpenstackProfile>();
        private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

        public DescriptorImpl(Class<? extends Publisher> clazz) {
            super(clazz);
            load();
        }

        public DescriptorImpl() {
            this(OpenstackBucketPublisher.class);
        }

        @Override
        public String getDisplayName() {
            return "Publish artifacts to OpenStack Storage";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/openstack/help.html";
        }

        @Override
        public OpenstackBucketPublisher newInstance(StaplerRequest req, net.sf.json.JSONObject formData) throws FormException {
            OpenstackBucketPublisher pub = new OpenstackBucketPublisher();
            req.bindParameters(pub, "openstack.");
            pub.getEntries().addAll(req.bindParametersToList(Entry.class, "openstack.entry."));
            return pub;
        }

        @Override
        public boolean configure(StaplerRequest req, net.sf.json.JSONObject json) throws FormException {
            profiles.replaceBy(req.bindParametersToList(OpenstackProfile.class, "openstack."));
            save();
            return true;
        }



        public OpenstackProfile[] getProfiles() {
            return profiles.toArray(new OpenstackProfile[0]);
        }

        public FormValidation doLoginCheck(final StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            String name = Util.fixEmpty(req.getParameter("name"));
            if (name == null) {// name is not entered yet
                return FormValidation.ok();

            }
            
            String authUrl = Util.fixEmpty(req.getParameter("authUrl"));
            if (authUrl == null) {// authUrl is not entered yet
                return FormValidation.ok();
            }
            
            String tenant = Util.fixEmpty(req.getParameter("tenant"));
            
            String accessKey = Util.fixEmpty(req.getParameter("accessKey"));
			String secretKey = Util.fixEmpty(req.getParameter("secretKey"));

			if (accessKey == null || secretKey == null) {
			    return FormValidation.error("Username / Password required");
	        }

            try {
                
				OpenstackProfile profile = new OpenstackProfile(name, authUrl, tenant, accessKey, secretKey);
                profile.check();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                return FormValidation.error("Can't connect to OpenStack service: " + e.getMessage());
            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }
    }
}
