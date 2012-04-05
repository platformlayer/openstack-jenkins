package jenkins.plugins.openstack;

import hudson.model.Descriptor;
import hudson.slaves.RetentionStrategy;
import hudson.util.TimeUnit2;

import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link RetentionStrategy} for OpenStack.
 *
 * @author Kohsuke Kawaguchi
 */
public class OpenstackRetentionStrategy extends RetentionStrategy<OpenstackComputer> {
    @DataBoundConstructor
    public OpenstackRetentionStrategy() {
    }

    @Override
	public synchronized long check(OpenstackComputer c) {
        if (c.isIdle() && !disabled) {
            // TODO: really think about the right strategy here
            final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
            if (idleMilliseconds > TimeUnit2.MINUTES.toMillis(30)) {
                LOGGER.info("Disconnecting "+c.getName());
                c.getNode().terminate();
            }
        }
        return 1;
    }

    /**
     * Try to connect to it ASAP.
     */
    @Override
    public void start(OpenstackComputer c) {
        c.connect(false);
    }

    // no registration since this retention strategy is used only for EC2 nodes that we provision automatically.
    // @Extension
    public static class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
        @Override
		public String getDisplayName() {
            return "Openstack";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(OpenstackRetentionStrategy.class.getName());

    public static boolean disabled = Boolean.getBoolean(OpenstackRetentionStrategy.class.getName()+".disabled");
}
