package hoontudy.toby.la.example;

import static org.assertj.core.api.Assertions.assertThat;

import hoontudy.toby.la.examples.templateCallback.Calculator;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CalcSumTest {

  Calculator calculator;
  String numFilepath;

  @BeforeEach
  public void setup() {
    this.calculator = new Calculator();
    this.numFilepath = getClass().getResource("numbers.txt").getPath();
  }

  @Test
  void sumOfNumbers() throws IOException {
    int sum = this.calculator.calcSum(this.numFilepath);
    assertThat(sum).isEqualTo(10);
  }

  @Test
  public void concatenateStrings() throws IOException {
    assertThat(calculator.concatenate(this.numFilepath)).isEqualTo("1234");
  }
}
