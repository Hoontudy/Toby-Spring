package hoontudy.toby.la.examples;

import java.sql.Connection;

public interface ConnectionMaker {

  public Connection makeConnection() throws Exception;
}
