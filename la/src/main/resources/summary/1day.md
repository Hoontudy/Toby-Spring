# 토비의 스프링 1일차

## 1.1 초난감 DAO
사용자 정보를 JDBC API를 통해 DB에 저장하고 조회할 수 있는 간단한 DAO를 하나 만들어보자.

> DAO(Data Access Object)는 DB를 사용해 데이터를 조회하거나 조작하는 기능을 전담하도록 만든 오브젝트를 말한다.

### User
사용자 정보를 저장할 User 클래스를 만든다.

```java
public class User {
  
  private String id;
  private String name;
  private String password;

  public User(String id, String name, String password) {
    this.id = id;
    this.name = name;
    this.password = password;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getPassword() {
    return password;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
```

이제 해당 User 객체를 실제로 저장할 DB 테이블을 만들어준다.

```h2
create table users (
    id varchar(10) primary key, 
    name varchar(20) not null, 
    password varchar(10) not null
)
```

> `자바빈(JavaBean)은 원래 비주얼 툴에서 조작 가능한 컴포넌트를 말한다.` 자바의 주력 개발 플랫폼이 웹 기반의 엔터프라이즈 방식으로 바뀌면서 비주얼 컴포넌트로서 자바빈은 인기를 잃어 갔지만, 자바빈의 몇 가지 코딩 관례는 JSP 빈, EJB와 같은 표준 기술과 자바빈 스타일의 오브젝트를 사용하는 오픈소스 기술을 통해 계속 이어져 왔다. `간단히 빈이라고 부르기도 한다.`

### UserDao
사용자 정보를 DB에 넣고 관리할 수 있는 DAO 클래스를 만들어보자. 일단 새로운 사용자를 생성, 아이디로 사용자 정보를 읽어오는 두 개의 메소드를 먼저 만들어보자.

JDBC를 이용하는 작업의 일반적인 순서는 다음과 같다.
- DB 연결을 위한 Connection을 가져온다.
- SQL을 담은 Statement(또는 Preparedstatement)를 만든다.
- 만들어진 Statement를 실행한다.
- 조회의 경우 SQL 쿼리의 실행 결과를 ResultSet으로 받아서 정보를 저장할 오브젝트에 옮겨준다.
- 작업 중에 생성된 Connection, Statement, ResultSet 같은 리소스는 작업을 마친 후 반드시 닫아준다.
- JDBC API가 만들어내는 예외를 잡아서 직접 처리하거나, 메소드에 throws를 선언해서 예외가 발생하면 메소드 밖으로 던지게 한다.

```java
public class UserDao {

  public void add(User user) throws Exception {
    Class.forName("com.mysql.jdbc.Driver");
    Connection connection = DriverManager.getConnection(
        "jdbc:mysql://localhost/toby", "root", "1234");
    
    PreparedStatement preparedStatement = connection.prepareStatement(
        "insert into users(id, name, password) values(?, ?, ?)");
    preparedStatement.setString(1, user.getId());
    preparedStatement.setString(2, user.getName());
    preparedStatement.setString(3, user.getPassword());

    preparedStatement.executeUpdate();

    preparedStatement.close();
    connection.close();
  }

  public User get(String id) throws Exception {
    Class.forName("com.mysql.jdbc.Driver");
    Connection connection = DriverManager.getConnection(
        "jdbc:mysql://localhost/toby", "root", "1234");
    
    PreparedStatement preparedStatement = connection.prepareStatement(
        "select * from users where id = ?");
    preparedStatement.setString(1, id);

    ResultSet resultSet = preparedStatement.executeQuery();
    resultSet.next();
    User user = new User();
    user.setId(resultSet.getString("id"));
    user.setName(resultSet.getString("name"));
    user.setPassword(resultSet.getString("password"));

    resultSet.close();
    preparedStatement.close();
    connection.close();
    
    return user;
  }
}
```

### main()을 이용한 DAO 테스트 코드
만들어진 코드의 기능을 검증하고자 할 때 사용할 수 있는 가장 간단한 방법은 오브젝트 스스로 자신을 검증하도록 만들어주는 것이다.

```java
public class Main {

  public static void main(String[] args) throws Exception {
    UserDao userDao = new UserDao();

    User user = new User();
    user.setId("1");
    user.setName("kim");
    user.setPassword("1234");

    userDao.add(user);

    System.out.println(String.format("%s 등록 성공", user.getName()));

    User foundUser = userDao.get("1");
    System.out.println(foundUser.getId());
    System.out.println(foundUser.getPassword());
    System.out.println(String.format("%s 조회 성공", foundUser.getName()));
  }
}
```

해당 클래스를 실행하면 다음과 같은 테스트 성공 메시지를 얻을 수 있다.

```
kim 등록 성공
1
1234
kim 조회 성공
```

이렇게 해서 사용자 정보의 등록과 조회가 되는 초간단 DAO와 테스트용 메소드까지 완성했다. 그런데 지금 만든 UserDao 클래스 코드에는 여러 가지 문제가 있다.

이제부터 이 문제 많은 초난감 DAO 코드를 객체지향 기술의 원리에 충실한 멋진 스프링 스타일의 코드로 개선해보는 작업을 할 것이다.

## 1.2 DAO의 분리