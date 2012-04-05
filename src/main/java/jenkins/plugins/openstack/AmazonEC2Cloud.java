//package jenkins.plugins.openstack;
//
//import hudson.Extension;
//import hudson.util.FormValidation;
//import hudson.util.ListBoxModel;
//
//import java.io.IOException;
//import java.net.MalformedURLException;
//import java.net.URL;
//import java.util.List;
//import java.util.Locale;
//
//import javax.servlet.ServletException;
//
//import org.apache.commons.lang.StringUtils;
//import org.kohsuke.stapler.DataBoundConstructor;
//import org.kohsuke.stapler.QueryParameter;
//import org.kohsuke.stapler.StaplerResponse;
//import org.openstack.client.common.OpenstackComputeClient;
//import org.openstack.model.compute.Zone;
//
///**
// * The original implementation of {@link OpenstackCloud}.
// *
// * @author Kohsuke Kawaguchi
// */
//public class AmazonEC2Cloud extends OpenstackCloud {
//    /**
//     * Represents the region. Can be null for backward compatibility reasons.
//     */
//    private String region;
//
//    // Used when running unit tests
//    public static boolean testMode;
//    
//    
//    @DataBoundConstructor
//    public AmazonEC2Cloud(String accessId, String secretKey, String region, String privateKey, String instanceCapStr, List<SlaveTemplate> templates) {
//        super("ec2-"+region, accessId, secretKey, privateKey, instanceCapStr, templates);
//        this.region = region;
//    }
//
//    public String getRegion() {
//        if (region == null)
//            region = DEFAULT_EC2_HOST; // Backward compatibility
//        // Handles pre 1.14 region names that used the old AwsRegion enum, note we don't change
//        // the region here to keep the meta-data compatible in the case of a downgrade (is that right?)
//        if (region.indexOf('_') > 0)
//        	return region.replace('_', '-').toLowerCase(Locale.ENGLISH);
//        return region;
//    }
//
//    public static URL getEc2EndpointUrl(String region) {
//        try {
//			return new URL("https://" + region + "." + EC2_URL_HOST + "/");
//		} catch (MalformedURLException e) {
//			throw new Error(e); // Impossible
//		}
//    }
//    
//    @Override
//    public URL getEc2EndpointUrl() {
//    	return getEc2EndpointUrl(getRegion());
//    }
//
//    @Override
//    public URL getS3EndpointUrl() {
//        try {
//			return new URL("https://"+getRegion()+".s3.amazonaws.com/");
//		} catch (MalformedURLException e) {
//			throw new Error(e); // Impossible
//		}
//    }
//
//    @Extension
//    public static class DescriptorImpl extends OpenstackCloud.DescriptorImpl {
//        @Override
//		public String getDisplayName() {
//            return "Amazon EC2";
//        }
//
//		public ListBoxModel doFillRegionItems(@QueryParameter String accessId,
//				@QueryParameter String secretKey) throws IOException,
//				ServletException {
//			ListBoxModel model = new ListBoxModel();
//			if (testMode) {
//				model.add(DEFAULT_EC2_HOST);
//				return model;
//			}
//				
//			if (!StringUtils.isEmpty(accessId) && !StringUtils.isEmpty(secretKey)) {
//				OpenstackComputeClient client = connect(accessId, secretKey, new URL(
//						"http://ec2.amazonaws.com")).getComputeClient();
//				for (Zone r : client.root().zones().list()) {
//					model.add(r.getName(), r.getName());
//				}
//			}
//			return model;
//		}
//
//        public FormValidation doTestConnection(
//                @QueryParameter String region,
//                 @QueryParameter String accessId,
//                 @QueryParameter String secretKey,
//                 @QueryParameter String privateKey) throws IOException, ServletException {
//            return super.doTestConnection(getEc2EndpointUrl(region),accessId,secretKey,privateKey);
//        }
//
//        public FormValidation doGenerateKey(
//                StaplerResponse rsp, @QueryParameter String region, @QueryParameter String accessId, @QueryParameter String secretKey) throws IOException, ServletException {
//            return super.doGenerateKey(rsp,getEc2EndpointUrl(region),accessId,secretKey);
//        }
//    }
//}
