package com.skybooker.passengerservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldMapNotFoundAndBadRequest() {
        var notFound = handler.handleNotFound(new ResourceNotFoundException("Passenger missing"));
        var badRequest = handler.handleBadRequest(new BadRequestException("Invalid booking id"));

        assertEquals(HttpStatus.NOT_FOUND.value(), notFound.getStatusCode().value());
        assertEquals("Passenger missing", notFound.getBody().get("message"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), badRequest.getStatusCode().value());
        assertEquals("Invalid booking id", badRequest.getBody().get("message"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMapValidationErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "passportNumber", "must not be blank"));
        bindingResult.addError(new FieldError("request", "bookingId", "must not be empty"));

        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyValidatedMethod", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        var response = handler.handleValidation(exception);
        Map<String, Object> body = response.getBody();
        Map<String, String> errors = (Map<String, String>) body.get("errors");

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
        assertEquals("Validation failed", body.get("message"));
        assertEquals("must not be blank", errors.get("passportNumber"));
        assertEquals("must not be empty", errors.get("bookingId"));
    }

    @SuppressWarnings("unused")
    private void dummyValidatedMethod(String request) {
        // helper for MethodParameter construction
    }
}
