package hoontudy.toby.la.examples;

public class MessageDao {

  private ConnectionMaker connectionMaker;

  public MessageDao(ConnectionMaker connectionMaker) {
    this.connectionMaker = connectionMaker;
  }
}
