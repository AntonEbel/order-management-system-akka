package com.example.order.persistence;

public class InvalidStateChangeException extends RuntimeException{

    public InvalidStateChangeException(String message) {
        super(message);
    }
}
