package hudson.plugins.s3;


/**
 * Provides a way to construct a destination bucket name and object name based
 * on the bucket name provided by the user.
 * 
 * The convention implemented here is that a / in a bucket name is used to
 * construct a structure in the object name.  That is, a put of file.txt to bucket name
 * of "mybucket/v1" will cause the object "v1/file.txt" to be created in the mybucket.
 * 
 */
public class Destination {
  public final String bucketName; 
  public final String objectName; 
  
  public Destination(final String userBucketName, final String fileName) {
    
    if (userBucketName == null || fileName == null) 
      throw new IllegalArgumentException("Not defined for null parameters: "+userBucketName+","+fileName);
    
    final String[] bucketNameArray = userBucketName.split("/", 2);
    
    bucketName = bucketNameArray[0];
    
    if (bucketNameArray.length > 1) {
        objectName = bucketNameArray[1] + "/" + fileName;
    } else {
        objectName = fileName;
    }
  }

@Override
 public String toString() {
   return "Destination [bucketName="+bucketName+", objectName="+objectName+"]";
 }
  
  
}
