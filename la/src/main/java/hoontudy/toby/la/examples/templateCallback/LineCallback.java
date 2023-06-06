package hoontudy.toby.la.examples.templateCallback;

public interface LineCallback<T> {

  T doSomethingWithLine(String line, T value);
}
