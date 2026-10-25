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

    public static final String CHARGE_READ = "CHARGE_READ";
    public static final String CHARGE_WRITE = "CHARGE_WRITE";
    public static final String BILLING_CONCEPT_READ = "BILLING_CONCEPT_READ";
    public static final String BILLING_CONCEPT_WRITE = "BILLING_CONCEPT_WRITE";
    public static final String BILLING_SETTINGS_READ = "BILLING_SETTINGS_READ";
    public static final String BILLING_SETTINGS_WRITE = "BILLING_SETTINGS_WRITE";

    public static final String PAYMENT_READ = "PAYMENT_READ";
    public static final String PAYMENT_WRITE = "PAYMENT_WRITE";
    public static final String PAYMENT_VERIFY = "PAYMENT_VERIFY";
    public static final String PAYMENT_APPLY = "PAYMENT_APPLY";
    public static final String PAYMENT_VOID = "PAYMENT_VOID";
    public static final String RECEIPT_READ = "RECEIPT_READ";

    private PermissionCodes() {
    }
}
