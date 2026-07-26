package com.travelalbum.controller;

import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.response.StorageDebugResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/auth/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.dev-login-enabled", havingValue = "true")
public class DevStorageController {

    @Value("${storage.root-path}")
    private String configuredRootPath;

    @Value("${app.dev-login-secret:}")
    private String devLoginSecret;

    @GetMapping("/storage-info")
    public ApiResponse<StorageDebugResponse> storageInfo(
            @RequestHeader(value = "X-Dev-Secret", required = false) String providedSecret) {
        if (devLoginSecret == null || devLoginSecret.isBlank() || !devLoginSecret.equals(providedSecret)) {
            throw new AccessDeniedException("Invalid or missing X-Dev-Secret header");
        }

        Path root = Paths.get(configuredRootPath).toAbsolutePath().normalize();
        boolean exists = Files.exists(root);
        boolean writable = exists && Files.isWritable(root);

        List<String> entries = List.of();
        if (exists) {
            File[] files = root.toFile().listFiles();
            if (files != null) {
                entries = java.util.Arrays.stream(files).map(File::getName).toList();
            }
        }

        StorageDebugResponse debug = StorageDebugResponse.builder()
                .configuredRootPath(configuredRootPath)
                .resolvedAbsolutePath(root.toString())
                .rootExists(exists)
                .rootWritable(writable)
                .topLevelEntries(entries)
                .build();

        return ApiResponse.success("OK", debug);
    }
}