package br.com.oficina.auth.application.port.out;

public interface TokenIssuerPort {

  String issue(long clientId);
}
