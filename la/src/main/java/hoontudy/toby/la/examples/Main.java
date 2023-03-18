//package hoontudy.toby.la.examples;
//
//public class Main {
//
//  public static void main(String[] args) throws Exception {
//    UserDao userDao = new UserDao();
//
//    User user = new User();
//    user.setId("1");
//    user.setName("kim");
//    user.setPassword("1234");
//
//    userDao.add(user);
//
//    System.out.println(String.format("%s 등록 성공", user.getName()));
//
//    User foundUser = userDao.get("1");
//    System.out.println(foundUser.getId());
//    System.out.println(foundUser.getPassword());
//    System.out.println(String.format("%s 조회 성공", foundUser.getName()));
//  }
//}
