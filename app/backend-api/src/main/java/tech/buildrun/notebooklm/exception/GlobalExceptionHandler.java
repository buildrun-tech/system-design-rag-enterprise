package tech.buildrun.notebooklm.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message) {
        log.warn("Request rejected: status={} code={} message={}", status.value(), code, message);
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }

    @ExceptionHandler(NotebookNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotebookNotFound(NotebookNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "NOTEBOOK_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleConversationNotFound(ConversationNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(QuizNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleQuizNotFound(QuizNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "QUIZ_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(InvalidSourceIdsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSourceIds(InvalidSourceIdsException e) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_SOURCE_IDS", e.getMessage());
    }

    @ExceptionHandler(SourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSourceNotFound(SourceNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "SOURCE_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFileType(UnsupportedFileTypeException e) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse("Campo invalido");
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
