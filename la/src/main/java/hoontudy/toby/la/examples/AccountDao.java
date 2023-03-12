package hoontudy.toby.la.examples;

public class AccountDao {

  private ConnectionMaker connectionMaker;

  public AccountDao(ConnectionMaker connectionMaker) {
    this.connectionMaker = connectionMaker;
  }
}
