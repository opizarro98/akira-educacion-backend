package com.akira.educacion.communication.domain;

/**
 * Estados de una comunicación asistida.
 *
 * <p>
 * {@link #OPENED} significa que el usuario abrió el enlace {@code wa.me}; no
 * significa que WhatsApp haya enviado o entregado el mensaje, porque la
 * plataforma no confirma ese dato.
 */
public enum CommunicationStatus {
    DRAFT,
    READY,
    OPENED,
    FAILED
}
