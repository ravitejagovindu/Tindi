package com.tindi.api.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

public record ErrorBody(Error error) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Error(String code, String message, Map<String, String> fields) {}

    public static ErrorBody of(String code, String message, Map<String, String> fields) {
        return new ErrorBody(new Error(code, message, fields));
    }
}
