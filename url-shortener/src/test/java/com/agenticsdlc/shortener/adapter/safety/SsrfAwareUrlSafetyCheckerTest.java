package com.agenticsdlc.shortener.adapter.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.shortener.port.UrlSafetyChecker;
import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SsrfAwareUrlSafetyCheckerTest {

    private final UrlSafetyChecker checker = new SsrfAwareUrlSafetyChecker();

    @Nested
    @DisplayName("accepts ordinary public targets")
    class Allows {

        @ParameterizedTest
        @ValueSource(strings = {
                "https://example.com",
                "http://example.com/path?query=1#frag",
                "https://sub.domain.example.co.uk/deep/path",
                "https://example.com:8443/on-a-port",
                "https://8.8.8.8/public-literal-address"
        })
        void allowsPublicTargets(String url) {
            assertThat(checker.check(URI.create(url)).safe()).isTrue();
        }
    }

    @Nested
    @DisplayName("scheme abuse")
    class Schemes {

        @ParameterizedTest
        @ValueSource(strings = {
                "javascript:alert(1)",
                "data:text/plain;base64,SGVsbG8=",
                "file:///etc/passwd",
                "ftp://example.com/x",
                "gopher://example.com/x"
        })
        @DisplayName("rejects anything that is not http or https")
        void rejectsNonHttpSchemes(String url) {
            // A shortener that redirects to javascript: is an XSS delivery service wearing
            // this domain's reputation.
            UrlSafetyChecker.Verdict verdict = checker.check(URI.create(url));

            assertThat(verdict.safe()).isFalse();
            assertThat(verdict.reason()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("SSRF: non-public addresses")
    class NonPublicAddresses {

        @ParameterizedTest
        @ValueSource(strings = {
                "http://127.0.0.1/admin",             // loopback
                "http://127.1.2.3/admin",             // the whole 127/8 block
                "http://0.0.0.0/",                    // any-local
                "http://10.0.0.5/internal",           // RFC 1918
                "http://172.16.3.4/internal",         // RFC 1918
                "http://192.168.1.1/router",          // RFC 1918
                "http://169.254.169.254/latest/meta-data/",  // cloud metadata
                "http://100.64.0.1/",                 // carrier-grade NAT
                "http://198.18.0.1/",                 // benchmarking range
                "http://224.0.0.1/",                  // multicast
                "http://[::1]/admin",                 // IPv6 loopback
                "http://[fc00::1]/",                  // IPv6 unique local
                "http://[fe80::1]/"                   // IPv6 link-local
        })
        @DisplayName("rejects literal addresses that are not publicly routable")
        void rejectsPrivateLiterals(String url) {
            assertThat(checker.check(URI.create(url)).safe())
                    .as("%s should be rejected", url)
                    .isFalse();
        }

        @Test
        @DisplayName("rejects the cloud metadata endpoint by both address and hostname")
        void rejectsMetadataEndpoint() {
            // 169.254.169.254 hands out instance credentials to anything that can reach it,
            // which makes it the single highest-value SSRF target in a cloud deployment.
            assertThat(checker.check(URI.create("http://169.254.169.254/")).safe()).isFalse();
            assertThat(checker.check(URI.create("http://metadata.google.internal/")).safe())
                    .isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "http://localhost/admin",
                "http://LOCALHOST/admin",
                "http://api.internal/secrets",
                "http://db.local/",
                "http://box.localdomain/"
        })
        @DisplayName("rejects hostnames that name private infrastructure, case-insensitively")
        void rejectsPrivateHostnames(String url) {
            assertThat(checker.check(URI.create(url)).safe()).isFalse();
        }
    }

    @Nested
    @DisplayName("malformed targets")
    class Malformed {

        @Test
        @DisplayName("rejects a relative URL")
        void rejectsRelative() {
            assertThat(checker.check(URI.create("/just/a/path")).safe()).isFalse();
        }

        @Test
        @DisplayName("rejects a URL with no host")
        void rejectsMissingHost() {
            assertThat(checker.check(URI.create("http:///nohost")).safe()).isFalse();
        }

        @Test
        @DisplayName("rejects null rather than throwing")
        void rejectsNull() {
            assertThat(checker.check(null).safe()).isFalse();
        }
    }

    @Nested
    @DisplayName("configurable denylist")
    class Denylist {

        private final UrlSafetyChecker denying =
                new SsrfAwareUrlSafetyChecker(false, Set.of("malware.example", "Bad.Example"));

        @Test
        @DisplayName("rejects a denied domain")
        void rejectsDeniedDomain() {
            assertThat(denying.check(URI.create("https://malware.example/payload")).safe())
                    .isFalse();
        }

        @Test
        @DisplayName("a denied apex also covers its subdomains")
        void deniesSubdomains() {
            // Otherwise blocking a domain is trivially bypassed by prefixing any label.
            assertThat(denying.check(URI.create("https://cdn.malware.example/x")).safe()).isFalse();
            assertThat(denying.check(URI.create("https://a.b.c.malware.example/x")).safe())
                    .isFalse();
        }

        @Test
        @DisplayName("denylist matching is case-insensitive in both directions")
        void caseInsensitive() {
            assertThat(denying.check(URI.create("https://BAD.EXAMPLE/x")).safe()).isFalse();
        }

        @Test
        @DisplayName("does not reject a domain that merely ends with the denied string")
        void doesNotOverMatch() {
            // "notmalware.example" must not match "malware.example": suffix matching without
            // a label boundary would block unrelated domains.
            assertThat(denying.check(URI.create("https://notmalware.example/x")).safe()).isTrue();
        }

        @Test
        @DisplayName("leaves other domains alone")
        void allowsOthers() {
            assertThat(denying.check(URI.create("https://example.com/x")).safe()).isTrue();
        }
    }

    @Test
    @DisplayName("does not perform DNS resolution by default")
    void doesNotResolveDnsByDefault() {
        // A hostname that cannot resolve must still be accepted when resolution is off,
        // which is what proves the create path does not depend on DNS being reachable.
        assertThat(checker.check(
                URI.create("https://this-host-does-not-exist-" + System.nanoTime() + ".example/"))
                .safe())
                .isTrue();
    }
}
