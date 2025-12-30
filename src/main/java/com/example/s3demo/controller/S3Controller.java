package com.example.s3demo.controller;

import com.example.s3demo.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.ObjectVersion;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/s3")
public class S3Controller {

    private final S3Service s3Service;

    public S3Controller(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    // --- BUCKET ENDPOINTS ---

    @PostMapping("/buckets/{name}")
    public ResponseEntity<String> createBucket(@PathVariable String name) {
        s3Service.createBucket(name);
        return ResponseEntity.ok("Bucket created: " + name);
    }

    @GetMapping("/buckets")
    public ResponseEntity<List<String>> listBuckets() {
        return ResponseEntity.ok(s3Service.listBuckets());
    }

    @DeleteMapping("/buckets/{name}")
    public ResponseEntity<String> deleteBucket(@PathVariable String name) {
        s3Service.deleteBucket(name);
        return ResponseEntity.ok("Bucket deleted: " + name);
    }

    // --- OBJECT ENDPOINTS ---

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam String key, @RequestParam MultipartFile file)
            throws IOException {
        return ResponseEntity.ok(s3Service.uploadObject(key, file));
    }

    @GetMapping("/download/{key}")
    public ResponseEntity<byte[]> download(@PathVariable String key) {
        return ResponseEntity.ok(s3Service.downloadObject(key));
    }

    @GetMapping("/objects")
    public ResponseEntity<List<String>> listObjects() {
        return ResponseEntity.ok(s3Service.listObjects());
    }

    @DeleteMapping("/objects/{key}")
    public ResponseEntity<String> deleteObject(@PathVariable String key) {
        s3Service.deleteObject(key);
        return ResponseEntity.ok("Object deleted: " + key);
    }

    // --- ADVANCED CONCEPTS ---

    @GetMapping("/presigned-download/{key}")
    public ResponseEntity<String> getPresignedDownload(@PathVariable String key) {
        return ResponseEntity.ok(s3Service.generatePresignedDownloadUrl(key));
    }

    @GetMapping("/presigned-upload/{key}")
    public ResponseEntity<String> getPresignedUpload(@PathVariable String key) {
        return ResponseEntity.ok(s3Service.generatePresignedUploadUrl(key));
    }

    @PostMapping("/versioning/{bucketName}")
    public ResponseEntity<String> enableVersioning(@PathVariable String bucketName) {
        s3Service.enableVersioning(bucketName);
        return ResponseEntity.ok("Versioning enabled for " + bucketName);
    }

    @GetMapping("/versions/{key}")
    public ResponseEntity<List<ObjectVersion>> listVersions(@PathVariable String key) {
        return ResponseEntity.ok(s3Service.listObjectVersions(key));
    }

    @PostMapping("/multipart")
    public ResponseEntity<String> multipartUpload(@RequestParam String key, @RequestParam MultipartFile file)
            throws IOException {
        return ResponseEntity.ok(s3Service.multipartUpload(key, file));
    }

    @PostMapping("/lifecycle/{bucketName}")
    public ResponseEntity<String> setLifecycle(@PathVariable String bucketName) {
        s3Service.setLifecycleConfiguration(bucketName);
        return ResponseEntity.ok("Lifecycle configuration set for " + bucketName);
    }

    @PostMapping("/upload-encrypted")
    public ResponseEntity<String> uploadEncrypted(@RequestParam String key, @RequestParam MultipartFile file)
            throws IOException {
        s3Service.uploadWithEncryption(key, file);
        return ResponseEntity.ok("File uploaded with SSE-S3: " + key);
    }

    @PostMapping("/tag")
    public ResponseEntity<String> tagObject(@RequestParam String key, @RequestParam String tagKey,
            @RequestParam String tagValue) {
        s3Service.tagObject(key, tagKey, tagValue);
        return ResponseEntity.ok("Tag added to " + key);
    }
}
