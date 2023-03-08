package hoontudy.toby.la.examples;

public class UserDaoTest {

  public static void main(String[] args) {
    ConnectionMaker connectionMaker = new DConnectionMaker();
    UserDao dao = new UserDao(connectionMaker);
  }
}
