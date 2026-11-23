package pl.hadacz.coupons.coupon.domain;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// Client IP address. Parsed with `InetAddress.ofLiteral` (Java 22+), which accepts only
/// IPv4/IPv6 literals and never performs a DNS lookup, unlike `InetAddress.getByName`.
public record IpAddress(InetAddress address) {

    public IpAddress {
        Objects.requireNonNull(address, "address");
    }

    public static IpAddress of(@Nullable String literal) {
        if (literal == null) {
            throw new DomainValidationException("ip", "Client IP address is missing");
        }
        try {
            return new IpAddress(InetAddress.ofLiteral(literal.strip()));
        } catch (IllegalArgumentException e) {
            throw new DomainValidationException("ip", "Not an IP address literal: " + literal);
        }
    }

    /// Whether the address can be geolocated. Loopback, private, link-local and similar ranges
    /// say nothing about the client's country and are never sent to an external provider.
    public boolean isPublic() {
        return !(address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isCarrierGradeNat()
                || isUniqueLocalIpv6());
    }

    public String value() {
        return address.getHostAddress();
    }

    // 100.64.0.0/10, shared address space used by carrier-grade NAT (RFC 6598).
    private boolean isCarrierGradeNat() {
        byte[] bytes = address.getAddress();
        return address instanceof Inet4Address && (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 64;
    }

    // fc00::/7, IPv6 unique local addresses (RFC 4193).
    private boolean isUniqueLocalIpv6() {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xFE) == 0xFC;
    }

    @Override
    public String toString() {
        return value();
    }
}
