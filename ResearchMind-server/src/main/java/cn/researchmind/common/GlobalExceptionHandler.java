package cn.researchmind.common;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(
            ApiException exception,
            HttpServletRequest request
    ) {
        return build(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "请求参数校验失败",
                request.getRequestURI(),
                errors
        );
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> handleDuplicateKey(
            DuplicateKeyException exception,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.CONFLICT,
                "RESOURCE_ALREADY_EXISTS",
                "用户名或邮箱已被使用",
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "PDF_TOO_LARGE",
                "PDF 文件不能超过 50 MB",
                request.getRequestURI(),
                Map.of()
        );
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            String code,
            String message,
            String path,
            Map<String, String> fieldErrors
    ) {
        return ResponseEntity.status(status).body(new ApiError(
                status.value(),
                code,
                message,
                path,
                OffsetDateTime.now(),
                fieldErrors
        ));
    }
}
