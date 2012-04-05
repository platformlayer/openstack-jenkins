package jenkins.plugins.openstack.storage;

import hudson.FilePath;

import java.io.IOException;

import org.openstack.client.OpenstackException;
import org.openstack.client.OpenstackNotFoundException;
import org.openstack.client.storage.OpenstackStorageClient;
import org.openstack.model.storage.ContainerProperties;

public class OpenstackStorage {
    public static void check(OpenstackStorageClient storageClient) {
        storageClient.root().show();
    }

    public static ContainerProperties ensureBucket(OpenstackStorageClient storageClient, String bucketName) throws IOException {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                ContainerProperties properties = storageClient.root().containers().id(bucketName).get();
                return properties;
            } catch (OpenstackNotFoundException e) {
                if (attempt != 1) {
                    throw new IOException("Could not find container", e);
                }
            }

            try {
                storageClient.root().containers().create(bucketName);
            } catch (OpenstackException e) {
                throw new IOException("Could not create container", e);
            }
        }
    }

    public static void upload(OpenstackStorageClient storageClient, String bucketName, FilePath filePath) throws IOException, InterruptedException {
        if (filePath.isDirectory()) {
            throw new IOException(filePath + " is a directory");
        }

        // TODO: Don't we usually want to keep the source paths??
        final Destination dest = new Destination(bucketName, filePath.getName());

        try {
            storageClient.putObject(dest.bucketName, dest.objectName, filePath.read(), filePath.length());
        } catch (Exception e) {
            throw new IOException("put " + dest, e);
        }
    }
}
