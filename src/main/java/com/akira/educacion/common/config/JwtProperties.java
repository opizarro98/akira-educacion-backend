package com.akira.educacion.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.jwt")
@Getter
@Setter
public class JwtProperties {

    private String issuer = "akira-educacion";

    /**
     * Secreto HS256. Debe tener al menos 32 bytes y no debe versionarse.
     */
    private String secret = "";

    private Duration ttl = Duration.ofHours(1);
}
