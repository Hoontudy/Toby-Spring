package hoontudy.toby.la.examples;

import java.util.Arrays;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class UserDaoTest {

  public static void main(String[] args) {
    ApplicationContext context = new AnnotationConfigApplicationContext();
    System.out.println(Arrays.toString(context.getBeanDefinitionNames()));
  }
}
