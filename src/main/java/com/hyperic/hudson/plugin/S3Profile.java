package com.hyperic.hudson.plugin;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import hudson.FilePath;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.logging.Logger;

public class S3Profile {
    String name;
    String accessKey;
    String secretKey;
    private AmazonS3Client client;

    public static final Logger LOGGER =
        Logger.getLogger(S3Profile.class.getName());

    public S3Profile() {
    }

    public S3Profile(String name, String accessKey, String secretKey) {
        client = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey));
        this.name = name;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void check() throws Exception {
        client.listBuckets();
    }

    public void upload(String bucketName,
                       FilePath filePath,
                       Map<String, String> envVars,
                       PrintStream logger)
        throws IOException, InterruptedException {

        if (filePath.isDirectory()) {
            throw new IOException(filePath + " is a directory");
        }
        else {
            File file = new File(filePath.getName());
            try {
                client.putObject(bucketName, file.getName(), file);
            } catch (Exception e) {
                throw new IOException("put " + file + ": " + e, e);
            }
        }
    }

    protected void log(final PrintStream logger, final String message) {
        final String name =
            StringUtils.defaultString(S3BucketPublisher.DESCRIPTOR.getShortName());
        logger.println(name + message);
    }
}
