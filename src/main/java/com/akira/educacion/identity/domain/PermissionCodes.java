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

    public static final String STUDENT_READ = "STUDENT_READ";
    public static final String STUDENT_WRITE = "STUDENT_WRITE";
    public static final String STUDENT_NOTES_READ = "STUDENT_NOTES_READ";
    public static final String REPRESENTATIVE_READ = "REPRESENTATIVE_READ";
    public static final String REPRESENTATIVE_WRITE = "REPRESENTATIVE_WRITE";
    public static final String CATALOG_READ = "CATALOG_READ";
    public static final String CATALOG_WRITE = "CATALOG_WRITE";
    public static final String ENROLLMENT_READ = "ENROLLMENT_READ";
    public static final String ENROLLMENT_WRITE = "ENROLLMENT_WRITE";

    private PermissionCodes() {
    }
}
