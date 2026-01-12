package com.tevore.error;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;


@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ExceptionMessage> handleHttpClientErrorException(HttpClientErrorException ex) {
        List<String> errorList = new ArrayList<>();
        errorList.add("User not found");
        return new ResponseEntity<>(new ExceptionMessage(errorList), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ExceptionMessage> handleHttpServerErrorException(HttpServerErrorException ex) {
        // Log the exception details internally
        List<String> errorList = new ArrayList<>();
        errorList.add("Service error detected");
        return new ResponseEntity<>(new ExceptionMessage(errorList), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ExceptionMessage> handleNoResourceFoundException(NoResourceFoundException ex) {
        List<String> errorList = new ArrayList<>();
        errorList.add("Username required or path is incorrect");
        return new ResponseEntity<>(new ExceptionMessage(errorList), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ExceptionMessage> handleConstraintViolationException(ConstraintViolationException ex) {
        // Collect all violation messages for a clean error response
        StringBuilder errorMessage = new StringBuilder();
        List<String> errorList = new ArrayList<>();
        ex.getConstraintViolations().forEach(violation -> errorList.add(violation.getMessage()));
        return new ResponseEntity<>(new ExceptionMessage(errorList), HttpStatus.BAD_REQUEST);
    }

    // Fallback handler for all other exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionMessage> handleGenericException(Exception ex) {
        // Log the exception details internally
        List<String> errorList = new ArrayList<>();
        errorList.add("An unexpected error occurred");
        return new ResponseEntity<>(new ExceptionMessage(errorList), HttpStatus.INTERNAL_SERVER_ERROR); // Returns 500
    }
}
