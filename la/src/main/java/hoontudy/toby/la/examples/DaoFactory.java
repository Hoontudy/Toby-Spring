package hoontudy.toby.la.examples;

public class DaoFactory {
  public UserDao userDao() {
    return new UserDao(connectionMaker());
  }

  public MessageDao messageDao() {
    return new MessageDao(connectionMaker());
  }

  public AccountDao accountDao() {
    return new AccountDao(connectionMaker());
  }

  private ConnectionMaker connectionMaker() {
    return new DConnectionMaker();
  }
}
