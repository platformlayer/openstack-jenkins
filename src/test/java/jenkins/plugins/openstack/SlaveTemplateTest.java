//package jenkins.plugins.openstack;
//
//
//import java.util.ArrayList;
//import java.util.List;
//
//import jenkins.plugins.openstack.OpenstackCloud;
//import jenkins.plugins.openstack.OpenstackSlave;
//import jenkins.plugins.openstack.SlaveTemplate;
//
//import org.jvnet.hudson.test.HudsonTestCase;
//import org.openstack.model.compute.Flavor;
//
///**
// * Basic test to validate SlaveTemplate.
// */
//public class SlaveTemplateTest extends HudsonTestCase {
//
//    protected void setUp() throws Exception {
//    	super.setUp();
//    	OpenstackCloud.testMode = true;
//    }
//	
//    protected void tearDown() throws Exception {
//    	super.tearDown();
//    	OpenstackCloud.testMode = false;
//    }
//	
//    public void testConfigRoundtrip() throws Exception {
//        String imageId = "ami1";
//        SlaveTemplate orig = new SlaveTemplate(imageId, OpenstackSlave.TEST_ZONE, "foo", "22", Flavor.DefaultNames.M1_LARGE, "ttt", "foo ami", "bar", "aaa", "10", "rrr", "fff", "-Xmx1g", false);
//        List<SlaveTemplate> templates = new ArrayList<SlaveTemplate>();
//        templates.add(orig);
//        OpenstackCloud ac = new StandardOpenstackCloud( "abc", "def", "us-east-1", "ghi", "3", templates);
//        hudson.clouds.add(ac);
//        submit(createWebClient().goTo("configure").getFormByName("config"));
//        SlaveTemplate received = ((OpenstackCloud)hudson.clouds.iterator().next()).getTemplate(ami);
//        assertEqualBeans(orig, received, "imageId,zone,description,remoteFS,type,jvmopts,stopOnTerminate");
//    }
//}
