package com.akira.educacion.communication.service;

import com.akira.educacion.common.api.BadRequestException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalización de teléfonos y construcción de enlaces {@code wa.me}.
 *
 * <p>
 * El enlace solo abre WhatsApp con el texto precargado. La plataforma no puede
 * confirmar que el mensaje se haya enviado o entregado.
 */
public final class WhatsAppSupport {

    public static final Set<String> ALLOWED_PLACEHOLDERS = Set.of(
            "estudiante", "representante", "institucion", "fecha", "detalle_financiero");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

    private WhatsAppSupport() {
    }

    /**
     * Acepta números con espacios, guiones o paréntesis. Los números locales de
     * Ecuador que comienzan por {@code 0} se convierten al prefijo {@code 593}.
     */
    public static String normalizePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new BadRequestException("El representante no tiene un teléfono registrado");
        }
        String digits = rawPhone.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 10 && digits.startsWith("0")) {
            digits = "593" + digits.substring(1);
        }
        if (digits.length() < 8 || digits.length() > 15) {
            throw new BadRequestException("El teléfono del representante no es válido");
        }
        return digits;
    }

    public static String waLink(String normalizedPhone, String message) {
        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
        return "https://wa.me/" + normalizedPhone + "?text=" + encoded;
    }

    /**
     * Rechaza marcadores desconocidos para evitar mensajes incompletos o fugas
     * de información no previstas.
     */
    public static void validatePlaceholders(String body) {
        Matcher matcher = PLACEHOLDER.matcher(body);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!ALLOWED_PLACEHOLDERS.contains(key)) {
                throw new BadRequestException("Marcador no permitido en la plantilla: {{" + matcher.group(1) + "}}");
            }
        }
    }

    public static String render(String body, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(body);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            String replacement = values.getOrDefault(key, "");
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
