# 토비의 스프링 1일차

## 1.1 초난감 DAO

사용자 정보를 JDBC API를 통해 DB에 저장하고 조회할 수 있는 간단한 DAO를 하나 만들어보자.

> DAO(Data Access Object)는 DB를 사용해 데이터를 조회하거나 조작하는 기능을 전담하도록 만든 오브젝트를 말한다.

### 1.1.1 User

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

### 1.1.2 UserDao

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

### 1.1.3 main()을 이용한 DAO 테스트 코드

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

### 1.2.1 관심사의 분리

`개발자가 객체를 설계할 때 가장 염두에 둬야 할 사항은 바로 미래의 변화를 어떻게 대비할 것인가이다.` 지금 당장 구현하고 있는 기능도 만들기 바쁜데 미래를 생각할 여유가 어딨냐고 반문할 수 있겠지만 지혜로운 개발자는 오늘 이 시간에 미래를 위해 설계하고 개발한다. 그리고 그 덕분에 미래에 닥칠지도 모르는 거대한 작업에 대한 부담과 변경에 따른 엄청난 스트레스, 그로 인해 발생하는 고객과의 사이에서 또 개발팀 내에서의 갈등을 최소화할 수 있다.

객체지향 설계와 프로그래밍이 이전의 절차적 프로그래밍 패러다임에 비해 초기에 좀 더 많은, 번거로운 작업을 요구하는 이유는 객체지향 기술 자체가 지니는, `변화에 효과적으로 대처할 수 있다는 기술적인 특징 때문이다.` 객체지향 기술은 흔히 실세계를 최대한 가깝게 모델링해낼 수 있기 때문에 의미가 있다고 여겨진다. 하지만 그보다는 객체지향 기술이 만들어내는 가상의 추상세계 자체를 효과적으로 구성할 수 있고, 이를 자유롭고 편리하게 변경, 발전, 확장시킬 수 있다는 데 더 의미가 있다.

그렇다면 어떻게 변경이 일어날 때 필요한 작업을 최소화하고, 그 변경이 다른 곳에 문제를 일으키지 않게 할 수 있을까? `분리와 확장을 고려한 설계`가 필요하다.

먼저 분리에 대해 생각해보자.

변경에 대한 요청이 "DB를 오라클에서 MySQL로 바꾸면서, 웹 화면의 레이아웃을 다중 프레임 구조에서 단일 프레임에 Ajax를 적용한 구조로 바꾸고, 매출이 일어날 때에 지난달 평균 매출액보다 많으면 감사 시스템의 정보가 웹 서비스로 전송되는 동시에 로그의 날짜 포맷을 6자리에서 Y2K를 고려해 8자리로 바꿔라"는 식으로 발생하지는 않는다. 무슨 얘긴가 하면, `모든 변경과 발전은 한 번에 한 가지 관심사항에 집중해서 일어난다는 뜻이다.`

문제는, 변화는 대체로 집중된 한 가지 관심에 대해 일어나지만 `그에 따른 작업은 한 곳에 집중되지 않는 경우가 많다는 점이다.` 예를 들어 트랜잭션 기술을 바꿨다고 비지니스 로직의 구조를 변경해야 한다면? 다른 개발자가 작업한 코드에 변경이 일어날 때 마다 내가 작업한 코드도 함께 수정을 해야 한다면? 얼마나 끔찍할지 모르겠다.

변화가 한 번에 한 가지 관심에 집중돼서 일어난다면, 우리가 준비해야 할 일은 한 가지 관심이 한 군데에 집중되게 하는 것이다. `즉 관심이 같은 것끼리는 모으고, 관심이 다른 것은 따로 떨어져 있게 하는 것이다.`

프로그래밍의 기초 개념 중에 `관심사의 분리` 라는게 있다. 이를 객체지향에 적용해보면, `관심이 같은 것끼리는 하나의 객체 안으로 또는 친한 객체로 모이게 하고, 관심이 다른 것은 가능한 한 따로 떨어져서 서로 영향을 주지 않도록 분리하는 것이라고 생각할 수 있다.`

