package BatterySwapStation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Gửi nhiều lỗi validate cùng lúc
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> messages = new HashMap<>();
        for (FieldError err : ex.getBindingResult().getFieldErrors()) {
            messages.put(err.getField(), err.getDefaultMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("messages", messages);
        body.put("error", "Validation failed");
        body.put("status", 400);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Business logic error (email tồn tại, confirmPassword sai, role không tồn tại)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", Map.of("business", ex.getMessage()));
        body.put("error", "Business error");
        body.put("status", 400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
