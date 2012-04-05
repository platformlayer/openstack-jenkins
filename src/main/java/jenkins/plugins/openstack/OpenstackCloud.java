package jenkins.plugins.openstack;

import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Hudson.CloudList;
import hudson.model.Label;
import hudson.model.Node;
import jenkins.plugins.openstack.Messages;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import hudson.util.StreamTaskListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.openstack.client.CloudCapabilities;
import org.openstack.client.InstanceState;
import org.openstack.client.OpenstackAuthenticationException;
import org.openstack.client.OpenstackCredentials;
import org.openstack.client.OpenstackException;
import org.openstack.client.common.OpenstackComputeClient;
import org.openstack.client.common.OpenstackSession;
import org.openstack.model.compute.Flavor;
import org.openstack.model.compute.KeyPair;
import org.openstack.model.compute.Server;
import org.openstack.model.identity.Service;
import org.openstack.model.identity.ServiceEndpoint;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;



/**
 * Hudson's view of EC2. 
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class OpenstackCloud extends Cloud {

//	public static final String DEFAULT_EC2_HOST = "us-east-1";
//	public static final String EC2_URL_HOST = "ec2.amazonaws.com";
	
	// Used when running unit tests
	public static boolean testMode;
  
	private final String id;
	private final URL authUrl;
    private final String accessId;
    private final String tenant;
    private final Secret secretKey;
    private final String sshPublicKey;
    private final Secret sshPrivateKey;
    
//    private final OpenstackSshKey sshKey;

    /**
     * Upper bound on how many instances we may provision.
     */
    public final int instanceCap;
    private final List<SlaveTemplate> templates;
    private transient KeyPair usableKeyPair;

    private transient OpenstackSession session;
    
	private static OpenstackCredentials openstackCredentials;

	static Secret toSecret(String s) {
        if (s == null) return null;
        return Secret.fromString(s.trim());
    }
	
	static String safeTrim(String s) {
        if (s == null) return null;
        return s.trim();
    }
    
    protected OpenstackCloud(String id, URL authUrl, String accessId, String tenant, String secretKey, String sshPublicKey, String sshPrivateKey, String instanceCapStr, List<SlaveTemplate> templates) {
        super(id);
        this.id = safeTrim(id);
        this.authUrl = authUrl;
        this.accessId = safeTrim(accessId);
        this.tenant = safeTrim(tenant);
        this.secretKey = toSecret(secretKey);
        this.sshPrivateKey = toSecret(sshPrivateKey);
        this.sshPublicKey = safeTrim(sshPublicKey);
        if(instanceCapStr.equals(""))
            this.instanceCap = Integer.MAX_VALUE;
        else
            this.instanceCap = Integer.parseInt(instanceCapStr);
        if(templates==null)     templates=Collections.emptyList();
        this.templates = templates;
        readResolve(); // set parents
    }

    protected Object readResolve() {
        for (SlaveTemplate t : templates)
            t.parent = this;
        return this;
    }

    public String getAccessId() {
        return accessId;
    }

    public String getSecretKey() {
        return secretKey.getEncryptedValue();
    }

    public String getSshPublicKey() {
        return sshPublicKey;
    }

    public String getSshPrivateKey() {
        return sshPrivateKey.getEncryptedValue();
    }

    public String getInstanceCapStr() {
        if(instanceCap==Integer.MAX_VALUE)
            return "";
        else
            return String.valueOf(instanceCap);
    }

    public List<SlaveTemplate> getTemplates() {
        return Collections.unmodifiableList(templates);
    }

    public SlaveTemplate getTemplate(String imageId) {
        for (SlaveTemplate t : templates)
            if(t.imageId.equals(imageId))
                return t;
        return null;
    }

    /**
     * Gets {@link SlaveTemplate} that has the matching {@link Label}.
     */
    public SlaveTemplate getTemplate(Label label) {
        for (SlaveTemplate t : templates)
        	if(label == null || label.matches(t.getLabelSet()))
                return t;
        return null;
    }

