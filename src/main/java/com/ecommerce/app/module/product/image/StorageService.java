package com.ecommerce.app.module.product.image;
import java.io.InputStream;
public interface StorageService {

    String upload(
        String fileName,
        InputStream inputStream,
        long contentLength,
        String contentType
    );
    boolean delete(String objectKey);
    String getFileUrl(String objectKey);
    
} 