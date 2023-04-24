package hoontudy.toby.la.example;

import hoontudy.toby.la.examples.UserDao;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class UserDaoTest {

  @Test
  public void addAndGet() {
    ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");

    UserDao dao = context.getBean("userDao", UserDao.class);

    /* ... */
  }
}