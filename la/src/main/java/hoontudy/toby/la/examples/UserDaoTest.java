package hoontudy.toby.la.examples;

public class UserDaoTest {

  public static void main(String[] args) {
    DaoFactory daoFactory = new DaoFactory();
    UserDao userDao = daoFactory.userDao();
  }
}
