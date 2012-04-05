package jenkins.plugins.openstack.storage;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.slaves.Cloud;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.openstack.client.common.OpenstackSession;
import org.openstack.client.storage.OpenstackStorageClient;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import jenkins.plugins.openstack.OpenstackCloud;

public final class OpenstackBucketPublisher extends Recorder implements Describable<Publisher> {
    private String cloudId;
    
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private final List<Entry> entries = new ArrayList<Entry>();


    @DataBoundConstructor
    public OpenstackBucketPublisher() {
        super();
    }

    public OpenstackBucketPublisher(String cloudId) {
        super();
        if (cloudId == null) {
            // defaults to the first one
            OpenstackCloud cloud = OpenstackCloud.get(null);
            if (cloud != null) {
                cloudId = cloud.getCloudId();
            }
        }
        this.cloudId = cloudId;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public OpenstackCloud getProfile() {
        return OpenstackCloud.get(cloudId);
    }

    public String getCloudId() {
        return cloudId;
    }

    public void setCloudId(String cloudId) {
        this.cloudId = cloudId;
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

        OpenstackCloud cloud = getProfile();
        if (cloud == null) {
            log(listener.getLogger(), "No OpenStack cloud is configured.");
            build.setResult(Result.UNSTABLE);
            return true;
        }
        log(listener.getLogger(), "Using OpenStack cloud: " + cloud.getDisplayName());
        try {
            OpenstackSession session = cloud.connect();
            OpenstackStorageClient storageClient = session.getStorageClient();
            
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
                    	OpenstackStorage.ensureBucket(storageClient, bucket);
                        knownExistsBuckets.add(bucket);
                    }

                    log(listener.getLogger(), " container: " + bucket + ", file: " + src.getName());
                    OpenstackStorage.upload(storageClient, bucket, src);
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

        // TODO: Move to resources
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

        public OpenstackCloud[] getProfiles() {
            List<OpenstackCloud> openstackClouds = Lists.newArrayList();

            for (Cloud cloud : Hudson.getInstance().clouds) {
                if (cloud instanceof OpenstackCloud) {
                    OpenstackCloud openstackCloud = (OpenstackCloud) cloud;
                    openstackClouds.add(openstackCloud);
                }
            }
            
            return openstackClouds.toArray(new OpenstackCloud[0]);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }
    }
}
