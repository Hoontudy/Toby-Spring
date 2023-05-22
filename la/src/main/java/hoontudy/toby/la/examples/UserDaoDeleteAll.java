package hoontudy.toby.la.examples;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class UserDaoDeleteAll extends UserDao {

  @Override
  protected PreparedStatement makeStatement(Connection c) throws SQLException {
    return c.prepareStatement("delete from users");
  }
}
