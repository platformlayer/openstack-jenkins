package jenkins.plugins.openstack;

import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.Set;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.openstack.client.CloudCapabilities;
import org.openstack.client.OpenstackException;
import org.openstack.client.OpenstackNotFoundException;
import org.openstack.client.common.OpenstackComputeClient;
import org.openstack.model.compute.Flavor;
import org.openstack.model.compute.Image;
import org.openstack.model.compute.KeyPair;
import org.openstack.model.compute.Server;
import org.openstack.model.compute.ServerForCreate;
import org.openstack.utils.Utf8;

/**
 * Template of {@link OpenstackSlave} to launch.
 *
 * @author Kohsuke Kawaguchi
 */
public class SlaveTemplate implements Describable<SlaveTemplate> {
    public final String imageId;
    public final String description;
    public final String zone;
    public final String remoteFS;
    public final String sshPort;
    public final String flavorId;
    public final String labels;
    public final String initScript;
    public final String userData;
    public final String numExecutors;
    public final String remoteAdmin;
    public final String rootCommandPrefix;
    public final String jvmopts;
    public final boolean stopOnTerminate;
    protected transient OpenstackCloud parent;
    

    private transient /*almost final*/ Set<LabelAtom> labelSet;

    @DataBoundConstructor
    public SlaveTemplate(String imageId, String zone, String remoteFS, String sshPort, String flavorId, String labelString, String description, String initScript, String userData, String numExecutors, String remoteAdmin, String rootCommandPrefix, String jvmopts, boolean stopOnTerminate) {
        this.imageId = imageId;
        this.zone = zone;
        this.remoteFS = remoteFS;
        this.sshPort = sshPort;
        this.flavorId = flavorId;
        this.labels = Util.fixNull(labelString);
        this.description = description;
        this.initScript = initScript;
        this.userData = userData;
        this.numExecutors = Util.fixNull(numExecutors).trim();
        this.remoteAdmin = remoteAdmin;
        this.rootCommandPrefix = rootCommandPrefix;
        this.jvmopts = jvmopts;
        this.stopOnTerminate = stopOnTerminate;
        readResolve(); // initialize
    }
    
    public OpenstackCloud getParent() {
        return parent;
    }

    public String getLabelString() {
        return labels;
    }

    public String getDisplayName() {
        return description+" ("+imageId+")";
    }

    String getZone() {
        return zone;
    }

    public int getNumExecutors() {
        try {
            return Integer.parseInt(numExecutors);
        } catch (NumberFormatException e) {
            return OpenstackSlave.toNumExecutors(getFlavor());
        }
    }

    private Flavor getFlavor() {
    	OpenstackComputeClient compute = getParent().connect().getComputeClient();
    	Flavor flavor = compute.root().flavors().flavor(flavorId).show();
    	return flavor;
	}

	public int getSshPort() {
        try {
            return Integer.parseInt(sshPort);
        } catch (NumberFormatException e) {
            return 22;
        }
    }
    public String getRemoteAdmin() {
        return remoteAdmin;
    }

    public String getRootCommandPrefix() {
        return rootCommandPrefix;
    }
    
    public Set getLabelSet(){
    	return labelSet;
    }
    
    /**
     * Does this contain the given label?
     *
     * @param l
     *      can be null to indicate "don't care".
     */
    public boolean containsLabel(Label l) {
        return l==null || labelSet.contains(l);
    }

    /**
     * Provisions a new OpenStack slave.
     *
     * @return always non-null. This needs to be then added to {@link Hudson#addNode(Node)}.
     */
    public OpenstackSlave provision(TaskListener listener) throws OpenstackException, IOException {
        PrintStream logger = listener.getLogger();
        String cloudId = getParent().getCloudId();
        OpenstackComputeClient compute = getParent().connect().getComputeClient();

        try {
            logger.println("Launching "+imageId);

            ServerForCreate request = new ServerForCreate();
            if (StringUtils.isNotBlank(getZone())) {
                request.setZone(getZone());
            }
            request.setImageRef(imageId);
            
            // TODO: Accept name, or (better) use a dropdown
            request.setFlavorRef(flavorId);

            request.setName("Jenkins slave");
            
            // RunInstancesRequest request = new RunInstancesRequest(imageId, 1, 1);
            // if (StringUtils.isNotBlank(getZone())) {
            // Placement placement = new Placement(getZone());
            // request.setPlacement(placement);
            // }
            // request.setUserData(userData);
            // request.setKeyName(keyPair.getKeyName());
            // request.setInstanceType(type.toString());
            // Instance inst = compute.runInstances(request).getReservation().getInstances().get(0);
            
            CloudCapabilities capabalities = compute.getSession().getCapabilities();
            if (capabalities.supportsSshKeys()) {
                OpenstackSshKey sshKeyPair = parent.getSshKeyPair();
                KeyPair keyPair = sshKeyPair.getOrCreate(compute);
                if (keyPair == null) {
                    // Unexpected ... getOrCreate should create it!
                    throw new OpenstackException(
                            "No matching keypair found on OpenStack. Is the OpenStack private key a valid one?");
                }
                
                request.setKeyName(keyPair.getName());
            }
            else {
                // TODO: Verify that file injection is supported
                OpenstackSshKey sshKeyPair = parent.getSshKeyPair();
                
                String fileContents = sshKeyPair.getPublicKey();
                request.addUploadFile("/root/.ssh/authorized_keys", Utf8.getBytes(fileContents));
            }
            if (StringUtils.isNotBlank(userData)) {
            	throw new IllegalArgumentException("userData not supported");
            }
            
            Server created = compute.root().servers().create(request);
            return newSlave(cloudId, created);
        } catch (FormException e) {
            throw new AssertionError(); // we should have discovered all configuration issues upfront
        }
    }

