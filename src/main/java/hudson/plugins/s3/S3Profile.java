package hudson.plugins.s3;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import hudson.FilePath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class S3Profile {
    private String name;
    private String accessKey;
    private String secretKey;
    private AmazonS3Client client;

    public static final Logger LOGGER = Logger.getLogger(S3Profile.class.getName());

    public S3Profile() {
    }

    @DataBoundConstructor
    public S3Profile(String name, String accessKey, String secretKey) {
        client = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey));
        this.name = name;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
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

    public void check() throws Exception {
        if (client == null) {
            client = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey));
        }
        client.listBuckets();
    }

    public void upload(String bucketName, FilePath filePath) throws IOException, InterruptedException {
        if (filePath.isDirectory()) {
            throw new IOException(filePath + " is a directory");
        }
        if (client == null) {
            client = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey));
        }
        String[] bucketNameArray = bucketName.split(File.separator, 2);
        try {
            client.putObject(bucketNameArray[0], bucketNameArray[1] + File.separator + filePath.getName(), filePath.read(), null);
        } catch (Exception e) {
            throw new IOException("put " + filePath.getName() + " to bucket " + bucketNameArray[0] + ": " + e);
        }

    }
}
