package br.com.oficina.auth.domain;

public final class TokenValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String code;

  public TokenValidationException(String code) {
    super(code);
    this.code = code;
  }

  public TokenValidationException(String code, Throwable cause) {
    super(code, cause);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
