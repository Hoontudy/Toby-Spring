package hoontudy.toby.la.examples;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class UserDaoTestTest {

  @Test
  public void addAndGet() {
    ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");

    UserDao dao = context.getBean("userDao", UserDao.class);

    /* ... */
  }
}