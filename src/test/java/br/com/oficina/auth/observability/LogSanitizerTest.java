package br.com.oficina.auth.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LogSanitizerTest {

  @Test
  void keepsTechnicalIdentifiers() {
    assertEquals("JKJaXmPLvHcESHA-1", LogSanitizer.identifier(" JKJaXmPLvHcESHA-1 "));
    assertEquals("abc.def:1", LogSanitizer.identifier("abc.def:1"));
  }

  @Test
  void removesUnsafeCharactersAndBlankValues() {
    assertEquals("Bearertoken", LogSanitizer.identifier("Bearer token"));
    assertNull(LogSanitizer.identifier("   "));
    assertNull(LogSanitizer.identifier(null));
    assertNull(LogSanitizer.code("---"));
  }

  @Test
  void redactsMaskedAndPlainDocuments() {
    assertEquals(LogSanitizer.REDACTED, LogSanitizer.identifier("529.982.247-25"));
    assertEquals(LogSanitizer.REDACTED, LogSanitizer.identifier("52998224725"));
    assertEquals(LogSanitizer.REDACTED, LogSanitizer.code("52998224725"));
    assertTrue(LogSanitizer.identifier("cliente-529.982.247-25").contains(LogSanitizer.REDACTED));
  }

  @Test
  void normalizesErrorCodes() {
    assertEquals("TOKEN_EXPIRED", LogSanitizer.code("token expired"));
    assertEquals("HTTP_502", LogSanitizer.code("http 502"));
  }

  @Test
  void limitsLength() {
    assertEquals(64, LogSanitizer.identifier("a".repeat(200)).length());
    assertEquals(64, LogSanitizer.code("A".repeat(200)).length());
  }
}
