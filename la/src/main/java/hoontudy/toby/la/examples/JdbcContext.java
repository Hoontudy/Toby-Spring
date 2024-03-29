package hoontudy.toby.la.examples;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

public class JdbcContext {

  private DataSource dataSource;

  public void setDataSource(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public void executeSql(final String query) throws SQLException {
    workWithStatementStrategy(c -> c.prepareStatement(query));
  }
  public void workWithStatementStrategy(StatementStrategy stmt) {
    Connection c = null;
    PreparedStatement ps = null;

    try {
      c = this.dataSource.getConnection();
      ps = stmt.makePreparedStatement(c);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new DuplicateUserIdException("duplicated user id.");
    } finally {
      if (ps != null) {
        try {
          ps.close();
        } catch (SQLException ignored) {

        }
      }

      if (c != null) {
        try {
          c.close();
        } catch (SQLException ignored) {

        }
      }
    }
  }
}
