package com.hyperic.hudson.plugin;

import hudson.FilePath;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.jets3t.service.S3Service;
import org.jets3t.service.S3ServiceException;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.model.S3Bucket;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.security.AWSCredentials;

public class S3Profile {
    String name;
    String accessKey;
    String secretKey;
    private S3Service s3;

    public static final Logger LOGGER =
        Logger.getLogger(S3Profile.class.getName());

    public S3Profile() {

    }

    public S3Profile(String name, String accessKey, String secretKey) {
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

    public void login() throws S3ServiceException {
        if (this.s3 != null) {
            return;
        }
        try {
            AWSCredentials creds =
                new AWSCredentials(this.accessKey, this.secretKey);
            this.s3 = new RestS3Service(creds);
        } catch (S3ServiceException e) {
            LOGGER.log(Level.SEVERE, e.getMessage());
            throw e;
        }
    }

    public void check() throws S3ServiceException {
        this.s3.listAllBuckets();
    }

    public void logout() {
        this.s3 = null;
    }

    private S3Bucket getOrCreateBucket(String bucketName) throws S3ServiceException {
        S3Bucket bucket = this.s3.getBucket(bucketName);
        if (bucket == null) {
            bucket = this.s3.createBucket(new S3Bucket(bucketName));
        }
        return bucket;
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
            S3Bucket bucket;
            try {
                bucket = getOrCreateBucket(bucketName);
            } catch (S3ServiceException e) {
                throw new IOException(bucketName + " bucket: " + e);
            }

            try {
                S3Object fileObject =
                    new S3Object(bucket, file.getName());
                fileObject.setDataInputStream(filePath.read());
                this.s3.putObject(bucket, fileObject);
            } catch (Exception e) {
                throw new IOException("put " + file + ": " + e);
            }
        }
    }

    protected void log(final PrintStream logger, final String message) {
        final String name =
            StringUtils.defaultString(S3BucketPublisher.DESCRIPTOR.getShortName());
        logger.println(name + message);
    }
}
