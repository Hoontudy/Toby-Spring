package hoontudy.toby.la.examples;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

public abstract class UserDao {

  private DataSource dataSource;
  private JdbcContext jdbcContext;

  public void setDataSource(DataSource dataSource) {
    this.jdbcContext = new JdbcContext();
    this.jdbcContext.setDataSource(dataSource);
    this.dataSource = dataSource;
  }

  public void add(final User user) throws Exception {
    this.jdbcContext.workWithStatementStrategy(c -> {
      PreparedStatement ps = c.prepareStatement(
          "insert into users(id, name, password) values (?, ?, ?)");
      ps.setString(1, user.getId());
      ps.setString(2, user.getName());
      ps.setString(3, user.getPassword());
      return ps;
    });
  }

  public User get(String id) throws Exception {
    return null;
  }

  public void deleteAll() throws SQLException {
    /* no-op */
  }

  public int getCount() throws SQLException {
    return 0;
  }

  abstract protected PreparedStatement makeStatement(Connection c) throws SQLException;
}