    private OpenstackSlave newSlave(String cloudId, Server inst) throws FormException, IOException {
        return new OpenstackSlave(cloudId, inst.getId(), description, remoteFS, getSshPort(), getNumExecutors(), labels, initScript, remoteAdmin, rootCommandPrefix, jvmopts, stopOnTerminate);
    }

    /**
     * Provisions a new EC2 slave based on the currently running instance on EC2,
     * instead of starting a new one.
     */
    public OpenstackSlave attach(String instanceId, TaskListener listener) throws OpenstackException, IOException {
        PrintStream logger = listener.getLogger();
        String cloudId = getParent().getCloudId();
        OpenstackComputeClient ec2 = getParent().connect().getComputeClient();

        try {
            logger.println("Attaching to "+instanceId);
            Server details = ec2.root().servers().server(instanceId).get().show();
            return newSlave(cloudId, details);
        } catch (FormException e) {
            throw new AssertionError(); // we should have discovered all configuration issues upfront
        }
    }

    /**
     * Initializes data structure that we don't persist.
     */
    protected Object readResolve() {
        labelSet = Label.parse(labels);
        return this;
    }

    public Descriptor<SlaveTemplate> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<SlaveTemplate> {
        @Override
		public String getDisplayName() {
            return null;
        }

        /**
         * Since this shares much of the configuration with {@link OpenstackComputer}, check its help page, too.
         */
        @Override
        public String getHelpFile(String fieldName) {
            String p = super.getHelpFile(fieldName);
            if (p==null)        p = Hudson.getInstance().getDescriptor(OpenstackSlave.class).getHelpFile(fieldName);
            return p;
        }

        /***
         * Check that the Image requested is available in the cloud and can be used.
         */
        public FormValidation doValidateImageId(
                @QueryParameter String authUrl, 
                @QueryParameter String accessId, 
                @QueryParameter String tenant, 
                @QueryParameter String secretKey,
                @QueryParameter String region,
                @QueryParameter String imageId) throws IOException, ServletException {
            OpenstackComputeClient ec2 = OpenstackCloud.connect(new URL(authUrl), accessId, tenant, secretKey).getComputeClient();
            if(ec2!=null) {
                try {
//                    List<String> images = new LinkedList<String>();
//                    images.add(imageId);
//                    List<String> owners = new LinkedList<String>();
//                    List<String> users = new LinkedList<String>();
//                    DescribeImagesRequest request = new DescribeImagesRequest();
//                    request.setImageIds(images);
//                    request.setOwners(owners);
//                    request.setExecutableUsers(users);
//                    List<Image> img = ec2.describeImages(request).getImages();
                	Image image = null;
                	try {
                		image = ec2.root().images().image(imageId).show();
                	}
                	catch (OpenstackNotFoundException e) {
                		image = null;
                	}
                	
                    if(image==null)
                        // de-registered Image causes an empty list to be returned. so be defensive
                        // against other possibilities
                        return FormValidation.error("No such Image, or not usable with this accessId: "+imageId);
//                    return FormValidation.ok(image.getImageLocation()+" by "+img.get(0).getImageOwnerAlias());
                    return FormValidation.ok("Image: " + image.getName());
                } catch (OpenstackException e) {
                    return FormValidation.error(e.getMessage());
                }
            } else
                return FormValidation.ok();   // can't test
        }
        
        public ListBoxModel doFillZoneItems(
                @QueryParameter String authUrl,
                @QueryParameter String accessId,
                @QueryParameter String tenant,
                @QueryParameter String secretKey, 
        		@QueryParameter String region) throws IOException,
    			ServletException {
        	return OpenstackSlave.fillZoneItems(new URL(authUrl), accessId, tenant, secretKey, region);
    	}
        
    }
}
