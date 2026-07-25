package com.travelalbum.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UploadFailure {
    private String fileName;
    private String errorCode;
}
