package cn.com.v2.common.exception;

import cn.com.v2.common.domain.AjaxResult;
import cn.dev33.satoken.exception.NotLoginException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler. Converts Sa-Token NotLoginException to HTTP 401
 * so the frontend can redirect to the login / AI-SOC handoff page.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<AjaxResult> handleNotLoginException(NotLoginException e) {
        AjaxResult body = AjaxResult.error(401, "Not logged in");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<AjaxResult> handleRuntimeException(RuntimeException e) {
        String message = e.getMessage() == null ? "Error" : e.getMessage();
        if (message.contains("Tenant context") || message.contains("not in current tenant") || message.contains("Project not found")) {
            AjaxResult body = AjaxResult.error(403, message);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }
        AjaxResult body = AjaxResult.error(500, message);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
