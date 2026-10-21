package com.akira.educacion.identity.domain;

/**
 * Permisos explícitos verificados por los servicios y controladores.
 */
public final class PermissionCodes {

    public static final String PLATFORM_INSTITUTION_MANAGE = "PLATFORM_INSTITUTION_MANAGE";
    public static final String INSTITUTION_READ = "INSTITUTION_READ";
    public static final String INSTITUTION_WRITE = "INSTITUTION_WRITE";
    public static final String USER_READ = "USER_READ";
    public static final String USER_WRITE = "USER_WRITE";

    private PermissionCodes() {
    }
}
