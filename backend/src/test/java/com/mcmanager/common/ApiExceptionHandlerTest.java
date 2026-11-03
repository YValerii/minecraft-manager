package com.mcmanager.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void apiExceptionKeepsItsStatusAndMessage() {
        ResponseEntity<?> res = handler.handleApi(new ApiException(HttpStatus.CONFLICT, "busy"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).isEqualTo(Map.of("message", "busy"));
    }

    @Test
    void validationErrorIsABadRequest() {
        ResponseEntity<?> res = handler.handleValidation(mock(MethodArgumentNotValidException.class));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isEqualTo(Map.of("message", "Invalid request data"));
    }

    @Test
    void anythingElseIsAnInternalError() {
        ResponseEntity<?> res = handler.handleOther(new IllegalStateException("boom"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody()).isEqualTo(Map.of("message", "Internal error: boom"));
    }
}
