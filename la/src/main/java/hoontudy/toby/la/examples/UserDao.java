package hoontudy.toby.la.examples;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserDao {

  public void add(User user) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    Connection connection = DriverManager.getConnection(
        "jdbc:mysql://localhost/toby", "root", "1234");

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
    Class.forName("com.mysql.cj.jdbc.Driver");
    Connection connection = DriverManager.getConnection(
        "jdbc:mysql://localhost/toby", "root", "1234");

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
}
