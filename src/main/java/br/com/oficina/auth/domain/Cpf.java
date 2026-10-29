package br.com.oficina.auth.domain;

import java.util.regex.Pattern;

public record Cpf(String value) {

  private static final Pattern NON_DIGIT = Pattern.compile("\\D");
  private static final Pattern REPEATED = Pattern.compile("(\\d)\\1{10}");

  public Cpf {
    if (!isValid(value)) {
      throw new IllegalArgumentException("CPF inválido");
    }
  }

  public static Cpf parse(String raw) {
    return new Cpf(normalize(raw));
  }

  public static String normalize(String raw) {
    return raw == null ? "" : NON_DIGIT.matcher(raw).replaceAll("");
  }

  public static boolean isValid(String normalized) {
    if (normalized == null || normalized.length() != 11 || REPEATED.matcher(normalized).matches()) {
      return false;
    }
    return digit(normalized, 9) == normalized.charAt(9) - '0'
        && digit(normalized, 10) == normalized.charAt(10) - '0';
  }

  private static int digit(String cpf, int length) {
    int sum = 0;
    for (int index = 0; index < length; index++) {
      sum += (cpf.charAt(index) - '0') * (length + 1 - index);
    }
    int remainder = 11 - (sum % 11);
    return remainder >= 10 ? 0 : remainder;
  }
}
