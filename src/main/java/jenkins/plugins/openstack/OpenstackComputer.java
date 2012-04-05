package jenkins.plugins.openstack;

import hudson.Extension;
import hudson.Util;
import hudson.model.Slave.SlaveDescriptor;
import hudson.slaves.SlaveComputer;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;

import javax.servlet.ServletException;

import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.openstack.client.InstanceState;
import org.openstack.client.OpenstackException;
import org.openstack.client.common.OpenstackComputeClient;
import org.openstack.client.common.OpenstackSession;
import org.openstack.model.compute.Addresses.Network;
import org.openstack.model.compute.Addresses.Network.Ip;
import org.openstack.model.compute.Server;

import com.google.common.base.Strings;

/**
 * @author Kohsuke Kawaguchi
 */
public class OpenstackComputer extends SlaveComputer {
    /**
     * Cached details of this cloud instance. Lazily fetched.
     */
    private volatile Server instanceDetails;

    public OpenstackComputer(OpenstackSlave slave) {
        super(slave);
    }

    @Override
    public OpenstackSlave getNode() {
        return (OpenstackSlave)super.getNode();
    }

    public String getCloudId() {
    	return getNode().cloudId;
    }
    
    public String getInstanceId() {
        return getName();
    }

    /**
     * Gets the VM's console output.
     */
    public String getConsoleOutput() throws OpenstackException {
    	OpenstackComputeClient computeClient = getComputeClient();
    	String consoleOutput = computeClient.root().servers().server(getInstanceId()).getConsoleOutput(null);
    	return consoleOutput;
    }

	private OpenstackComputeClient getComputeClient() {
		OpenstackSession session = OpenstackCloud.get(getCloudId()).connect();
    	OpenstackComputeClient computeClient = session.getComputeClient();
		return computeClient;
	}

    /**
     * Obtains the instance state description in EC2.
     *
     * <p>
     * This method returns a cached state, so it's not suitable to check {@link Instance#getState()}
     * and {@link Instance#getStateCode()} from the returned instance (but all the other fields are valid as it won't change.)
     *
     * The cache can be flushed using {@link #updateInstanceDescription()}
     */
    public Server describeInstance() throws OpenstackException {
        if(instanceDetails ==null)
        	instanceDetails = _describeInstance();
        return instanceDetails;
    }

    /**
     * This will flush any cached description held by {@link #describeInstance()}.
     */
    public Server updateInstanceDescription() throws OpenstackException {
        return instanceDetails = _describeInstance();
    }

    /**
     * Gets the current state of the instance.
     *
     * <p>
     * Unlike {@link #describeInstance()}, this method always return the current status by calling EC2.
     */
    public InstanceState getState() throws OpenstackException {
        instanceDetails=_describeInstance();
        return InstanceState.get(instanceDetails);
    }

    /**
     * Number of milli-secs since the instance was started.
     */
    public long getUptime() throws OpenstackException {
    	Date created = describeInstance().getCreated();
    	
        return System.currentTimeMillis()-created.getTime();
    }

    /**
     * Returns uptime in the human readable form.
     */
    public String getUptimeString() throws OpenstackException {
        return Util.getTimeSpanString(getUptime());
    }

    private Server _describeInstance() throws OpenstackException {
    	Server server = getComputeClient().root().servers().server(getInstanceId()).get().show();
    	return server;
//    	DescribeInstancesRequest request = new DescribeInstancesRequest();
//    	request.setInstanceIds(Collections.<String>singletonList(getNode().getInstanceId()));
//        return OpenstackCloud.get().connect().describeInstances(request).getReservations().get(0).getInstances().get(0);
    }

    /**
     * When the slave is deleted, terminate the instance.
     */
    @Override
    public HttpResponse doDoDelete() throws IOException {
        checkPermission(DELETE);
        getNode().terminate();
        return new HttpRedirect("..");
    }

    /** What username to use to run root-like commands
     *
     */
    public String getRemoteAdmin() {
        return getNode().getRemoteAdmin();
    }

    public int getSshPort() {
         return getNode().getSshPort();
     }

    public String getRootCommandPrefix() {
        return getNode().getRootCommandPrefix();
    }

	public String getPublicHost(Server details) {
		String address = details.getAccessIpV4();
		if (!Strings.isNullOrEmpty(address)) {
			return address;
		}

        for (Network network : details.getAddresses().getNetworks()) {
            if ("public".equals(network.getId())) {
                for (Ip ip : network.getIps()) {
                    if (Strings.isNullOrEmpty(ip.getAddr()))
                        continue;

                    if (!"4".equals(ip.getVersion()))
                        continue;

                    return ip.getAddr();
                }
            }
        }

        // If we couldn't find a public IP, see if there is a private IP
        // This happens in private clouds
        // TODO: Clean up this logic
        for (Network network : details.getAddresses().getNetworks()) {
            for (Ip ip : network.getIps()) {
                if (Strings.isNullOrEmpty(ip.getAddr()))
                    continue;

                if (!"4".equals(ip.getVersion()))
                    continue;

                return ip.getAddr();
            }
        }

		return null;
	}
    
}
