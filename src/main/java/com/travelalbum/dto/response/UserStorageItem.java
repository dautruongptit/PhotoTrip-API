package com.travelalbum.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserStorageItem {
    private Long userId;
    private String email;
    private long storageUsed;
    private long storageQuota;
}