그런데 언젠가는 뭉쳐 있는 여러 종류의 관심사를 적절하게 구분하고 따로 분리하는 작업을 해줘야만 할 때가 온다. `관심사가 같은 것끼리 모으고 다른 것은 분리해줌으로써 같은 관심에 효과적으로 집중할 수 있게 만들어주는 것이다.`

### 1.2.2 커넥션 만들기의 추출

UserDao의 구현된 메소드를 다시 살펴보자. 자세히 들여다보면 add() 메소드 하나에서만 적어도 세 가지 관심사항을 발견할 수 있다.

**UserDao의 관심사항**

- 첫째는 DB와 연결을 위한 커넥션을 어떻게 가져올까라는 관심이다.
- 둘째는 사용자 등록을 위해 DB에 보낼 SQL 문장을 담을 Statement를 만들고 실행하는 것이다. 여기서의 관심은 파라미터로 넘어온 사용자 정보를 Statement에 바인딩시키고, Statement에 담긴 SQL을 DB를 통해 실행시키는 방법이다.
- 셋째는 작업이 끝나면 사용한 리소스인 Statement와 Connection 오브젝트를 닫아줘서 소중한 공유 리소스를 시스템에 돌려주는 것이다.

가장 문제가 되는 것은 첫째 관심사인 DB 연결을 위한 Connection 오브젝트를 가져오는 부분이다. add(), get() 메소드 각각에 DB 커넥션을 가져오는 코드가 중복되어 있다. 현재는 두 개의 메소드지만, 앞으로 수백, 수천 개의 DAO 메소드를 만들게 된다면 DB 커넥션을 가져오는 코드가 여기저기 계속 중복돼서 나타날 것이다.

**중복 코드의 메소드 추출**

가장 먼저 할 일은 커넥션을 가져오는 중복된 코드를 분리하는 것이다. 중복된 DB 연결 코드를 getConnection()이라는 이름의 독립적인 메소드로 만들어둔다. 

```java
//getConnection() 메소드를 추출해서 중복을 제거한 UserDao
private Connection getConnection() throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    return DriverManager.getConnection(
        "jdbc:mysql://localhost/toby", "root", "1234");
}
```

지금은 UserDao 클래스의 메소드가 두 개이지만 나중에 메소드가 2,000개쯤 된다고 상상해보자. DB 연결과 관련된 부분에 변경이 일어났을 경우, 예를 들어 DB 종류와 접속 방법이 바뀌어서 드라이버 클래스와 URL이 바뀌었다거나, 로그인 정보가 변경돼도 앞으로는 getConnection()이라는 한 메소드의 코드만 수정하면 된다.

**변경사항에 대한 검증: 리팩토링과 테스트**

방금 한 작업은 UserDao의 기능에는 아무런 변화를 주지 않았다. 여전히 사용자 정보를 등록하고 조회하는 조금 난감한 DAO 클래스일 뿐이다. 하지만 중요한 변화가 있었다. 앞에서 한 작업은 여러 메소드에 중복돼서 등장하는 특정 관심사항이 담긴 코드를 별도의 메소드로 분리해낸 것이다. 이 작업은 기능에는 영향을 주지 않으면서 코드의 구조만 변경한다. 기능이 추가되거나 바뀐 것은 없지만 UserDao는 이전보다 훨씬 깔끔해졌고 미래의 변화에 좀 더 손쉽게 대응할 수 있는 코드가 됐다. 이런 작업을 `리팩토링`이라고 한다. 또한 위에서 사용한 getConnection()이라고 하는 공통의 기능을 담당하는 메소드로 중복된 코드를 뽑아내는 것을 리팩토링에서는 메소드 추출 기법이라고 부른다.

