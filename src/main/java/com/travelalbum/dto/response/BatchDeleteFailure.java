package com.travelalbum.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BatchDeleteFailure {
    private Long id;
    private String errorCode;
}
