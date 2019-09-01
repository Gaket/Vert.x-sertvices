package com.vyng.vertex.error;

public class QueryLimitReachedException extends RuntimeException {

    public QueryLimitReachedException(String message) {
        super(message);
    }
}
