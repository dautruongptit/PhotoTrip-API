package com.travelalbum.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class BatchDeleteResponse {
    private List<Long> deletedIds;
    private List<BatchDeleteFailure> failures;
}
