package jenkins.plugins.openstack;

import hudson.util.Secret;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.openstack.client.OpenstackException;
import org.openstack.client.common.OpenstackComputeClient;
import org.openstack.model.compute.KeyPair;

import com.google.common.collect.Lists;

/**
 * Stores an RSA public/private keypari
 *
 * @author Kohsuke Kawaguchi
 * @author Justin SB
 */
public final class OpenstackSshKey {
    private final String publicKey;
    private final Secret privateKey;

    OpenstackSshKey(String publicKey, String privateKey) {
        this.publicKey = publicKey;
        this.privateKey = Secret.fromString(privateKey.trim());
    }

    /**
     * Obtains the fingerprint of the key in the "ab:cd:ef:...:12" format.
     */
    public String getFingerprint() throws IOException {
        return OpenSshUtils.getFingerprint(privateKey.getPlainText());
    }

    /**
     * Is this file really a private key?
     */
    public boolean isPrivateKey() throws IOException {
        BufferedReader br = new BufferedReader(new StringReader(privateKey.getPlainText()));
        String line;
        while ((line = br.readLine()) != null) {
            if (line.equals("-----BEGIN RSA PRIVATE KEY-----"))
                return true;
        }
        return false;
    }

    /**
     * Finds the {@link KeyPairInfo} that corresponds to this key in OpenStack.
     */
    public org.openstack.model.compute.KeyPair find(OpenstackComputeClient compute) throws IOException, OpenstackException {
        List<KeyPair> keyPairs = Lists.newArrayList(compute.root().keyPairs().list());
        return find(keyPairs);
    }
    
    /**
     * Finds the {@link KeyPairInfo} that corresponds to this key in OpenStack.
     */
    org.openstack.model.compute.KeyPair find(List<org.openstack.model.compute.KeyPair> keyPairs) throws IOException, OpenstackException {
        String fp = getFingerprint();
        for(org.openstack.model.compute.KeyPair kp : keyPairs) {
            if(kp.getFingerprint().equalsIgnoreCase(fp)) {
                org.openstack.model.compute.KeyPair keyPair = new org.openstack.model.compute.KeyPair();
                keyPair.setName(kp.getName());
                keyPair.setFingerprint(fp);
                keyPair.setPrivateKey(Secret.toString(privateKey));
                return keyPair;
            }
        }
        return null;
    }

    @Override
    public int hashCode() {
        return privateKey.hashCode();
    }

    @Override
    public boolean equals(Object that) {
        return that instanceof OpenstackSshKey 
                && this.privateKey.equals(((OpenstackSshKey)that).privateKey)
                && this.publicKey.equals(((OpenstackSshKey)that).publicKey)
                ;
    }

    @Override
    public String toString() {
        return "OpenstackSshKey"; //privateKey.toString();
    }


    public String getPublicKey() {
        return publicKey;
    }

    public String getPrivateKey() {
        return privateKey.getPlainText();
    }

    public org.openstack.model.compute.KeyPair getOrCreate(OpenstackComputeClient compute) throws IOException, OpenstackException {
        List<KeyPair> existingKeys = Lists.newArrayList(compute.root().keyPairs().list());

        org.openstack.model.compute.KeyPair keyPair = find(existingKeys);
        if (keyPair == null) {
            int n = 0;
            while (true) {
                boolean foundName = false;
                for (KeyPair k : existingKeys) {
                    if (k.getName().equals("jenkins-" + n))
                        foundName = true;
                }
                if (!foundName)
                    break;
                n++;
            }

            keyPair = new KeyPair();
            keyPair.setName("jenkins-" + n);
            keyPair.setPublicKey(publicKey);
            keyPair.setPrivateKey(privateKey.getPlainText());
            
            KeyPair created = compute.root().keyPairs().create(keyPair);
            
            keyPair = created;
        }
        
        return keyPair;
    }
}
