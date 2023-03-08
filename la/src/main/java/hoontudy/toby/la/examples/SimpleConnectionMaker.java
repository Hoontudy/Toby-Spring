package hoontudy.toby.la.examples;

import java.sql.Connection;
import java.sql.DriverManager;

public class SimpleConnectionMaker {

  public Connection makeNewConnection() throws Exception {
    Class.forName("com.mysql.jdbc.Driver");
    return DriverManager.getConnection(
        "jdbc:mysql://localhost/toby", "root", "1234");
  }
}
