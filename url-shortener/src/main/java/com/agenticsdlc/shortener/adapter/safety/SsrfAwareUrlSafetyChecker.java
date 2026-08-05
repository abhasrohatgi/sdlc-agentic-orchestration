package com.agenticsdlc.shortener.adapter.safety;

import com.agenticsdlc.shortener.port.UrlSafetyChecker;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Rejects targets that a shortener should refuse to point at.
 *
 * <h2>The threat</h2>
 *
 * <p>A URL shortener is a redirect-as-a-service, and an open one is useful to an attacker in
 * two distinct ways:
 *
 * <ul>
 *   <li><strong>Scheme abuse.</strong> A {@code javascript:} or {@code data:} target turns
 *       every short link into a delivery mechanism for whatever the creator wanted, wearing
 *       the trust of this service's domain.</li>
 *   <li><strong>SSRF pivot.</strong> If anything downstream follows these links server-side
 *       - a link preview generator, a crawler, a health checker - then a link pointing at
 *       {@code 169.254.169.254} or {@code 127.0.0.1:8080} borrows that component's network
 *       position. The cloud metadata endpoint is the classic target because it hands out
 *       credentials to anyone who can reach it.</li>
 * </ul>
 *
 * <h2>What this does and does not protect</h2>
 *
 * <p>This checker blocks by <em>literal address</em> and by hostname denylist. It does
 * <strong>not</strong> resolve DNS by default, and that is a deliberate limitation rather
 * than an oversight:
 *
 * <ul>
 *   <li>Resolving on the create path adds a network round trip and a dependency on DNS
 *       availability to an operation that should not need either.</li>
 *   <li>Resolution is inherently <strong>time-of-check to time-of-use</strong>. A hostname
 *       that resolves publicly now can resolve to {@code 127.0.0.1} later - DNS rebinding is
 *       a well-known bypass - so a passing check at create time proves nothing at click
 *       time.</li>
 * </ul>
 *
 * <p>The honest conclusion is that <strong>create-time validation cannot make SSRF safe</strong>.
 * It raises the cost of the obvious attack, and the real control belongs at the point of
 * egress: any component that follows a stored URL must apply its own allowlist at the moment
 * it connects. That is stated here so the limitation travels with the code.
 *
 * <p>Set {@code shortener.safety.resolve-dns=true} to additionally check resolved addresses.
 * It is off by default because it makes the create path depend on DNS and, per above, buys
 * less than it appears to.
 */
