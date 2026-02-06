package com.chanakya.shl.exception;

public class ShlExpiredException extends RuntimeException {

    public ShlExpiredException(String identifier) {
        super("SHL has expired: " + identifier);
    }
}
