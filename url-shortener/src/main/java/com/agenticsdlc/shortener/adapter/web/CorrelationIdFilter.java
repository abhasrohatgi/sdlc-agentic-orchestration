package com.agenticsdlc.shortener.adapter.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Attaches a correlation id to every request, for logs and for the client.
 *
 * <p>An inbound {@code X-Correlation-Id} is honoured so a trace can span services; otherwise
 * one is generated. The value is echoed on the response so that a user reporting a problem
 * can quote an identifier that appears in the logs.
 *
 * <p>Inbound values are validated against a strict pattern and otherwise replaced. This
 * matters more than it looks: the value goes into log output, and an unvalidated
 * caller-controlled string lets an attacker inject newlines and forge whole log lines. Log
 * forging is a real technique for hiding activity from whoever reads the logs afterwards.
 *
 * <p>The MDC entry is removed in a {@code finally} block. Request threads are pooled, and a
 * leaked MDC entry would silently tag a later, unrelated request with the previous
 * request's id - producing logs that are confidently wrong.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Deliberately narrow: alphanumerics, hyphen and underscore, bounded length. */
    private static final Pattern ACCEPTABLE = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = sanitise(request.getHeader(HEADER));

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String sanitise(String inbound) {
        if (inbound != null && ACCEPTABLE.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString();
    }
}
