package com.agenticsdlc.shortener.adapter.web;

import com.agenticsdlc.shortener.domain.AliasAlreadyTakenException;
import com.agenticsdlc.shortener.domain.InvalidTargetException;
import com.agenticsdlc.shortener.domain.LinkExpiredException;
import com.agenticsdlc.shortener.domain.LinkNotFoundException;
import java.net.URI;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain exceptions into RFC 7807 problem details.
 *
 * <p>Centralised here rather than handled per-controller so that the same failure always
 * produces the same status and body, and so controllers stay free of error-mapping noise.
 *
 * <p>Every response carries a stable {@code type} URI. Clients should branch on that rather
 * than on the human-readable {@code detail} string, which is free to change.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String TYPE_BASE = "https://agenticsdlc.example/problems/";

    /** Unknown code. */
    @ExceptionHandler(LinkNotFoundException.class)
    public ProblemDetail handleNotFound(LinkNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Link not found");
        problem.setType(URI.create(TYPE_BASE + "link-not-found"));
        problem.setProperty("code", e.code().value());
        return problem;
    }

    /**
     * Known code, past its expiry.
     *
     * <p>410 rather than 404 because "this existed and is gone" is actionable information a
     * client can distinguish from "this was never real" - it means retrying is pointless but
     * the original share was legitimate.
     */
    @ExceptionHandler(LinkExpiredException.class)
    public ProblemDetail handleExpired(LinkExpiredException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, e.getMessage());
        problem.setTitle("Link expired");
        problem.setType(URI.create(TYPE_BASE + "link-expired"));
        problem.setProperty("code", e.code().value());
        problem.setProperty("expiredAt", e.expiredAt().toString());
        return problem;
    }

    /** Requested alias is in use. */
    @ExceptionHandler(AliasAlreadyTakenException.class)
    public ProblemDetail handleAliasTaken(AliasAlreadyTakenException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Alias already taken");
        problem.setType(URI.create(TYPE_BASE + "alias-already-taken"));
        problem.setProperty("code", e.code().value());
        return problem;
    }

    /** Target URL rejected. */
    @ExceptionHandler(InvalidTargetException.class)
    public ProblemDetail handleInvalidTarget(InvalidTargetException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid target URL");
        problem.setType(URI.create(TYPE_BASE + "invalid-target"));
        return problem;
    }

    /**
     * Malformed short code or alias.
     *
     * <p>{@code ShortCode}'s constructor validates, so a bad value arrives here as an
     * {@link IllegalArgumentException} with a message that already names the problem.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid request");
        problem.setType(URI.create(TYPE_BASE + "invalid-request"));
        return problem;
    }

    /** Bean Validation failures on the request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Request validation failed");
        problem.setType(URI.create(TYPE_BASE + "validation-failed"));
        return problem;
    }
}
