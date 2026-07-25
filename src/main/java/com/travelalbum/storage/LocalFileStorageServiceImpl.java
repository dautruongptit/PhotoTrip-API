package com.travelalbum.storage;

import com.travelalbum.exception.StorageException;
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

/**
 * Lưu ảnh trực tiếp trên Ubuntu Server, phân theo user — xem SEC-01/SEC-11.
 * Cấu trúc: {root}/users/user_{id}/events/{eventFolder}/{uuid}_{originalName}
 */
@Service
public class LocalFileStorageServiceImpl implements StorageService {

    @Value("${storage.root-path}")
    private String rootPath;

    private Path userRoot(Long userId) {
        return Paths.get(rootPath, "users", "user_" + userId).normalize();
    }

    private Path eventDir(Long userId, String eventFolder) {
        Path base = userRoot(userId);
        Path target = base.resolve("events").resolve(eventFolder).normalize();
        // Chống Path Traversal: target PHẢI nằm trong đúng thư mục gốc của user đó
        if (!target.startsWith(base)) {
            throw new SecurityException("Invalid storage path");
        }
        return target;
    }

    @Override
    public void createUserRootFolder(Long userId) {
        try {
            Files.createDirectories(userRoot(userId));
        } catch (IOException ex) {
            throw new StorageException("Failed to create user root folder", ex);
        }
    }

    @Override
    public void createEventFolder(Long userId, String eventFolder) {
        try {
            Files.createDirectories(eventDir(userId, eventFolder));
        } catch (IOException ex) {
            throw new StorageException("Failed to create event folder", ex);
        }
    }

    @Override
    public StoredFile store(Long userId, String eventFolder, MultipartFile file) {
        Path dir = eventDir(userId, eventFolder);
        try {
            Files.createDirectories(dir);
            String storedName = UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());
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

            String relativePath = Paths.get(rootPath).relativize(target).toString();
            return new StoredFile(storedName, relativePath, checksum, width, height);
        } catch (IOException ex) {
            throw new StorageException("Failed to store file", ex);
        }
    }

    @Override
    public Resource load(Long userId, String relativePath) {
        Path base = userRoot(userId);
        Path target = Paths.get(rootPath).resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new SecurityException("Invalid storage path");
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(Long userId, String relativePath) {
        Path base = userRoot(userId);
        Path target = Paths.get(rootPath).resolve(relativePath).normalize();
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
    public void deleteEventFolder(Long userId, String eventFolder) {
        Path dir = eventDir(userId, eventFolder);
        try {
            FileSystemUtils.deleteRecursively(dir);
        } catch (IOException ex) {
            throw new StorageException("Failed to delete event folder", ex);
        }
    }

    @Override
    public void deleteUserRootFolder(Long userId) {
        Path root = userRoot(userId);
        try {
            FileSystemUtils.deleteRecursively(root);
        } catch (IOException ex) {
            throw new StorageException("Failed to delete user folder", ex);
        }
    }

    @Override
    public long calculateUsedSpace(Long userId) {
        Path root = userRoot(userId);
        if (!Files.exists(root)) {
            return 0L;
        }
        try (var stream = Files.walk(root)) {
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

    private String sanitize(String name) {
        return name == null ? "file" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    @Override
    public long getDiskTotalSpace() {
        try {
            Path root = Paths.get(rootPath);
            Files.createDirectories(root);
            return Files.getFileStore(root).getTotalSpace();
        } catch (IOException ex) {
            throw new StorageException("Failed to read disk total space", ex);
        }
    }

    @Override
    public long getDiskUsableSpace() {
        try {
            Path root = Paths.get(rootPath);
            Files.createDirectories(root);
            return Files.getFileStore(root).getUsableSpace();
        } catch (IOException ex) {
            throw new StorageException("Failed to read disk usable space", ex);
        }
    }
}
