package com.chanakya.shl.exception;

public class ShlNotFoundException extends RuntimeException {

    public ShlNotFoundException(String identifier) {
        super("SHL not found: " + identifier);
    }
}