//    /**
//     * Gets the {@link KeyPairInfo} used for the launch.
//     */
//    public synchronized KeyPair getKeyPair() throws OpenstackException, IOException {
//        if(usableKeyPair==null) {
//            OpenstackSshKey sshKey = new OpenstackSshKey(sshPublicKey, sshPrivateKey.getPlainText());
//            usableKeyPair = sshKey.find(connect().getComputeClient());
//        }
//        return usableKeyPair;
//    }

    /**
     * Counts the number of instances in EC2 currently running.
     *
     * <p>
     * This includes those instances that may be started outside Hudson.
     */
    public int countCurrentEC2Slaves() throws OpenstackException {
		int n = 0;
		for (Server server : connect().getComputeClient().root().servers()
				.list()) {
			InstanceState instanceState = InstanceState.get(server);

			if (instanceState.isActive() || instanceState.isStarting()) {
			    n++;
			}
//			switch (instanceState) {
//			case RUNNING:
//			case PENDING:
//				n++;
//				break;
//			default:
//				break;
//			}
		}
//        for (Reservation r : connect().describeInstances().getReservations()) {
//            for (Instance i : r.getInstances()) {
//                InstanceStateName stateName = InstanceStateName.fromValue(i.getState().getName());
//                if (stateName == InstanceStateName.Pending || stateName == InstanceStateName.Running)
//                    n++;
//            }
//        }
        return n;
    }

    /**
     * Debug command to attach to a running instance.
     */
    public void doAttach(StaplerRequest req, StaplerResponse rsp, @QueryParameter String id) throws ServletException, IOException, OpenstackException {
        checkPermission(PROVISION);
        SlaveTemplate t = getTemplates().get(0);

        StringWriter sw = new StringWriter();
        StreamTaskListener listener = new StreamTaskListener(sw);
        OpenstackSlave node = t.attach(id,listener);
        Hudson.getInstance().addNode(node);

        rsp.sendRedirect2(req.getContextPath()+"/computer/"+node.getNodeName());
    }

    public void doProvision(StaplerRequest req, StaplerResponse rsp, @QueryParameter String imageId) throws ServletException, IOException {
        checkPermission(PROVISION);
        if(imageId==null) {
            sendError("The 'imageId' query parameter is missing",req,rsp);
            return;
        }
        SlaveTemplate t = getTemplate(imageId);
        if(t==null) {
            sendError("No such image: "+imageId,req,rsp);
            return;
        }

        StringWriter sw = new StringWriter();
        StreamTaskListener listener = new StreamTaskListener(sw);
        try {
            OpenstackSlave node = t.provision(listener);
            Hudson.getInstance().addNode(node);

            rsp.sendRedirect2(req.getContextPath()+"/computer/"+node.getNodeName());
        } catch (OpenstackException e) {
            e.printStackTrace(listener.error(e.getMessage()));
            sendError(sw.toString(),req,rsp);
        }
    }

    @Override
	public Collection<PlannedNode> provision(Label label, int excessWorkload) {
        try {

            final SlaveTemplate t = getTemplate(label);

            List<PlannedNode> r = new ArrayList<PlannedNode>();
            for( ; excessWorkload>0; excessWorkload-- ) {
                if(countCurrentEC2Slaves()>=instanceCap) {
                    LOGGER.log(Level.INFO, "Instance cap reached, not provisioning.");
                    break;      // maxed out
                }

                r.add(new PlannedNode(t.getDisplayName(),
                        Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                            public Node call() throws Exception {
                                // TODO: record the output somewhere
                                OpenstackSlave s = t.provision(new StreamTaskListener(System.out));
                                Hudson.getInstance().addNode(s);
                                // EC2 instances may have a long init script. If we declare
                                // the provisioning complete by returning without the connect
                                // operation, NodeProvisioner may decide that it still wants
                                // one more instance, because it sees that (1) all the slaves
                                // are offline (because it's still being launched) and
                                // (2) there's no capacity provisioned yet.
                                //
                                // deferring the completion of provisioning until the launch
                                // goes successful prevents this problem.
                                s.toComputer().connect(false).get();
                                return s;
                            }
                        })
                        ,t.getNumExecutors()));
            }
            return r;
        } catch (OpenstackException e) {
            LOGGER.log(Level.WARNING,"Failed to count the # of live instances on Openstack",e);
            return Collections.emptyList();
        }
    }

    @Override
	public boolean canProvision(Label label) {
        return getTemplate(label)!=null;
    }

    /**
     * Gets the first {@link OpenstackCloud} instance configured in the current Hudson, or null if no such thing exists.
     */
	public static OpenstackCloud get(String cloudId) {
		for (Cloud cloud : Hudson.getInstance().clouds) {
			if (cloud instanceof OpenstackCloud) {
				OpenstackCloud openstackCloud = (OpenstackCloud) cloud;
				String cloudName = openstackCloud.getCloudId();
				if (cloudId.equals(cloudName)) {
					return openstackCloud;
				}
			}
		}
		return null;
	}

    /**
     * Connects to EC2 and returns {@link AmazonEC2}, which can then be used to communicate with EC2.
     */
    public synchronized OpenstackSession connect() throws OpenstackException {
        if (session == null) {
            session= connect(authUrl, accessId, tenant, secretKey);
        }
        return session;
    }

    /***
     * Connect to an EC2 instance.
     * @return {@link AmazonEC2} client
     */
    public static OpenstackSession connect(URL authUrl, String accessId, String tenant, String secretKey) {
        return connect(authUrl, accessId, tenant, Secret.fromString(secretKey));
    }

    /***
     * Connect to an EC2 instance.
     * @return {@link AmazonEC2} client
     */
    public static OpenstackSession connect(URL authUrl, String accessId, String tenant, Secret secretKey) {
    	openstackCredentials = new OpenstackCredentials(authUrl.toString(), accessId, Secret.toString(secretKey), tenant);
        OpenstackSession session = OpenstackSession.create();
        session.authenticate(openstackCredentials);
        return session;
    }

    /***
     * Convert a configured hostname like 'us-east-1' to a FQDN or ip address
     */
    public static String convertHostName(String ec2HostName) {
//        if (ec2HostName == null || ec2HostName.length()==0)
//            ec2HostName = DEFAULT_EC2_HOST;
//        if (!ec2HostName.contains("."))
//            ec2HostName = ec2HostName + "." + EC2_URL_HOST;
        return ec2HostName;
    }

    /***
     * Convert a user entered string into a port number
     * "" -> -1 to indicate default based on SSL setting
     */
    public static Integer convertPort(String ec2Port) {
        if (ec2Port == null || ec2Port.length() == 0)
            return -1;
        else
            return Integer.parseInt(ec2Port);
    }

    /**
     * Computes the presigned URL for the given S3 resource.
     *
     * @param path
     *      String like "/bucketName/folder/folder/abc.txt" that represents the resource to request.
     */
    public URL buildPresignedURL(String path) throws IOException, OpenstackException {
        throw new UnsupportedOperationException();
        
//    	long expires = System.currentTimeMillis()+60*60*1000;
//        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(path, Secret.toString(secretKey));
//        request.setExpiration(new Date(expires));
//        AmazonS3 s3 = new AmazonS3Client(awsCredentials);
//        return s3.generatePresignedUrl(request);
    }

    /* Parse a url or return a sensible error */
    public static URL checkEndPoint(String url) throws FormValidation {
        try {
            return new URL(url);
        } catch (MalformedURLException ex) {
            throw FormValidation.error("Endpoint URL is not a valid URL");
        }
    }


    public static abstract class DescriptorImpl extends Descriptor<Cloud> {
        public Iterable<Flavor> getInstanceTypes() {
        	// Is this used?
        	throw new UnsupportedOperationException();
//        	OpenstackSession ec2 = connect(accessId, secretKey, ec2endpoint);
//            return Lists.newArrayList(ec2.getComputeClient().root().flavors().list());
        }

        public FormValidation doCheckAccessId(@QueryParameter String value) throws IOException, ServletException {
            return FormValidation.ok();
            
//            return FormValidation.validateBase64(value,false,false,Messages.OpenstackCloud_InvalidAccessId());
        }

        public FormValidation doCheckSecretKey(@QueryParameter String value) throws IOException, ServletException {
            return FormValidation.ok();
//            return FormValidation.validateBase64(value,false,false,Messages.OpenstackCloud_InvalidSecretKey());
        }

        public ListBoxModel doFillRegionItems(
                @QueryParameter String authUrl,
                @QueryParameter String accessId,
                @QueryParameter String tenant,
                @QueryParameter String secretKey) throws IOException,
                ServletException {
            ListBoxModel model = new ListBoxModel();
                
            if (    !StringUtils.isEmpty(authUrl)
                    && !StringUtils.isEmpty(accessId)
                    && !StringUtils.isEmpty(secretKey)
                    ) {
                OpenstackSession session = connect(new URL(authUrl), accessId, tenant, secretKey);
                Set<String> regions = Sets.newHashSet();
                for (Service service: session.getAccess().getServiceCatalog()) {
                    for (ServiceEndpoint endpoint : service.getEndpoints()) {
                        String region = endpoint.getRegion();
                        if (!Strings.isNullOrEmpty(region)) {
                            regions.add(region);
                        }
                    }
                }
                for (String region : regions) {
                    model.add(region, region);
                }
            }
            return model;
        }

        public FormValidation doCheckPrivateKey(@QueryParameter String value) throws IOException, ServletException {
            boolean hasStart=false,hasEnd=false;
            BufferedReader br = new BufferedReader(new StringReader(value));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.equals("-----BEGIN RSA PRIVATE KEY-----"))
                    hasStart=true;
                if (line.equals("-----END RSA PRIVATE KEY-----"))
                    hasEnd=true;
            }
            if(!hasStart)
                return FormValidation.error("This doesn't look like a private key at all");
            if(!hasEnd)
                return FormValidation.error("The private key is missing the trailing 'END RSA PRIVATE KEY' marker. Copy&paste error?");
            return FormValidation.ok();
        }

                
        public FormValidation doTestConnection(
                @QueryParameter String authUrl,
                @QueryParameter String accessId,
                @QueryParameter String tenant,
                @QueryParameter String secretKey,
                @QueryParameter String sshPrivateKey) throws IOException, ServletException {
            try {
                if(StringUtils.isEmpty(authUrl))
                    return FormValidation.error("Authentication URL is not specified");
                if(StringUtils.isEmpty(accessId))
                    return FormValidation.error("Access ID is not specified");
                if(StringUtils.isEmpty(secretKey))
                    return FormValidation.error("Secret key is not specified");
//                if(StringUtils.isEmpty(privateKey))
//                    return FormValidation.error("Private key is not specified. Click 'Generate Key' to generate one.");

                OpenstackSession ec2 = connect(new URL(authUrl), accessId, tenant, secretKey);
                ec2.getComputeClient().root().flavors().list();

//                if(privateKey.trim().length()>0) {
//                    // check if this key exists
//                    OpenstackSshKey pk = new OpenstackSshKey(privateKey);
//                    if(pk.find(ec2.getComputeClient())==null)
//                        return FormValidation.error("The EC2 key pair private key isn't registered to this EC2 region (fingerprint is "+pk.getFingerprint()+")");
//                }
                
                return FormValidation.ok(Messages.OpenstackCloud_Success());
            } catch (OpenstackException e) {
                LOGGER.log(Level.WARNING, "Failed to check EC2 credential",e);
                return FormValidation.error(e.getMessage());
            }
        }

      public FormValidation doGenerateKey(
              StaplerResponse rsp,
              @QueryParameter String authUrl,
              @QueryParameter String accessId,
              @QueryParameter String tenant,
              @QueryParameter String secretKey,
              @QueryParameter String region) throws IOException, ServletException {
            try {
                String sshPublicKey;
                String sshPrivateKey;

                java.security.KeyPair rsaKeyPair = RsaUtils.generateRsaKeyPair();

                sshPublicKey = OpenSshUtils.serialize(rsaKeyPair.getPublic());
                sshPrivateKey = OpenSshUtils.serialize(rsaKeyPair.getPrivate());

                String script = "findPreviousFormItem(button,'sshPrivateKey').value='"
                        + sshPrivateKey.replace("\n", "\\n") + "'";
                rsp.addHeader("script", script);

                script = "findPreviousFormItem(button,'sshPublicKey').value='" + sshPublicKey.replace("\n", "\\n")
                        + "'";
                rsp.addHeader("script", script);

                return FormValidation.ok(Messages.OpenstackCloud_Success());
            } catch (OpenstackAuthenticationException e) {
                LOGGER.log(Level.WARNING, "OpenStack credentials were not valid",e);
                return FormValidation.error(e.getMessage());
	        }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(OpenstackCloud.class.getName());

    private static boolean isSSL(URL endpoint) {
        return endpoint.getProtocol().equals("https");
    }

    private static int portFromURL(URL endpoint) {
        int ec2Port = endpoint.getPort();
        if (ec2Port == -1) {
            ec2Port = endpoint.getDefaultPort();
        }
        return ec2Port;
    }

	public String getCloudId() {
		return getDisplayName();
	}

    public URL getAuthUrl() {
        return authUrl;
    }

    public String getTenant() {
        return tenant;
    }

    public String getId() {
        return id;
    }
    
    public OpenstackSshKey getSshKeyPair() {
        OpenstackSshKey sshKey = new OpenstackSshKey(sshPublicKey, sshPrivateKey.getPlainText());
        return sshKey;
    }
}
