package hudson.plugins.openstack;

import hudson.FilePath;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import org.kohsuke.stapler.DataBoundConstructor;
import org.openstack.client.OpenstackCredentials;
import org.openstack.client.OpenstackException;
import org.openstack.client.OpenstackNotFoundException;
import org.openstack.client.common.OpenstackSession;
import org.openstack.client.storage.OpenstackStorageClient;
import org.openstack.model.storage.ContainerProperties;

public class OpenstackProfile {
	private String name;
    private String authUrl;
    private String tenant;
    private String accessKey;
    private String secretKey;
    private AtomicReference<OpenstackSession> session = new AtomicReference<OpenstackSession>(null);

    public OpenstackProfile() {
    }

    @DataBoundConstructor
    public OpenstackProfile(String name, String authUrl, String tenant, String accessKey, String secretKey) {
        this.name = name;
        this.authUrl = authUrl;
        this.tenant = tenant;
        this.accessKey = accessKey;
        this.secretKey = secretKey;

        OpenstackSession session = OpenstackSession.create();
        OpenstackCredentials credentials = buildCredentials();
        session.authenticate(credentials);
        this.session.set(session);
    }

    private OpenstackCredentials buildCredentials() {
        return new OpenstackCredentials(authUrl, accessKey, secretKey, tenant);
    }

    public final String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public final String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public final String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAuthUrl() {
        return authUrl;
    }

    public void setAuthUrl(String authUrl) {
        this.authUrl = authUrl;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public OpenstackSession getSession() {
        if (session.get() == null) {
            OpenstackSession session = OpenstackSession.create();
            OpenstackCredentials credentials = buildCredentials();
            session.authenticate(credentials);
            this.session.set(session);
        }
        return session.get();
    }

    public OpenstackStorageClient getStorageClient() {
        OpenstackSession session = getSession();
        return session.getStorageClient();
    }

    public void check() throws Exception {
        getStorageClient().root().show();
    }

    public ContainerProperties ensureBucket(String bucketName) throws IOException {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                ContainerProperties properties = getStorageClient().root().containers().id(bucketName).get();
                return properties;
            } catch (OpenstackNotFoundException e) {
                if (attempt != 1) {
                    throw new IOException("Could not find container", e);
                }
            }

            try {
                getStorageClient().root().containers().create(bucketName);
            } catch (OpenstackException e) {
                throw new IOException("Could not create container", e);
            }
        }
    }

    public void upload(String bucketName, FilePath filePath) throws IOException, InterruptedException {
        if (filePath.isDirectory()) {
            throw new IOException(filePath + " is a directory");
        }

        // TODO: Don't we usually want to keep the source paths??
        final Destination dest = new Destination(bucketName, filePath.getName());

        try {
            getStorageClient().putObject(dest.bucketName, dest.objectName, filePath.read(), filePath.length());
        } catch (Exception e) {
            throw new IOException("put " + dest, e);
        }
    }

}
