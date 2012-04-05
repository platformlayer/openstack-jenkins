//package jenkins.plugins.openstack;
//
//import org.jvnet.hudson.test.HudsonTestCase;
//
//
//import java.util.Collections;
//
//import jenkins.plugins.openstack.AmazonEC2Cloud;
//import jenkins.plugins.openstack.SlaveTemplate;
//
///**
// * @author Kohsuke Kawaguchi
// */
//public class AmazonEC2CloudTest extends HudsonTestCase {
//
//	protected void setUp() throws Exception {
//		super.setUp();
//		OpenstackCloud.testMode = true;
//	}
//
//	protected void tearDown() throws Exception {
//		super.tearDown();
//		OpenstackCloud.testMode = false;
//	}
//
//	public void testConfigRoundtrip() throws Exception {
//	    OpenstackCloud orig = new StandardOpenstackCloud("abc", "def", "us-east-1",
//				"ghi", "3", Collections.<SlaveTemplate> emptyList());
//		hudson.clouds.add(orig);
//		submit(createWebClient().goTo("configure").getFormByName("config"));
//
//		assertEqualBeans(orig, hudson.clouds.iterator().next(),
//				"region,accessId,secretKey,privateKey,instanceCap");
//	}
//}
