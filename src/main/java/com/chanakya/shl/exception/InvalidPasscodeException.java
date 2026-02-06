package com.chanakya.shl.exception;

import lombok.Getter;

@Getter
public class InvalidPasscodeException extends RuntimeException {

    private final int remainingAttempts;

    public InvalidPasscodeException(int remainingAttempts) {
        super("Invalid passcode. Remaining attempts: " + remainingAttempts);
        this.remainingAttempts = remainingAttempts;
    }
}
