package com.skybooker.paymentservice.exception;

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
    void shouldMapBusinessExceptions() {
        assertEquals(HttpStatus.NOT_FOUND.value(), handler.handleNotFound(new ResourceNotFoundException("missing")).getStatusCode().value());
        assertEquals(HttpStatus.BAD_REQUEST.value(), handler.handleBadRequest(new BadRequestException("bad")).getStatusCode().value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMapValidationErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "bookingId", "required"));

        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class);
        MethodArgumentNotValidException exception =
            new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

        var response = handler.handleValidation(exception);
        Map<String, Object> body = response.getBody();
        Map<String, String> errors = (Map<String, String>) body.get("errors");

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
        assertEquals("Validation failed", body.get("message"));
        assertEquals("required", errors.get("bookingId"));
    }

    @SuppressWarnings("unused")
    private void dummy(String value) {
    }
}
