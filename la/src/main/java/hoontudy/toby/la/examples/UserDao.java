package hoontudy.toby.la.examples;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

public class UserDao {

  private DataSource dataSource;

  public void setDataSource(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public void add(User user) throws Exception {
    Connection connection = dataSource.getConnection();

    PreparedStatement preparedStatement = connection.prepareStatement(
        "insert into users(id, name, password) values(?, ?, ?)");
    preparedStatement.setString(1, user.getId());
    preparedStatement.setString(2, user.getName());
    preparedStatement.setString(3, user.getPassword());

    preparedStatement.executeUpdate();

    preparedStatement.close();
    connection.close();
  }

  public User get(String id) throws Exception {
    Connection connection = dataSource.getConnection();

    PreparedStatement preparedStatement = connection.prepareStatement(
        "select * from users where id = ?");
    preparedStatement.setString(1, id);

    ResultSet resultSet = preparedStatement.executeQuery();
    resultSet.next();
    User user = new User();
    user.setId(resultSet.getString("id"));
    user.setName(resultSet.getString("name"));
    user.setPassword(resultSet.getString("password"));

    resultSet.close();
    preparedStatement.close();
    connection.close();

    return user;
  }

  public void deleteAll() throws SQLException {
    Connection c = dataSource.getConnection();

    PreparedStatement ps = c.prepareStatement("delete from users");
    ps.executeUpdate();

    ps.close();
    c.close();
  }

  public int getCount() throws SQLException{
    Connection c = dataSource.getConnection();

    PreparedStatement ps = c.prepareStatement("select count(*) from users");

    ResultSet rs = ps.executeQuery();
    rs.next();
    int count = rs.getInt(1);

    rs.close();
    ps.close();
    c.close();

    return count;
  }
}
