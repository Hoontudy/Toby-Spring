package hoontudy.toby.la.examples;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class UserDaoTest {

  public static void main(String[] args) {
    ApplicationContext context = new AnnotationConfigApplicationContext();
    UserDao dao = context.getBean("userDao", UserDao.class);
  }
}
