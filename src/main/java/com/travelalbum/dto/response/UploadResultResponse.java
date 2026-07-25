package com.travelalbum.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class UploadResultResponse {
    private int uploaded;
    private int failed;
    private List<PhotoResponse> photos;
    private List<UploadFailure> failures;
}
