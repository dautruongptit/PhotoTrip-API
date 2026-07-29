package com.travelalbum.service;

import com.travelalbum.storage.StoredFile;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    void createUserRootFolder(String parentFolder);

    void createEventFolder(String parentFolder, String eventFolder);

    StoredFile store(String parentFolder, String eventFolder, MultipartFile file);

    Resource load(String parentFolder, String relativePath);

    void delete(String parentFolder, String relativePath);

    void deleteEventFolder(String parentFolder, String eventFolder);

    void deleteUserRootFolder(String parentFolder);

    long calculateUsedSpace(String parentFolder);

    long getDiskTotalSpace();

    long getDiskUsableSpace();
}