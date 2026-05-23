package com.ecommerce.app.module.product.image;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.util.UUID;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;

public class MinIOStorageService implements StorageService{
    private static final Logger LOG = LoggerFactory.getLogger(MinIOStorageService.class);

    private final MinioClient minioClient ;
    private final String bucketName;
    private final String endpoint;

    public MinIOStorageService(String endpoint,String accessKey,String secretKey ,String bucketName){
        this.bucketName = bucketName;
        this.endpoint = endpoint;
        this.minioClient =  MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }


        @Override
    public String upload(String fileName,InputStream inputStream,long contentLength, String contentType ) {
        LOG.info("minio upload starterd");
        try {   

            String objectKey = UUID.randomUUID() + "-" + fileName;
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(inputStream, contentLength, -1).contentType(contentType) .build()
            );

            return objectKey;

        } catch (Exception e) {
            LOG.error("exception", e);
            throw new RuntimeException( "failed to upload file" );
                    
        }
    }
    @Override
    public boolean delete(String objectKey) {

        try {

            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucketName).object(objectKey)  .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    @Override
    public String getFileUrl(String objectKey) {
        return endpoint + "/" + bucketName + "/" +  objectKey;
    }
}
