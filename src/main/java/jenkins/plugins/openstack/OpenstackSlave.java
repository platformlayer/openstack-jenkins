package jenkins.plugins.openstack;

import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.Slave;
import hudson.slaves.NodeProperty;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import jenkins.plugins.openstack.ssh.OpenstackUnixLauncher;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.openstack.client.OpenstackException;
import org.openstack.client.common.OpenstackComputeClient;
import org.openstack.model.compute.Flavor;
import org.openstack.model.compute.Zone;

/**
 * Slave running on OpenStack.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class OpenstackSlave extends Slave {
	public final String cloudId;
    /**
     * Comes from {@link SlaveTemplate#initScript}.
     */
    public final String initScript;
    public final String remoteAdmin; // e.g. 'ubuntu'
    public final String rootCommandPrefix; // e.g. 'sudo'
    public final String jvmopts; //e.g. -Xmx1g
    public final boolean stopOnTerminate;

    /**
     * For data read from old Hudson, this is 0, so we use that to indicate 22.
     */
    private final int sshPort;

    public static final String TEST_ZONE = "testZone";
    
    public OpenstackSlave(String cloudId, String instanceId, String description, String remoteFS, int sshPort, int numExecutors, String labelString, String initScript, String remoteAdmin, String rootCommandPrefix, String jvmopts, boolean stopOnTerminate) throws FormException, IOException {
        this(cloudId, instanceId, description, remoteFS, sshPort, numExecutors, Mode.NORMAL, labelString, initScript, Collections.<NodeProperty<?>>emptyList(), remoteAdmin, rootCommandPrefix, jvmopts, stopOnTerminate);
    }

    @DataBoundConstructor
    public OpenstackSlave(String cloudId, String instanceId, String description, String remoteFS, int sshPort, int numExecutors, Mode mode, String labelString, String initScript, List<? extends NodeProperty<?>> nodeProperties, String remoteAdmin, String rootCommandPrefix, String jvmopts, boolean stopOnTerminate) throws FormException, IOException {
        super(instanceId, description, remoteFS, numExecutors, mode, labelString, new OpenstackUnixLauncher(), new OpenstackRetentionStrategy(), nodeProperties);
        this.cloudId = cloudId;
        this.initScript  = initScript;
        this.remoteAdmin = remoteAdmin;
        this.rootCommandPrefix = rootCommandPrefix;
        this.jvmopts = jvmopts;
        this.sshPort = sshPort;
        this.stopOnTerminate = stopOnTerminate;
    }

    /**
     * Constructor for debugging.
     */
    public OpenstackSlave(String cloudId, String instanceId) throws FormException, IOException {
        this(cloudId, instanceId,"debug", "/tmp/hudson", 22, 1, Mode.NORMAL, "debug", "", Collections.<NodeProperty<?>>emptyList(), null, null, null, false);
    }

    /*package*/ static int toNumExecutors(Flavor flavor) {
        int executors = flavor.getVcpus();
        
        // TODO: Modify based on RAM?
        
        return executors;
    }

    /**
     * EC2 instance ID.
     */
    public String getInstanceId() {
        return getNodeName();
    }

    @Override
    public Computer createComputer() {
        return new OpenstackComputer(this);
    }

    /**
     * Terminates the OpenStack instance
     */
    public void terminate() {
        String instanceId = getInstanceId();
        try {
            OpenstackComputeClient compute = OpenstackCloud.get(cloudId).connect().getComputeClient();
            // TODO: What's the difference between stop & terminate?
            if (stopOnTerminate) {
            	compute.root().servers().server(instanceId).delete();
//            	StopInstancesRequest request = new StopInstancesRequest(Collections.singletonList(getInstanceId()));
//            	ec2.stopInstances(request);
                LOGGER.info("Terminated OpenStack instance (stopped): "+instanceId);
            } else {
            	compute.root().servers().server(instanceId).delete();
//            	TerminateInstancesRequest request = new TerminateInstancesRequest(Collections.singletonList(getInstanceId()));
//            	ec2.terminateInstances(request);
                LOGGER.info("Terminated OpenStack instance (terminated): "+instanceId);
            }
            Hudson.getInstance().removeNode(this);
        } catch (OpenstackException e) {
            LOGGER.log(Level.WARNING,"Failed to terminate OpenStack instance: "+instanceId,e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,"Failed to terminate OpenStack instance: "+instanceId,e);
        }
    }

    String getRemoteAdmin() {
        if (remoteAdmin == null || remoteAdmin.length() == 0)
            return "root";
        return remoteAdmin;
    }

    String getRootCommandPrefix() {
        if (rootCommandPrefix == null || rootCommandPrefix.length() == 0)
            return "";
        return rootCommandPrefix + " ";
    }

    String getJvmopts() {
        return Util.fixNull(jvmopts);
    }

    public int getSshPort() {
        return sshPort!=0 ? sshPort : 22;
    }

    public boolean getStopOnTerminate() {
        return stopOnTerminate;
    }

	public static ListBoxModel fillZoneItems(URL authUrl, 
	        String accessId, String tenant,
			String secretKey, String region) throws IOException,
			ServletException {
		ListBoxModel model = new ListBoxModel();
		if (OpenstackCloud.testMode) {
			model.add(TEST_ZONE);
			return model;
		}
			
		if (!StringUtils.isEmpty(accessId) && !StringUtils.isEmpty(secretKey) && !StringUtils.isEmpty(region)) {
			OpenstackComputeClient client = OpenstackCloud.connect(authUrl, accessId, tenant, secretKey).getComputeClient();
			model.add("<not specified>", "");
			for (Zone z : client.root().zones().list()) {
				model.add(z.getName(), z.getName());
			}
		}
		return model;
	}

    
    
    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {
        @Override
		public String getDisplayName() {
            return "OpenStack";
        }

        @Override
        public boolean isInstantiable() {
            return false;
        }

        public ListBoxModel doFillZoneItems(
                @QueryParameter String authUrl,
                @QueryParameter String accessId,
                @QueryParameter String tenant,
                @QueryParameter String secretKey, 
        		@QueryParameter String region) throws IOException,
    			ServletException {
        	return fillZoneItems(new URL(authUrl), accessId, tenant, secretKey, region);
    	}
    }

    private static final Logger LOGGER = Logger.getLogger(OpenstackSlave.class.getName());
}
