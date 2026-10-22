package br.com.oficina.auth.adapter.out.jwt;

import br.com.oficina.auth.application.port.out.TokenIssuerPort;
import br.com.oficina.auth.application.port.out.TokenValidatorPort;
import br.com.oficina.auth.config.AuthConfig;
import br.com.oficina.auth.domain.AuthorizedClient;
import br.com.oficina.auth.domain.TokenValidationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.security.SignatureException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

public final class JwtTokenAdapter implements TokenIssuerPort, TokenValidatorPort {

  private static final String CLIENT_ROLE = "CLIENTE";

  private final AuthConfig config;
  private final PrivateKey privateKey;
  private final PublicKey publicKey;

  public JwtTokenAdapter(AuthConfig config) {
    this.config = config;
    this.privateKey =
        config.jwtPrivateKey() == null || config.jwtPrivateKey().isBlank()
            ? null
            : parsePrivateKey(config.jwtPrivateKey());
    this.publicKey = parsePublicKey(config.jwtPublicKey());
  }

  @Override
  public String issue(long clientId) {
    if (privateKey == null) {
      throw new IllegalStateException("JWT_PRIVATE_KEY é obrigatória para emissão de token");
    }
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(Long.toString(clientId))
        .claim("client_id", clientId)
        .claim("role", CLIENT_ROLE)
        .claim("aud", config.jwtAudience())
        .issuer(config.jwtIssuer())
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(config.jwtTtlSeconds())))
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
  }

  @Override
  public AuthorizedClient validate(String token) {
    Claims claims = verifyClaims(token);
    Long clientId = claims.get("client_id", Long.class);
    if (clientId == null) {
      throw new TokenValidationException("TOKEN_CLAIM_INVALID");
    }
    return new AuthorizedClient(claims.getSubject(), clientId, claims.get("role", String.class));
  }

  public Claims verifyClaims(String token) {
    if (token == null || token.isBlank()) {
      throw new TokenValidationException("TOKEN_MISSING");
    }
    Claims claims = parse(token);
    if (!hasAudience(claims.get("aud"), config.jwtAudience())) {
      throw new TokenValidationException("TOKEN_AUDIENCE_INVALID");
    }
    if (!CLIENT_ROLE.equals(claims.get("role", String.class))) {
      throw new TokenValidationException("TOKEN_ROLE_INVALID");
    }
    return claims;
  }

  private Claims parse(String token) {
    try {
      return Jwts.parser()
          .verifyWith(publicKey)
          .requireIssuer(config.jwtIssuer())
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (ExpiredJwtException exception) {
      throw new TokenValidationException("TOKEN_EXPIRED", exception);
    } catch (SignatureException exception) {
      throw new TokenValidationException("TOKEN_SIGNATURE_INVALID", exception);
    } catch (IncorrectClaimException | MissingClaimException exception) {
      throw new TokenValidationException(claimErrorCode(exception.getClaimName()), exception);
    } catch (JwtException | IllegalArgumentException exception) {
      throw new TokenValidationException("TOKEN_MALFORMED", exception);
    }
  }

  private static String claimErrorCode(String claimName) {
    return Claims.ISSUER.equals(claimName) ? "TOKEN_ISSUER_INVALID" : "TOKEN_CLAIM_INVALID";
  }

  private static boolean hasAudience(Object audience, String expected) {
    if (audience instanceof String value) {
      return expected.equals(value);
    }
    if (audience instanceof Collection<?> values) {
      return values.contains(expected);
    }
    return false;
  }

  private static PrivateKey parsePrivateKey(String pem) {
    try {
      byte[] encoded = Base64.getDecoder().decode(content(pem, "PRIVATE KEY"));
      return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    } catch (Exception exception) {
      throw new IllegalArgumentException("Chave privada RSA inválida", exception);
    }
  }

  private static PublicKey parsePublicKey(String pem) {
    try {
      byte[] encoded = Base64.getDecoder().decode(content(pem, "PUBLIC KEY"));
      return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
    } catch (Exception exception) {
      throw new IllegalArgumentException("Chave pública RSA inválida", exception);
    }
  }

  private static String content(String pem, String label) {
    return pem.replace("-----BEGIN " + label + "-----", "")
        .replace("-----END " + label + "-----", "")
        .replaceAll("\\s", "");
  }
}