public class SsrfAwareUrlSafetyChecker implements UrlSafetyChecker {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    /**
     * Hostnames that resolve to somewhere sensitive without looking like an IP address.
     *
     * <p>{@code metadata.google.internal} is included because it is the DNS name for the
     * same metadata service that {@code 169.254.169.254} exposes.
     */
    private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
            "localhost",
            "localhost.localdomain",
            "metadata.google.internal",
            "metadata.goog",
            "instance-data");

    /** Suffixes that only ever name private infrastructure. */
    private static final Set<String> BLOCKED_SUFFIXES = Set.of(
            ".localhost", ".local", ".internal", ".localdomain");

    private final boolean resolveDns;
    private final Set<String> deniedDomains;

    /**
     * @param resolveDns    whether to additionally resolve the host and check the addresses
     * @param deniedDomains additional domains to block, matched on the host and its parents
     */
    public SsrfAwareUrlSafetyChecker(boolean resolveDns, Set<String> deniedDomains) {
        this.resolveDns = resolveDns;
        this.deniedDomains = deniedDomains.stream()
                .map(d -> d.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Default configuration: no DNS resolution, no extra denylist. */
    public SsrfAwareUrlSafetyChecker() {
        this(false, Set.of());
    }

    @Override
    public Verdict check(URI target) {
        if (target == null) {
            return Verdict.rejected("target is missing");
        }
        if (!target.isAbsolute() || target.getScheme() == null) {
            return Verdict.rejected("target must be an absolute URL including a scheme");
        }

        String scheme = target.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            return Verdict.rejected("scheme '" + scheme + "' is not allowed; use http or https");
        }

        String host = target.getHost();
        if (host == null || host.isBlank()) {
            // Also catches an authority the URI parser could not interpret, such as one
            // containing an underscore, which some clients treat differently from others.
            return Verdict.rejected("target must include a valid host");
        }
        host = host.toLowerCase(Locale.ROOT);

        // Strip the brackets IPv6 literals carry in a URI authority.
        String bare = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;

        if (BLOCKED_HOSTNAMES.contains(bare)) {
            return Verdict.rejected("host '" + bare + "' is not a permitted target");
        }
        for (String suffix : BLOCKED_SUFFIXES) {
            if (bare.endsWith(suffix)) {
                return Verdict.rejected("host '" + bare + "' is not a permitted target");
            }
        }
        if (isDenied(bare)) {
            return Verdict.rejected("host '" + bare + "' is on the denylist");
        }

        // A literal address can be judged with no network access at all.
        InetAddress literal = parseLiteralAddress(bare);
        if (literal != null) {
            Verdict verdict = checkAddress(literal, bare);
            if (!verdict.safe()) {
                return verdict;
            }
        } else if (resolveDns) {
            try {
                for (InetAddress resolved : InetAddress.getAllByName(bare)) {
                    Verdict verdict = checkAddress(resolved, bare);
                    if (!verdict.safe()) {
                        return verdict;
                    }
                }
            } catch (UnknownHostException e) {
                return Verdict.rejected("host '" + bare + "' could not be resolved");
            }
        }

        return Verdict.allowed();
    }

    /** Matches the host itself and any parent domain, so a denied apex covers subdomains. */
    private boolean isDenied(String host) {
        if (deniedDomains.isEmpty()) {
            return false;
        }
        String candidate = host;
        while (true) {
            if (deniedDomains.contains(candidate)) {
                return true;
            }
            int dot = candidate.indexOf('.');
            if (dot < 0) {
                return false;
            }
            candidate = candidate.substring(dot + 1);
        }
    }

    private static Verdict checkAddress(InetAddress address, String host) {
        if (address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return Verdict.rejected("host '" + host + "' resolves to a non-public address");
        }
        // isSiteLocalAddress covers 10/8, 172.16/12 and 192.168/16 but not these two, which
        // are equally not routable on the public internet.
        if (address instanceof Inet4Address v4) {
            byte[] octets = v4.getAddress();
            int first = octets[0] & 0xFF;
            int second = octets[1] & 0xFF;
            boolean carrierGradeNat = first == 100 && second >= 64 && second <= 127;
            boolean sharedTest = first == 198 && (second == 18 || second == 19);
            if (carrierGradeNat || sharedTest) {
                return Verdict.rejected("host '" + host + "' resolves to a non-public address");
            }
        }
        if (address instanceof Inet6Address v6) {
            byte[] bytes = v6.getAddress();
            // fc00::/7 - IPv6 unique local addresses, the equivalent of RFC 1918 space.
            if ((bytes[0] & 0xFE) == 0xFC) {
                return Verdict.rejected("host '" + host + "' resolves to a non-public address");
            }
        }
        return Verdict.allowed();
    }

    /**
     * Parses a host as a literal IP address, or returns {@code null} if it is a name.
     *
     * <p>{@link InetAddress#getByName} performs a DNS lookup for anything that is not a
     * literal, which is exactly what must not happen here, so the string is screened first.
     */
    private static InetAddress parseLiteralAddress(String host) {
        boolean looksNumeric = host.chars().allMatch(c -> (c >= '0' && c <= '9') || c == '.');
        boolean looksIpv6 = host.indexOf(':') >= 0;
        if (!looksNumeric && !looksIpv6) {
            return null;
        }
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
