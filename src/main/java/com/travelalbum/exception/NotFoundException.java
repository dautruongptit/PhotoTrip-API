package com.travelalbum.exception;

import lombok.Getter;

@Getter
public class NotFoundException extends RuntimeException {

    private final String errorCode;

    public NotFoundException(String message) {
        super(message);
        this.errorCode = "NOT_FOUND";
    }
}
