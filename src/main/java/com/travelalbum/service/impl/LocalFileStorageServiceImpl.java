package com.travelalbum.service.impl;
import com.travelalbum.exception.StorageException;
import com.travelalbum.service.StorageService;
import com.travelalbum.storage.StoredFile;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalFileStorageServiceImpl implements StorageService {

    @Value("${storage.root-path}")
    private String rootPath;

    private Path root() {
        return Paths.get(rootPath).toAbsolutePath().normalize();
    }

    private Path parentDir(String parentFolder) {
        Path base = root();
        Path target = base.resolve(sanitizeSegment(parentFolder)).normalize();
        if (!target.startsWith(base)) {
            throw new SecurityException("Invalid storage path");
        }
        return target;
    }

    private Path eventDir(String parentFolder, String eventFolder) {
        Path base = root();
        Path target = parentDir(parentFolder).resolve(sanitizeSegment(eventFolder)).normalize();
        if (!target.startsWith(base)) {
            throw new SecurityException("Invalid storage path");
        }
        return target;
    }

    private String sanitizeSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            throw new SecurityException("Invalid folder segment");
        }
        String cleaned = segment.replace("..", "").replaceAll("[\\\\/]", "_");
        if (cleaned.isBlank()) {
            throw new SecurityException("Invalid folder segment");
        }
        return cleaned;
    }

    @Override
    public void createUserRootFolder(String parentFolder) {
        try {
            Files.createDirectories(parentDir(parentFolder));
        } catch (IOException ex) {
            throw new StorageException("Failed to create user root folder", ex);
        }
    }

    @Override
    public void createEventFolder(String parentFolder, String eventFolder) {
        try {
            Files.createDirectories(eventDir(parentFolder, eventFolder));
        } catch (IOException ex) {
            throw new StorageException("Failed to create event folder", ex);
        }
    }

    @Override
    public StoredFile store(String parentFolder, String eventFolder, MultipartFile file) {
        Path dir = eventDir(parentFolder, eventFolder);
        try {
            Files.createDirectories(dir);
            String storedName = UUID.randomUUID() + "_" + sanitizeFileName(file.getOriginalFilename());
            Path target = dir.resolve(storedName);

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            String checksum;
            try (InputStream in = Files.newInputStream(target)) {
                checksum = DigestUtils.sha256Hex(in);
            }

            Integer width = null;
            Integer height = null;
            BufferedImage img = ImageIO.read(target.toFile());
            if (img != null) {
                width = img.getWidth();
                height = img.getHeight();
            }

            String relativePath = root().relativize(target).toString();
            return new StoredFile(storedName, relativePath, checksum, width, height);
        } catch (IOException ex) {
            throw new StorageException("Failed to store file", ex);
        }
    }

    @Override
    public Resource load(String parentFolder, String relativePath) {
        Path base = parentDir(parentFolder);
        Path target = root().resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new SecurityException("Invalid storage path");
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String parentFolder, String relativePath) {
        Path base = parentDir(parentFolder);
        Path target = root().resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new SecurityException("Invalid storage path");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new StorageException("Failed to delete file", ex);
        }
    }

    @Override
    public void deleteEventFolder(String parentFolder, String eventFolder) {
        Path dir = eventDir(parentFolder, eventFolder);
        try {
            FileSystemUtils.deleteRecursively(dir);
        } catch (IOException ex) {
            throw new StorageException("Failed to delete event folder", ex);
        }
    }

    @Override
    public void deleteUserRootFolder(String parentFolder) {
        Path dir = parentDir(parentFolder);
        try {
            FileSystemUtils.deleteRecursively(dir);
        } catch (IOException ex) {
            throw new StorageException("Failed to delete user folder", ex);
        }
    }

    @Override
    public long calculateUsedSpace(String parentFolder) {
        Path dir = parentDir(parentFolder);
        if (!Files.exists(dir)) {
            return 0L;
        }
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException ex) {
            throw new StorageException("Failed to calculate used space", ex);
        }
    }

    @Override
    public long getDiskTotalSpace() {
        try {
            Files.createDirectories(root());
            return Files.getFileStore(root()).getTotalSpace();
        } catch (IOException ex) {
            throw new StorageException("Failed to read disk total space", ex);
        }
    }

    @Override
    public long getDiskUsableSpace() {
        try {
            Files.createDirectories(root());
            return Files.getFileStore(root()).getUsableSpace();
        } catch (IOException ex) {
            throw new StorageException("Failed to read disk usable space", ex);
        }
    }

    private String sanitizeFileName(String name) {
        return name == null ? "file" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}