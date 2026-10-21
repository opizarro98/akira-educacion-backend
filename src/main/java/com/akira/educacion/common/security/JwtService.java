package com.akira.educacion.common.security;

import com.akira.educacion.common.config.JwtProperties;
import com.akira.educacion.identity.domain.AppUser;
import com.akira.educacion.identity.domain.Permission;
import com.akira.educacion.identity.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Emisión de tokens JWT firmados con HS256.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getTtl());

        Set<String> roles = new TreeSet<>();
        Set<String> authorities = new TreeSet<>();
        for (Role role : user.getRoles()) {
            roles.add(role.getCode());
            authorities.add("ROLE_" + role.getCode());
            for (Permission permission : role.getPermissions()) {
                authorities.add(permission.getCode());
            }
        }

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("roles", new LinkedHashSet<>(roles))
                .claim("authorities", new LinkedHashSet<>(authorities));

        if (user.getInstitution() != null) {
            claims.claim("inst", user.getInstitution().getId().toString());
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
