package com.chanakya.shl.exception;

public class ShlInactiveException extends RuntimeException {

    public ShlInactiveException(String identifier) {
        super("SHL is inactive: " + identifier);
    }
}
