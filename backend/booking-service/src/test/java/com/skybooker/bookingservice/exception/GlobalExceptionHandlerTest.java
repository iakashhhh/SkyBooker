package com.skybooker.bookingservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
        assertEquals(HttpStatus.BAD_REQUEST.value(),
            handler.handleBadRequest(new BadRequestException("bad")).getStatusCode().value());
        assertEquals(HttpStatus.NOT_FOUND.value(),
            handler.handleNotFound(new ResourceNotFoundException("missing")).getStatusCode().value());
        assertEquals(HttpStatus.CONFLICT.value(),
            handler.handleConflict(new ConflictException("conflict")).getStatusCode().value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMapValidationAndGenericErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "bookingId", "must not be blank"));

        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class);
        MethodArgumentNotValidException validationException =
            new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

        var validation = handler.handleValidation(validationException);
        Map<String, Object> body = validation.getBody();
        Map<String, String> errors = (Map<String, String>) body.get("validationErrors");

        assertEquals(HttpStatus.BAD_REQUEST.value(), validation.getStatusCode().value());
        assertEquals("Validation failed", body.get("message"));
        assertEquals("must not be blank", errors.get("bookingId"));

        assertEquals(HttpStatus.BAD_REQUEST.value(),
            handler.handleUnreadableBody(new HttpMessageNotReadableException("bad json")).getStatusCode().value());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(),
            handler.handleGeneric(new RuntimeException("boom")).getStatusCode().value());
    }

    @SuppressWarnings("unused")
    private void dummy(String request) {
    }
}
