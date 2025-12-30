package com.example.s3demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class S3Service {

        private final S3Client s3Client;
        private final S3Presigner s3Presigner;

        public S3Service(S3Client s3Client, S3Presigner s3Presigner) {
                this.s3Client = s3Client;
                this.s3Presigner = s3Presigner;
        }

        @Value("${aws.s3.bucket}")
        private String defaultBucketName;

        // --- 1. BUCKET OPERATIONS ---

        public void createBucket(String bucketName) {
                CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                                .bucket(bucketName)
                                .build();
                s3Client.createBucket(createBucketRequest);
        }

        public List<String> listBuckets() {
                return s3Client.listBuckets().buckets().stream()
                                .map(Bucket::name)
                                .collect(Collectors.toList());
        }

        public void deleteBucket(String bucketName) {
                s3Client.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());
        }

        // --- 2. OBJECT OPERATIONS (Basic CRUD) ---

        public String uploadObject(String key, MultipartFile file) throws IOException {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                                .bucket(defaultBucketName)
                                .key(key)
                                .contentType(file.getContentType())
                                .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
                return "File uploaded successfully: " + key;
        }

        public byte[] downloadObject(String key) {
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                                .bucket(defaultBucketName)
                                .key(key)
                                .build();
                ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
                return objectBytes.asByteArray();
        }

        public List<String> listObjects() {
                ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
                                .bucket(defaultBucketName)
                                .build();
                return s3Client.listObjectsV2(listObjectsV2Request).contents().stream()
                                .map(S3Object::key)
                                .collect(Collectors.toList());
        }

        public void deleteObject(String key) {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                                .bucket(defaultBucketName)
                                .key(key)
                                .build());
        }

        // --- 3. VERSIONING ---

        public void enableVersioning(String bucketName) {
                PutBucketVersioningRequest request = PutBucketVersioningRequest.builder()
                                .bucket(bucketName)
                                .versioningConfiguration(VersioningConfiguration.builder()
                                                .status(BucketVersioningStatus.ENABLED)
                                                .build())
                                .build();
                s3Client.putBucketVersioning(request);
        }

        public List<ObjectVersion> listObjectVersions(String key) {
                ListObjectVersionsRequest request = ListObjectVersionsRequest.builder()
                                .bucket(defaultBucketName)
                                .prefix(key)
                                .build();
                return s3Client.listObjectVersions(request).versions();
        }

        // --- 4. PRESIGNED URLS ---

        public String generatePresignedDownloadUrl(String key) {
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                                .bucket(defaultBucketName)
                                .key(key)
                                .build();

                GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofMinutes(10))
                                .getObjectRequest(getObjectRequest)
                                .build();

                PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
                return presignedRequest.url().toString();
        }

        public String generatePresignedUploadUrl(String key) {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                                .bucket(defaultBucketName)
                                .key(key)
                                .build();

                PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofMinutes(10))
                                .putObjectRequest(putObjectRequest)
                                .build();

                return s3Presigner.presignPutObject(presignRequest).url().toString();
        }

        // --- 5. MULTIPART UPLOAD (For Large Files) ---

        public String multipartUpload(String key, MultipartFile file) throws IOException {
                // 1. Initiate
                CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                                .bucket(defaultBucketName)
                                .key(key)
                                .build();
                CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
                String uploadId = createResponse.uploadId();

                List<CompletedPart> completedParts = new ArrayList<>();
                byte[] bytes = file.getBytes();
                int partSize = 5 * 1024 * 1024; // 5MB part size

                try {
                        for (int i = 0; i * partSize < bytes.length; i++) {
                                int start = i * partSize;
                                int end = Math.min(start + partSize, bytes.length);
                                byte[] partBuffer = new byte[end - start];
                                System.arraycopy(bytes, start, partBuffer, 0, end - start);

                                UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                                                .bucket(defaultBucketName)
                                                .key(key)
                                                .uploadId(uploadId)
                                                .partNumber(i + 1)
                                                .build();

                                String etag = s3Client.uploadPart(uploadPartRequest, RequestBody.fromBytes(partBuffer))
                                                .eTag();
                                completedParts.add(CompletedPart.builder().partNumber(i + 1).eTag(etag).build());
                        }

                        // 2. Complete
                        CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                                        .parts(completedParts)
                                        .build();
                        s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                                        .bucket(defaultBucketName)
                                        .key(key)
                                        .uploadId(uploadId)
                                        .multipartUpload(completedMultipartUpload)
                                        .build());
                } catch (Exception e) {
                        s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                                        .bucket(defaultBucketName)
                                        .key(key)
                                        .uploadId(uploadId)
                                        .build());
                        throw e;
                }
                return "Multipart upload complete";
        }

        // --- 6. LIFECYCLE CONFIGURATION ---

        public void setLifecycleConfiguration(String bucketName) {
                LifecycleRule rule = LifecycleRule.builder()
                                .id("MoveToGlacierAfter30Days")
                                .status(ExpirationStatus.ENABLED)
                                .transitions(Transition.builder()
                                                .days(30)
                                                .storageClass(TransitionStorageClass.GLACIER)
                                                .build())
                                .filter(LifecycleRuleFilter.builder().prefix("temp/").build())
                                .build();

                BucketLifecycleConfiguration config = BucketLifecycleConfiguration.builder()
                                .rules(rule)
                                .build();

                s3Client.putBucketLifecycleConfiguration(PutBucketLifecycleConfigurationRequest.builder()
                                .bucket(bucketName)
                                .lifecycleConfiguration(config)
                                .build());
        }

        // --- 7. SERVER-SIDE ENCRYPTION (SSE-S3) ---

        public void uploadWithEncryption(String key, MultipartFile file) throws IOException {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                                .bucket(defaultBucketName)
                                .key(key)
                                .serverSideEncryption(ServerSideEncryption.AES256)
                                .build();
                s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
        }

        // --- 8. OBJECT TAGGING ---

        public void tagObject(String key, String tagKey, String tagValue) {
                Tag tag = Tag.builder().key(tagKey).value(tagValue).build();
                s3Client.putObjectTagging(PutObjectTaggingRequest.builder()
                                .bucket(defaultBucketName)
                                .key(key)
                                .tagging(Tagging.builder().tagSet(tag).build())
                                .build());
        }
}
