package br.com.oficina.auth.observability;

import java.util.regex.Pattern;

public final class LogSanitizer {

  public static final String REDACTED = "REDACTED";

  private static final Pattern UNSAFE_IDENTIFIER = Pattern.compile("[^A-Za-z0-9._:-]");
  private static final Pattern UNSAFE_CODE = Pattern.compile("[^A-Z0-9_]");
  private static final Pattern LONG_DIGIT_RUN = Pattern.compile("\\d{4,}");
  private static final Pattern DOCUMENT_LIKE =
      Pattern.compile("\\d{3}\\D{0,2}\\d{3}\\D{0,2}\\d{3}\\D{0,2}\\d{2}");
  private static final int MAX_LENGTH = 64;

  private LogSanitizer() {}

  public static String identifier(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String cleaned = UNSAFE_IDENTIFIER.matcher(maskDocuments(value.trim())).replaceAll("");
    if (cleaned.isBlank()) {
      return null;
    }
    return truncate(cleaned);
  }

  public static String code(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String cleaned = UNSAFE_CODE.matcher(maskDocuments(value.trim()).toUpperCase()).replaceAll("_");
    if (cleaned.chars().noneMatch(Character::isLetterOrDigit)) {
      return null;
    }
    return truncate(cleaned);
  }

  private static String maskDocuments(String value) {
    String withoutDocuments = DOCUMENT_LIKE.matcher(value).replaceAll(REDACTED);
    return LONG_DIGIT_RUN.matcher(withoutDocuments).replaceAll(REDACTED);
  }

  private static String truncate(String value) {
    return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
  }
}
