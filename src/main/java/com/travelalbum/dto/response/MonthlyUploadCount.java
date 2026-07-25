package com.travelalbum.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MonthlyUploadCount {
    private String month;   // định dạng yyyy-MM
    private long count;
}
