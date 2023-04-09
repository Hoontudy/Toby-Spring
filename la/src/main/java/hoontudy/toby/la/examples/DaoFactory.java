package hoontudy.toby.la.examples;

import com.mysql.cj.jdbc.MysqlDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DaoFactory {

  @Bean
  public UserDao userDao() {
    UserDao userDao = new UserDao();
    userDao.setDataSource(dataSource());
    return userDao;
  }

  @Bean
  public DataSource dataSource() {
    MysqlDataSource dataSource = new MysqlDataSource();
    dataSource.setUrl("jdbc:mysql://localhost:3306/springbook");
    dataSource.setUser("root");
    dataSource.setPassword("1234");
    return dataSource;
  }

  @Bean
  public ConnectionMaker connectionMaker() {
    return new DConnectionMaker();
  }

  public MessageDao messageDao() {
    return new MessageDao(connectionMaker());
  }

  public AccountDao accountDao() {
    return new AccountDao(connectionMaker());
  }
}
