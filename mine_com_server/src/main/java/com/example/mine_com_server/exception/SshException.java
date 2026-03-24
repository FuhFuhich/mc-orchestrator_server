package com.example.mine_com_server.exception;

public class SshException extends RuntimeException {

    public SshException(String message) {
        super(message);
    }

    public SshException(String message, Throwable cause) {
        super(message, cause);
    }
}