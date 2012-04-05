package jenkins.plugins.openstack;

import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.io.PrintStream;

import org.openstack.client.InstanceState;
import org.openstack.client.OpenstackException;
import org.openstack.model.compute.Server;

/**
 * {@link ComputerLauncher} for EC2 that waits for the instance to really come up before proceeding to
 * the real user-specified {@link ComputerLauncher}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class OpenstackComputerLauncher extends ComputerLauncher {
    @Override
    public void launch(SlaveComputer _computer, TaskListener listener) {
        try {
            OpenstackComputer computer = (OpenstackComputer)_computer;
            PrintStream logger = listener.getLogger();

            OUTER:
            while(true) {
                InstanceState instanceState = computer.getState();
                if (instanceState.isStarting()) {
                    Thread.sleep(5000); // check every 5 secs
                    continue OUTER;
                }

                if (instanceState.isActive()) {
                    break OUTER;
                }

                if (instanceState.isTerminated() || instanceState.isTerminating()) {
                    // abort
                    logger.println("The instance "+computer.getInstanceId()+" appears to be shut down. Aborting launch.");
                    return;
                }
                
                logger.println("The instance "+computer.getInstanceId()+" is in an unknown state (" + instanceState + ").");
            }

            launch(computer, logger, computer.describeInstance());
        } catch (OpenstackException e) {
            e.printStackTrace(listener.error(e.getMessage()));
        } catch (IOException e) {
            e.printStackTrace(listener.error(e.getMessage()));
        } catch (InterruptedException e) {
            e.printStackTrace(listener.error(e.getMessage()));
        }

    }

    /**
     * Stage 2 of the launch. Called after the EC2 instance comes up.
     */
    protected abstract void launch(OpenstackComputer computer, PrintStream logger, Server details)
            throws OpenstackException, IOException, InterruptedException;
}