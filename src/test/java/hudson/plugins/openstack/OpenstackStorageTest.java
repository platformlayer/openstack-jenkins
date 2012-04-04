package hudson.plugins.openstack;


import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.HudsonTestCase;

public class OpenstackStorageTest extends HudsonTestCase {

    public void testConfig() throws Exception {
        HtmlPage page = new WebClient().goTo("configure");
        WebAssert.assertTextPresent(page, "OpenStack profiles");
    }
}
