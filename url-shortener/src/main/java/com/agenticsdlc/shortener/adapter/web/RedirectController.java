package com.agenticsdlc.shortener.adapter.web;

import com.agenticsdlc.shortener.application.LinkService;
import com.agenticsdlc.shortener.domain.ShortCode;
import com.agenticsdlc.shortener.domain.ShortLink;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The redirect hot path.
 *
 * <p>This is the endpoint that carries essentially all of the service's traffic, and the one
 * whose latency the brownfield orchestration scenario is asked to improve. Anything added
 * here is on the critical path for every click.
 */
@RestController
public class RedirectController {

    private final LinkService linkService;

    public RedirectController(LinkService linkService) {
        this.linkService = linkService;
    }

    /**
     * Redirects a short code to its target.
     *
     * <p>Returns <strong>302 Found</strong> rather than 301. A permanent redirect is cached
     * indefinitely by browsers and intermediaries, which would make deletion and expiry
     * unenforceable: a client that has seen a 301 will keep following it after the link is
     * gone. Since links here are deletable and can expire, 302 is the only correct choice.
     *
     * <p>{@code Cache-Control: no-store} is set for the same reason.
     *
     * @return 302 to the target, 404 if unknown, 410 if expired
     */
    @GetMapping("/{code:[A-Za-z0-9_-]{1,32}}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        ShortLink link = linkService.resolve(new ShortCode(code));

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, link.target().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