> 리팩토링은 기존의 코드를 외부의 동작방식에는 변화 없이 내부 구조를 변경해서 재구성하는 작업 또는 기술을 말한다. 리팩토링을 하면 코드 내부의 설계가 개선되어 코드를 이해하기가 더 편해지고, 변화에 효율적으로 대응할 수 있다. 결국 생산성은 올라가고, 코드의 품질은 높아지며, 유지보수하기 용이해지고, 견고하면서도 유연한 제품을 개발할 수 있다. 리팩토링이 절실히 필요한 코드의 특징을 나쁜 냄새라고 부르기도 한다. 대표적으로, 중복된 코드는 매우 흔하게 발견되는 나쁜 냄새다. 이런 코드는 적절한 리팩토링 방법을 적용해 나쁜 냄새를 제거해줘야 한다.

### 1.2.3 DB 커넥션 만들기의 독립

앞에서 만든 UserDao가 발전에 발전을 거듭해서 세계적인 포탈 사이트 N 사와 D 사에서 사용자 관리를 위해 이 UserDao를 구매하겠다는 주문이 들어왔다고 상상해보자. 그런데 납품 과정에서 문제가 발생했다. 문제는 N 사와 D 사가 각기 다른 종류의 DB를 사용하고 있고, DB커넥션을 가져오는 데 있어 독자적으로 만든 방법을 적용하고 싶어한다는 점이다. 더욱 큰 문제는 UserDao를 구매한 이후에도 DB 커넥션을 가져오는 방법이 종종 변경될 가능성이 있다는 점이다.

이런 경우에는 아예 UserDao의 소스코드를 고객에게 제공해주고, 변경이 필요하면 getConnection() 메소드를 수정해서 사용하라고 할 수 있다. 하지만 초특급 비밀기술이 적용된 UserDao인지라 고객에게 소스를 직접 공개하고 싶지는 않다. 고객에게는 미리 컴파일된 클래스 바이너리 파일만 제공하고 싶다. 이런 경우에 UserDao 소스코드를 N 사와 D 사에 제공해주지 않고도 고객 스스로 원하는 DB 커넥션 생성 방식을 적용해가면서 UserDao를 사용하게 할 수 있을까?

**상속을 통한 확장**

일단 우리가 만든 UserDao에서 메소드의 구현 코드를 제거하고 getConnection()을 추상 메소드로 만들어놓는다. 추상 메소드라서 메소드 코드는 없지만 메소드 자체는 존재한다. 따라서 add(), get() 메소드에서 getConnection()을 호출하는 코드는 그대로 유지할 수 있다.

이제 이 추상 클래스인 UserDao를 N 사와 D 사에게 판매한다. UserDao를 구입한 포탈사들은 UserDao 클래스를 상속해서 각각 NUserDao와 DUserDao라는 서브클래스를 만든다. 서브클래스에서는 UserDao에서 추상 메소드로 선언했던 getConnection() 메소드를 원하는 방식대로 구현할 수 있다. 이렇게 하면 UserDao의 소스코드를 제공해서 수정해 쓰도록 하지 않아도 getConnection() 메소드를 원하는 방식으로 확장한 후에 UserDao의 기능과 함께 사용할 수 있다.

기존에는 같은 클래스에 다른 메소드로 분리됐던 DB 커넥션 연결이라는 관심을 이번에는 상속을 통해 서브 클래스로 분리해버리는 것이다.

```java
public abstract class UserDao {
  //구현 코드는 제거되고 추상 메소드로 바뀌었다.  
  protected abstract Connection getConnection() throws Exception;
} 

public class NUserDao extends UserDao {

  @Override
  protected Connection getConnection() throws Exception {
    // N 사 DB Connection 생성 코드
    return null;
  }
}

public class DUserDao extends UserDao{

  @Override
  protected Connection getConnection() throws Exception {
    // D 사 DB Connection 생성 코드
    return null;
  }
}
```

이제는 UserDao의 코드는 한 줄도 수정할 필요 없이 DB 연결 기능을 새롭게 정의한 클래스를 만들 수 있다. 새로운 DB 연결 방법을 적용해야 할 때는 UserDao를 상속을 통해 확장해주기만 하면 된다.

