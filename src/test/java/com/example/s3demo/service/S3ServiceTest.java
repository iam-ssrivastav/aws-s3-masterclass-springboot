package com.example.s3demo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private MultipartFile multipartFile;

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Service = new S3Service(s3Client, s3Presigner);
        ReflectionTestUtils.setField(s3Service, "defaultBucketName", "test-bucket");
    }

    @Test
    void testListBuckets() {
        // Arrange
        Bucket bucket = Bucket.builder().name("test-bucket").build();
        ListBucketsResponse response = ListBucketsResponse.builder()
                .buckets(Collections.singletonList(bucket))
                .build();
        when(s3Client.listBuckets()).thenReturn(response);

        // Act
        List<String> bucketNames = s3Service.listBuckets();

        // Assert
        assertEquals(1, bucketNames.size());
        assertEquals("test-bucket", bucketNames.get(0));
    }

    @Test
    void testUploadObject() throws IOException {
        // Arrange
        String key = "test-key";
        byte[] content = "test-content".getBytes();
        when(multipartFile.getBytes()).thenReturn(content);
        when(multipartFile.getContentType()).thenReturn("text/plain");

        // Act
        s3Service.uploadObject(key, multipartFile);

        // Assert
        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertEquals(key, requestCaptor.getValue().key());
    }
}
