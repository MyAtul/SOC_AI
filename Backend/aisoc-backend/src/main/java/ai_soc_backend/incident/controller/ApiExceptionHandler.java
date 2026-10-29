package ai_soc_backend.incident.controller;

import ai_soc_backend.incident.service.IncidentNotFoundException;
import ai_soc_backend.incident.service.ApprovalDecisionConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApprovalDecisionConflictException.class)
    public ResponseEntity<ApiError> handleApprovalConflict(ApprovalDecisionConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(409, "DECISION_CONFLICT", "Incident already has a decision or is not awaiting one"));
    }

    @ExceptionHandler(IncidentNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(IncidentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(404, "NOT_FOUND", "Incident not found"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiError> handleValidation(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError(400, "VALIDATION_ERROR", "Request validation failed"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError(400, "MALFORMED_REQUEST", "Request body is missing or malformed"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        return ResponseEntity.internalServerError()
                .body(new ApiError(500, "INTERNAL_ERROR", "An unexpected server error occurred"));
    }

    public record ApiError(int status, String code, String message) {
    }
}