이렇게 슈퍼클래스에 기본적인 로직의 흐름을 만들고, 그 기능의 일부를 추상 메소드나 오버라이딩이 가능한 protected 메소드 등으로 만든 뒤 서브클래스에서 이런 메소드를 필요에 맞게 구현해서 사용하도록 하는 방법을 디자인 패턴에서 `템플릿 메소드 패턴`이라고 한다. UserDao의 getConnection() 메소드는 Connection 타입 오브젝트를 생성한다는 기능을 정의해놓은 추상 메소드다. 그리고 UserDao의 서브클래스의 getConnection() 메소드는 어떤 Connection 클래스의 오브젝트를 어떻게 생성할 것인지를 결정하는 방법이라고도 볼 수 있다. 이렇게 서브클래스에서 구체적인 오브젝트 생성 방법을 결정하게 하는 것을 `팩토리 메소드 패턴` 이라고 부르기도 한다.

UserDao는 어떤 기능을 사용한다는 데에만 관심이 있고, NUserDao나 DUserDao에서는 어떤 식으로 Connection 기능을 제공하는지에 관심을 두고 있는 것이다. 또, 어떤 방법으로 Connection 오브젝트를 만들어내는지도 NUserDao와 DUserDao의 관심사항이다.

> `디자인 패턴`은 소프트웨어 설계 시 특정 상황에서 자주 만나는 문제를 해결하기 위해 사용할 수 있는 재사용 가능한 솔루션을 말한다. 모든 패턴에는 간결한 이름이 있어서 잘 알려진 패턴을 적용하고자 할 때 간단히 패턴 이름을 언급하는 것만으로도 설계의 의도와 해결책을 함께 설명할 수 있다는 장점이 있다.

> `템플릿 메소드 패턴`은 상속을 통해 슈퍼클래스의 기능을 확장할 때 사용하는 가장 대표적인 방법이다. 변하지 않는 기능은 슈퍼 클래스에 만들어두고 자주 변경되며 확장할 기능은 서브클래스에서 만들도록 한다. 슈퍼클래스에서는 미리 추상 메소드 또는 오버라이드 가능한 메소드를 정의해두고 이를 활용해 코드의 기본 알고리즘을 담고 있는 템플릿 메소드를 만든다.

> `팩토리 메소드 패턴`도 템플릿 메소드 패턴과 마찬가지로 상속을 통해 기능을 확장하게 하는 패턴이다. 그래서 구조도 비슷하다. 슈퍼클래스 코드에서는 서브클래스에서 구현할 메소드를 호출해서 필요한 타입의 오브젝트를 가져와 사용한다. 이 메소드는 주로 인터페이스 타입으로 오브젝트를 리턴하므로 서브 클래스에서 어떤 클래스의 오브젝트를 만들어 리턴할지는 슈퍼 클래스에서는 알지 못한다. 서브 클래스는 다양한 방법으로 오브젝트를 생성하는 메소드를 재정의할 수 있다.

이렇게 템플릿 메소드 패턴 또는 팩토리 메소드 패턴으로 관심사항이 다른 코드를 분리해내고, 서로 독립적으로 변경 또는 확장할 수 있도록 만드는 것은 간단하면서도 매우 효과적인 방법이다.

하지만 이 방법은 상속을 사용했다는 단점이 있다. 상속 자체는 간단해 보이고 사용하기도 편리하게 느껴지지만 사실 많은 한계점이 있다.

자바는 클래스의 다중상속을 허용하지 않는다. 단지, 커넥션 객체를 가져오는 방법을 분리하기 위해 상속구조로 만들어버리면, 후에 다른 목적으로 UserDao에 상속을 적용하기 힘들다.

또 다른 문제는 상속을 통한 상하위 클래스의 관계는 생각보다 밀접하다는 점이다.

확장된 기능인 DB 커넥션을 생성하는 코드를 다른 DAO 클래스에 적용할 수 없다는 것도 큰 단점이다.
