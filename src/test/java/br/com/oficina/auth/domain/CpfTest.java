package br.com.oficina.auth.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CpfTest {

  @Test
  void acceptsValidCpfWithOrWithoutMask() {
    assertEquals("52998224725", Cpf.parse("529.982.247-25").value());
    assertEquals("52998224725", Cpf.parse("52998224725").value());
  }

  @Test
  void rejectsInvalidCheckDigitsAndRepeatedValues() {
    assertThrows(IllegalArgumentException.class, () -> Cpf.parse("52998224724"));
    assertThrows(IllegalArgumentException.class, () -> Cpf.parse("11111111111"));
  }
}
