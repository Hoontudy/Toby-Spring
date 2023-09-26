AOP를 이해하려면 스프링이 AOP를 도입한 이유와 적용을 통해 얻을 수 있는 장점이 무엇인지에 대한 충분한 이해가 필요하다. 스프링에 적용된 가장 인기있는 AOP 적용은 트랜잭션 기능이다. 트랜잭션 경계설정 기능을 AOP를 이용해 더욱 깔끔하게 바꿔보자

## 6.1 트랜잭션 코드의 분리

### 6.1.1 메소드 분리

```java
public void upgradeLevels() {
	
	TransactionStatus status = 
				this.transactionManager.getTransaction(new DefaultTransactionDefinition());	

	try {
		List<User> users = userDao.getAll();
		for (User user : users) {
			if(canUpgradeLevel(user)) {
				upgradeLevel(user);
			}
		}

		this.transactionManger.commit(status);
	} catch (RuntimeException e) {
		this.transactionManger.rollback(status);
		throw e;
	}
}
```

트랜잭션 경계설정이 꼭 필요해서 스프링에서 제공해주는 인터페이스를 사용해 최대한 깔끔하게 구현했음에도, 여전히 비지니스 로직이 있어야할 Service 계층에 트랜잭션 코드가 존재한다.

위의 코드를 보면 가운데 비지니스 로직을 둘러싸고 트랜잭션 경계설정 코드가 존재한다. 또 비지니스로직 코드는 트랜잭션 경계코드와 자원을 공유하거나 주고받지 않는 독립된 코드임을 알 수 있다. 

다른 메서드로 분리해보자 

```java
public void upgradeLevels() {
	
	TransactionStatus status = 
				this.transactionManager.getTransaction(new DefaultTransactionDefinition());	

	try {
		upgradeLevelsInternal();
		this.transactionManger.commit(status);
	} catch (RuntimeException e) {
		this.transactionManger.rollback(status);
		throw e;
	}
}

private void upgradeLevelsInternal() {
	List<User> users = userDao.getAll();
		for (User user : users) {
			if(canUpgradeLevel(user)) {
				upgradeLevel(user);
			}
		}
}
```

### 6.1.2 DI를 이용한 클래스의 분리

UserService에서도 비지니스로직과 관련없는 경계설정 코드를 제외해보자. 코드를 클래스 밖으로 뽑아내자

**DI 적용을 이용한 트랜잭션분리**

UserServiceTest가 현재 클라이언트로써 UserService를 직접 참조하여 사용하고있는데, UserService에서 트랜잭션 경계설정을 빼버리면 트랜잭션 경계설정이 빠진 코드를 사용할 수 밖에 없다. → 그러니까 간접적으로 사용하도록 인터페이스 및 DI를 사용하자??

- before
    
    <img width="402" alt="스크린샷 2023-08-22 오후 8 41 37" src="https://github.com/Hoontudy/Toby-Spring/assets/50127628/4d975875-3d9a-441f-b048-1ed91c7aa5c1">
    
- after
    
    <img width="411" alt="스크린샷 2023-08-22 오후 8 42 00" src="https://github.com/Hoontudy/Toby-Spring/assets/50127628/52fe8bd4-7765-49c2-aa02-733676c564e0">
    

보통 이렇게 사용하려고하는 경우는 다양한 UserService 구현체를 바꿔가면서 사용하려고해서이다. 그런데 꼭 이 이유때문만으로 사용해야하는 것은 아니다. UserService의 구현체를 동시에 사용할 수 있다.

- after2
    
    <img width="501" alt="스크린샷 2023-08-22 오후 8 44 36" src="https://github.com/Hoontudy/Toby-Spring/assets/50127628/b7922494-34be-4a66-b802-04cf31ced876">
    

UserServiceTx는 UserServiceImpl의 기능을 대체하기위한, 즉 UserService의 또다른 비지니스 구현체가 아니다. 단지 트랜잭션 경계설정역할만 하고 실제 비지니스 로직은 UserServiceImpl이 수행하도록 한다.

**UserService 인터페이스 도입**

UserService를 UserServiceImpl로 변경하고, 핵심 메서드만 담은 UserSerivce Interface를 생성한다.

```java
public interface UserService {
	void add(User user);
	void upgradeLevels();
}
```

UserServiceImpl은 UserService를 구현하고, 트랜잭션 경계설정 관련 코드는 제거한다.

```java
public class UserServiceImpl implements UserService {
	UserDao userDao;
	MailSender mailSender;
	
	public void upgradeLevels() {
		List<User> users = userDao.getAll();
			for (User user : users) {
				if(canUpgradeLevel(user)) {
					upgradeLevel(user);
				}
			}
	}
	
	...
}
```

**분리된 트랜잭션 기능**

비지니스 로직 위임기능을 가진 트랜잭션 처리 UserServiceTx를 만든다.

```java
public class UserServiceTx implements UserService {
	UserService userService;

	public void setUserService(UserService userService) {
		this.userService = userService;
	}

	public void add(User user) {
		userService.add(user);
	}

	public void upgradeLevels() {
		userService.upgradeLevels();
	}
}
```

이제 UserServiceTx에 트랜잭션 경계설정 기능을 추가하자

```java
public class UserServiceTx implements UserService {
	UserService userService;
	PlatformTransactionManager transactionManager;

	public void setTransactionManager(TransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

	public void setUserService(UserService userService) {
		this.userService = userService;
	}

	public void add(User user) {
		userService.add(user);
	}

	public void upgradeLevels() {
		TransactionStatus status = 
				this.transactionManager.getTransaction(new DefaultTransactionDefinition());	
		try {
			userService.upgradeLevels();
		
			this.transactionManger.commit(status);
		} catch (RuntimException e) {
			this.transactionManger.rollback(status);
			throw e;
		}
	}
}
```

**트랜잭션 적용을 위한 DI 설정**

이제 마지막으로 설정 파일을 수정하자. 결국 클라이언트인 UserServiceTest는 UserServiceTx를 주입받고, UserService는 UserServiceImpl을 주입받는 아래와 같은 그림이 된다.

<img width="504" alt="스크린샷 2023-08-22 오후 9 01 50" src="https://github.com/Hoontudy/Toby-Spring/assets/50127628/2aa4c9f7-2bcb-48b1-9f70-d0c0f281b560">

```xml
<bean id="userService" class="springbook.user.service.UserServiceTx">
	<property name="transactionManager" ref="transactionManger"/>
	<property name="userService" ref="userServiceImpl"/>
</bean>

<bean id="userServiceImpl" class="springbook.user.service.UserServiceImpl">
	<property name="userDao" ref="userDao" />
	<property name="mailSender" ref="mailSender" />
</bean>
```

~~상기의 xml 빈 설정으로 이제 클라이언트인 UserServiceTest는 UserServiceTx를 사용하게 된다.~~  → 아님 빈을 등록한것 뿐이고 UserServiceTest가 UserServiceTx를 구현체로 사용하도록 해야함 !

**트랜잭션 분리에 따른 테스트 수정**

이제 테스트를 돌려보기 전에, 테스트에 수정이 조금 필요하다. 

@Autowired는 기본적으로 타입이 일치하는 빈을 넣어준다. 그런데 UserService를 구현한 구현체가 UserServiceTx와 UserServiceImpl 두개이다. 이럴때 @Autowired는 **필드 이름**을 이용해 빈을 찾는다. 따라서 설정 파일에서 id가 userService인 빈이 주입된다.

**트랜잭션 경계설정 코드 분리의 장점**

트랜잭션 경계설정 코드 분리의 장점은 무엇인가?

1. UserServiceImpl의 코드를 작성할 때는 트랜잭션 같은 기술적인 내용에 전혀신경쓰지 않아도된다. 언제든지 트랜잭션을 도입할 수 있다.
2. 비지니스 로직에 대한 테스트를 손쉽게 만들어 낼 수 있다.


## 6.2 고립된 단위테스트

테스트할 대상이 크고 복잡하면 테스트를 만들기도 어려울 뿐더러, 테스트가 오류가 나도 그중 어떤것이 오류를 발생했는지 확인하기 어렵다. 따라서 작은 단위의 테스트가 필요하다. 하지만 테스트가 다른 오브젝트 환경에 의존을 하고 있다면 작은 단위 테스트를 작성하는게 어려울 수 있다.

### 6.2.1 복잡한 의존관계 속의 테스트

UserService는 사실 유저의 정보를 관리하는 간단한 오브젝트이나, UserService를 구동하기 위해서는 UserDao, Mail관련 클래스, 트랜잭션을 위한 PlatformTransactionManger등이 필요한 의존관계를 맺고있기때문에 테스트가 어렵다. 심지어 UserDao는 데이터베이스, DataSource등과의 커넥션을 맺어야하기 때문에, 우리는 UserServiceTest를 통해 UserService만 테스트하고싶지만 뒤여 얽혀진 수많은 의존관계가 모두 정상 동작해야 UserService를 Test할 수 있다는 문제를 가지고있다. 

따라서 UserService가 아닌 의존된 오브젝트의 코드를 바꾸거나 연결방법을 수정하거나 해서 발생하는 문제또한 관련없는 UserService가 떠앉게 된다.

### 6.2.2 테스트 대상 오브젝트 고립시키기

**테스트를 위한 UserServiceImpl 고립**

고립된 UserServiceImpl을 하기와 같이 만든다.

<img width="683" alt="스크린샷 2023-08-29 10 21 35" src="https://github.com/Hoontudy/Toby-Spring/assets/50127628/a3180d8d-9e42-4598-bbcf-6673c7299584">

그런데 이전에는 UserService를 테스트하면서 기능이 정상적으로 동작했는지를 데이터베이스에 정상적으로 데이터가 들어갔는지로 확인을 했다. 하지만 이렇게 Mock을 사용하면서 데이터베이스에서 확인할 수 있는 방법은 더이상 사용할 수가 없다. 더더욱 void를 리턴하는 method는 실행 결과를 알 수 없어서 메소드가 정상 수행되었는지 테스트할 방법이 없다.

따라서 UserDao 역할을 수행하면서 UserServiceImpl의 결과를 저장했다가 검증하는데 도움을 주는 Mock오브젝트를 만들 필요가있다.(MockUserDao)

**고립된 단위 테스트 활용**

고립된 단위테스트방법을 UserServiceTest의 upgradeLevels() 테스트에 적용해보자.

```java
//기존 UserServiceTest

@Test
    public void upgradeLevels() throws Exception {
        userDao.deleteAll();
        for(User user : users) userDao.add(user); //DB 데이터세팅

        MockMailSender mockMailSender = new MockMailSender();
        userServiceImpl.setMailSender(mockMailSender); //MockMailSender DI

        userService.upgradeLevels(); //테스트 대상 실행

        checkLevelUpgraded(users.get(0), false); //DB결과 확인
        checkLevelUpgraded(users.get(1), true);
        checkLevelUpgraded(users.get(2), false);
        checkLevelUpgraded(users.get(3), true);
        checkLevelUpgraded(users.get(4), false);

        List<String> request =mockMailSender.getRequest(); //Mock오브젝트로 결과확인
        assertThat(request.size(), is(2));
        assertThat(request.get(0), is(users.get(1).getEmail()));
        assertThat(request.get(1), is(users.get(3).getEmail()));
    }
    
    private void checkLevelUpgraded(User user, boolean upgraded) {
        User userUpdate = userDao.get(user.getId());
        ...
    }
```

여기서 UserDao와 Mock인 MockMailSender의 준비과정과 검증방법을 비교해 볼 수 있는데, UserDao는 실제 DB에 테스트 데이터를 미리 넣어놓고 검증도 데이터를 가져와서 하는 반면, MockMailSender는 Mock데이터를 준비하고 준비된 메서드를 통해 검증한다.

**UserDao 목 테스트**

이제 DB에 의존하고있는 UserDao도 목 오브젝트를 만들어 적용해보자

목 오브젝트는 기본적으로 테스트 대상이 필요한 내용을 제공해주어야하기 때문에 기존에는 어떤 데이터를 주고 받았는지 우선 확인해보자

```java
//UserService에서 userDao를 사용하는 코드
public void upgradeLevels() {
        List<User> users = **userDao**.getAll();
        for (User user : users) {
            if (canUpgradeLevel(user)) {
                upgradeLevel(user);
            }
        }
    }
    
    protected void upgradeLevel(User user) {
        user.upgradeLevel();
        **userDao**.update(user);
        sendUpgradeEMail(user);
    }
```

기존 UserService를 보면 upgradeLevels() 메소드와 해당 메소드에서 사용하는 메소드에서 사용하고있다.

getAll() 메서드를 호출하면 DB에서 유저정보를 가져오는 것처럼 Mock 메서드를 세팅하고 update는 딱히 리턴값이 없으니 빈 메서드로 만들어두어도 된다. (다만 원래 해당 메서드는 유저가 업그레이드 기준을 충족하면 업그레이드하는 메소드라는 점은 기억하자)

해당역할을 하는 MockUserDao 오브젝트를 만들자

```java
public class MockUserDao implements UserDao{
    private List<User> users;
    private List<User> updated = new ArrayList<>();
    
    private MockUserDao(List<user> users) {
        this.users = users;
    }
    
    public List<User> getUpdated() {
        return this.updated;
    } 
    
    public List<User> getAll() {
        return this.users;
    }
    
    public void update(User user) {
        updated.add(user);
    }
    
//사용되지 않도록 Exception 발생시킨다
    public void add(User user) {
        throw new UnsupportedOperationException();
    }
    
    public void deleteAll() {
        throw new UnsupportedOperationException();
    }
    
    public User get(String id) {
        throw new UnsupportedOperationException();
    }
    
    public int getCount() {
        throw new UnsupportedOperationException();
    }
}
```

이렇게 DB에 갔다오는 대신 users와 updated를 둬서 리스트에서 반환해줄 수 있도록 해준다.

그러면 UserServiceTest를 이 MockUserDao를 사용하는 것으로 변경하자

```java
@Test
    public void upgradeLevels() throws Exception {
        UserServiceImpl userServiceImpl = new UserServiceImpl(); //직접 생성 

        MockUserDao mockUserDao = new MockUserDao(this.users); //DI
        userServiceImpl.setUserDao(mockUserDao);

        MockMailSender mockMailSender = new MockMailSender();
        userServiceImpl.setMailSender(mockMailSender);

        userServiceImpl.upgradeLevels(); //테스트 대상 실행

        List<User> updated = mockUserDao.getUpdated();
        assertThat(updated.size(), is(2));
        checkUserAndLevel(updated.get(0), "joytouch", Level.SILVER);
        checkUserAndLevel(updated.get(0), "madnite1", Level.GOLD);

        List<String> request = mockMailSender.getRequest();
        assertThat(request.size(), is(2));
        assertThat(request.get(0), is(users.get(1).getEmail()));
        assertThat(request.get(1), is(users.get(3).getEmail()));
    }

    private void checkUserAndLevel(User updated, String expectedId, Level expectedLevel) {
        assertThat(updated.getId(), is(expectedId));
        assertThat(updated.getLevel(), is(expectedLevel));
    }
```

기존에는 UserService를 사용했다. 하지만 UserSerivce는 많은 의존성을 가지고있어서 테스트에 고립된 클래스가 필요했고 이에따라 Mock을 사용하는 UserServiceImpl을 생성하여 사용했다. 

이렇게 되면 DB에 데이터를 미리 준비할 필요도, 검증시 DB를 다녀와 변경된 데이터를 가져올 필요도 없어진다.

**테스트 수행 성능의 향상**

의존성을 Mock으로 대체한 테스트코드와, 기존대로 전체 의존성을 가져와 테스트하는 경우 테스트 수행 속도가 많이 차이나는 것을 알 수 있다. 의존성을 Mock으로 대체하여 필요한 실제 비지니스 코드만 테스트한 경우가 테스트 수행속도가 훨씬 빠르다.

만약 이것보다 더 복잡하고 실제와 가까운 비지니스코드를 테스트한다면 차이는 더 뚜렷하게 날 것이다. 테스트가 빨라지면 부담감 없이 테스트를 더 자주 돌려볼 수 있게 된다. 목 오브젝트를 만들어야한다는 수고성은 있지만 보상도 충분하다.

### 6.2.3 단위테스트와 통합테스트

단위는 사용자가 정하기 나름이지만, 우선 이 책에서 단위테스트는 외부의 리소스에 의존하지 않고 목 등을 사용하여 고립시켜 테스트하는 것을 단위테스트라고 명명한다. 반면 통합테스트는 한개 이상의 외부 리소스가 참여하는 것을 통합테스트라고 한다.

스프링 생성하는 스프링 컨텍스트를 사용하여 DI 받는 것 또한 통합테스트라고 본다.

그러면 어떠한 경우 단위테스트를 사용하고 어떠한 경우 통합테스트를 사용할까?

- 항상 단위테스트를 먼저 고려한다
- 외부와의 의존관계를 차단하고 목을 이용하도록 테스트를 만든다
- 외부리소스를 사용해야만 가능한 테스트는 통합 테스트로 만든다
- DAO같은경우 고립된 테스트보다는 연동테스트로 만드는 것이 효과적이다
- DAO는 외부리소스를 사용하기에 통합테스트라고 볼 수 있으나, 검증을 잘 해놓으면 DAO를 사용하는 코드는 목으로 대체해서 테스트할 수 있다.
- 통합테스트는 필요하지만 단위테스트를 충분히 거쳤다면 부담이 적어진다
- 단위테스트 만들기가 복잡하다면 처음부터 통합테스트를 고려해본다
- 스프링 컨텍스트를 사용하는 테스트는 통합테스트이다.

여기서 말하는 테스트는 단위, 통합 모두 개발자가 스스로 만드는 테스트이다. 테스트는 코드를 만들자마자 작성하는 것이 작성이해도가 높다. 코드를 작성하면서 테스트는 어떻게 할까 생각하는게 좋다.

### 6.2.4 목 프레임워크

단위 테스트를 만들기 위해서는 스텁이나 목 오브젝트 사용이 필수적이다. 단위테스트가 많은 장점이 있지만, 목 오브젝트를 만드는것이 가장 큰 짐이다.

다행히도, 목 오브젝트를 편리하게 작성하도록 도와주는 다양한 목 오브젝트 지원 프레임워크가 있다.

**Mockito 프레임워크**

직접 만든 목 오브젝트를 사용했던 테스트를 Mockito를 이용하도록 바꾸자

UserDao mockUserDao = mock(UserDao.class);

when(mockUserDao.getAll()).thenReturn(this.users);

verify(mockUserDao, times(2)).update(any(User.class));

→ update() 메소드가 두 번 호출됐는지 확인하라는 의미

결국 UserDao 인터페이스를 구현한 MockUserDao를 만들 필요도 없고 리턴값을 관리하고 있을 필요도 없다.

목 오브젝트 사용은 다음의 네 단계를 거친다.

1. 인터페이스를 이용해 목 오브젝트를 만든다.
2. 목 오브젝트가 리턴할 값이 있으면 이를 지정해준다. 예외를 던지도록 지정해 줄 수도있다.
3. 테스트 대상 오브젝트에 DI해서 목 오브젝트가 테스트 중에 사용되도록 만든다
4. 목 오브젝트의 특정 메소드가 호출됐는지, 어떤 값을 가지고 몇 번 호출됐는지를 검증한다.

```java
@Test
    public void upgradeLevels() throws Exception {
        UserServiceImpl userServiceImpl = new UserServiceImpl(); //직접 생성 

        MockUserDao mockUserDao = mock(UserDao.class);
        userServiceImpl.setUserDao(mockUserDao);
				when(mockUserDao.getAll()).thenReturn(this.users);

        MockMailSender mockMailSender = mock(MailSender.class);
        userServiceImpl.setMailSender(mockMailSender);

        userServiceImpl.upgradeLevels(); //테스트 대상 실행

				verify(mockUserDao, times(2)).update(any(User.class));
				verify(mockUserDao, times(2)).update(any(User.class));
				verify(mockUserDao).update(users.get(1));
        assertThat(users.get(1).getLevle(), is(Level.SILVER));
				verify(mockUserDao).update(users.get(3));
        assertThat(users.get(3).getLevle(), is(Level.GOLD));

    }
```


## 6.3 다이내믹 프록시와 팩토리 빈

### 6.3.1 프록시와 프록시패턴 데코레이터 패턴

클라이언트가 사용하려고하는 실제 대상인척 위장해서 클라이언트의 요청을 받아주는 것을 프록시라고한다.

<img width="649" alt="스크린샷 2023-09-08 16 33 21" src="https://github.com/Hoontudy/Toby-Spring/assets/50127628/60ec868f-e3b5-4300-8c7b-047836c469b4">

프록시를 통해 요청을 위임받아 처리하는 실제 오브젝트를 타깃 또는 실체라고 부른다.

프록시의 특징

- 타깃과 같은 인터페이스를 구현했다는 점
- 프록시가 타깃을 제어할 수 있는 위치에 있다는 점

프록시 종류를 사용 목적에 따라 두가지로 분리 할 수 있다.

- 클라이언트가 타깃에 직접 접근하는 것을 방지하기 위해
- 타깃에 부가적인 기능을 추가하기위해

두 방법은 목적에 따라 디자인 패턴에서는 다른 패턴으로 구분한다.

**데코레이터 패턴**

부가적인 기능을 런타임 시점에 다양하게 적용해주기 위해 사용하는 패턴

프록시 여러개를 사용할 수도 있다. 데코레이터는 위임하는 대상에도 인터페이스로 동작하기 때문에 다음 위임대상이 타겟인지 다음 데코레이터인지 알 수 없다.

```jsx
<!--데코레이터-->
<bean id="userService" class="springbook.user.service.UserServiceTx">
	<property name="transactionManager" ref="transactionManger"/>
	<property name="userService" ref="userServiceImpl"/>
</bean>

<!--타깃-->
<bean id="userServiceImpl" class="springbook.user.service.UserServiceImpl">
	<property name="userDao" ref="userDao" />
	<property name="mailSender" ref="mailSender" />
</bean>
```

여러 데코레이터를 적용할 수도 있고, 중간에 추가 할 수도 있다. 새로운 기능을 추가할 때 유용한 방법이다.

**프록시 패턴**

일반적인 프록시 vs 디자인패턴의 프록시

일반적인 프록시: 클라이언트와 사용 대상 사이에 대리 역할을 맞은 오브젝트를 두는 방법을 총칭

디자인패턴의 프록시: 타깃에 대한 접근방법을 제어하는 목적을 가진 경우를 가리킴

프록시패턴은 타깃에대한 기능을 추가하거나 확장하지 않는다. 당장 타깃 오브젝트가 필요하지 않는 경우 레퍼런스만 필요한경우 실제 타깃 오브젝트가 아닌 프록시만 넘겨주도록 사용할 수 있다. 그러다가 타깃을 이용하려고하면 그 때 타깃 오브젝트를 생성한다. 타깃이 끝끝내 사용되지 않을 수도 있고, 오랜 후에 사용하는 경우 이렇게 생성 시기를 늦추는 것은 얻는 장점이 많다.

이렇게 프록시 패턴은 기능에 대한 추가적인 것은 아무것도 없고 접근에 대한 제어를 컨트롤 하는 패턴이다.

### 6.3.2 다이내믹 프록시

하지만 프록시를 구현하는 것은 귀찮다. 매번 클래스를 만들어야하고 인터페이스의 메소드를 전부 구현해야하기 때문이다. 그리고 위임하는 코드도 작성해야한다.

따라서 자바에서는 java.lang.reflex 패키지안에 프록시를 손쉽게 만들수 있도록 해주는 클래스들이 있다.

**프록시의 구성과 프록시 작성의 문제점**

프록시의 역할은 이렇게 위임과 부가기능작업 두가지로 구분할 수 있다.

```java
public class UserServiceTx implements UserService {
	UserService userService; --> 타깃 오브젝트
	...	

	public void add(User user) { --> 메소드 구현과 위임
		userService.add(user);
	}

	public void upgradeLevels() { --> 메소드 구현
		TransactionStatus status =  --> 부가기능 수행
				this.transactionManager.getTransaction(new DefaultTransactionDefinition());	
		try {

			userService.upgradeLevels(); --> 위임
		
			this.transactionManger.commit(status); --> 부가기능수행
		} catch (RuntimException e) {
			this.transactionManger.rollback(status);
			throw e;
		}
	}
}
```

문제점은 동일하게 두가지가 있다. 

1. 위임하는 코드를 매번 작성해주어야한다. 인터페이스의 메소드를 매번 구현해줘야한다.
2. 부가기능 코드가 중복될 가능성이 많다. 예를들어 상기의 add() 메소드에 트랜잭션 경계설정 부가기능이 적용되어야한다면 트랜잭션관련 동일한 코드가 계속 반복된다.

두번째인 부가기능 코드 중복은 메소드를 빼는등 하여 해결 할 수 있을 듯 하지만, 첫번째의 문제는 해결하기 쉽지 않아보인다. 이때 유용한것이 JDK의 다이내믹 프록시이다.

**리플렉션**

→ 에서 Class 얘기는 왜 나온거?

다이내믹 프록시는 리플렉션 기능을 이용해서 프록시를 만들어준다. 리플렉션은 자바의 코드 자체를 추상화해서 접근하도록 만든것이다.

`String name = “Spring”;`

이 String의 길이를 알고 싶다면 name.length()와 같이 직접 메소드를 호출하는 코드를 만들 수 있다. 반면에 리플렉션 API를 사용할 수도 있는데, java.lang.reflect 하위의 API를 사용할 수 있다. 

`Method lengthMethod = String.class.getMethod(”length”);`

invoke 메소드를 통해 실행도 가능하다.

`int length = lengthMethod.invoke(name);`

하기 코드는 String의 메소드를 직접 호출 한 것과, reflection invoke를 사용하여 호출한 것을 비교한 코드다.

```java
class ReflectionTest {
    @Test
    void invokeMethod() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String name = "Spring";

        //length()
        assertThat(name.length()).isSameAs(6);

        Method lengthMethod = String.class.getMethod("length");
        Integer invoke = (Integer) lengthMethod.invoke(name);
        assertThat((int) invoke).isSameAs(6);

        //charAt()
        assertThat(name.charAt(0)).isSameAs('S');

        Method charAtMethod = String.class.getMethod("charAt", int.class);
        assertThat((char) charAtMethod.invoke(name, 0)).isSameAs('S');
    }
}
```

**프록시 클래스**

다이내믹 프록시를 이용한 프록시를 만들어 보자. 간단한 예시 샘플을 만든다.

```java
//Hello interface
interface Hello {
    String sayHello(String name);
    String sayHi(String name);
    String sayThankYou(String name);
}
```

```java
//타깃 클래스
public class HelloTarget implements Hello{
    @Override
    public String sayHello(String name) {
        return "Hello " + name;
    }

    @Override
    public String sayHi(String name) {
        return "Hi " + name;
    }

    @Override
    public String sayThankYou(String name) {
        return "Thank You " + name;
    }
}
```

Hello 인터페이스를 통해 HelloTarget 오브젝트를 사용하는 클라이언트 역할을 하는 간단한 테스트를 만든다.

```java
@Test
    void simpleProxy() {
        Hello hello = new HelloTarget();
        assertThat(hello.sayHello("Toby")).isSameAs("Hello Toby");
        assertThat(hello.sayHi("Toby")).isSameAs("Hi Toby");
        assertThat(hello.sayThankYou("Toby")).isSameAs("Thank You Toby");
    }
```

이제 Hello 인터페이스를 구현한 프록시를 만들어본다. 프록시는 데코레이터 패턴을 적용해서 타깃 HelloTarget에 부가기능을 추가한다. 타겟의 리턴값을 대문자로 변환해주는 부가기능을 추가한다.

```java
public class HelloUppercase implements Hello{

    Hello hello; //위임할 타깃 클래스의 오브젝트

    public HelloUppercase(Hello hello) {
        this.hello = hello;
    }

    @Override
    public String sayHello(String name) {
        return hello.sayHello(name).toUpperCase();
    }

    @Override
    public String sayHi(String name) {
        return hello.sayHi(name).toUpperCase();
    }

    @Override
    public String sayThankYou(String name) {
        return hello.sayThankYou(name).toUpperCase();
    }
}
```

테스트 코드를 추가해서 프록시가 동작하는지 확인한다.

```java
@Test
    @DisplayName("HelloUppercase 프록시 테스트")
    void helloUppercaseProxyTest() {
        Hello hello = new HelloUppercase(new HelloTarget());
        assertThat(hello.sayHello("Toby")).isEqualTo("HELLO TOBY");
        assertThat(hello.sayHi("Toby")).isEqualTo("HI TOBY");
        assertThat(hello.sayThankYou("Toby")).isEqualTo("THANK YOU TOBY");
    }
```

이 프록시는 상기에서 말했던 일반적인 프록시 적용의 문제점 두가지를 모두가지고있다. 모든 메소드를  구현해 위임하도록 코드를 만들어야하며, 부가기능인 리턴값을 문자로 바꾸는 기능이 모든 메소드에 중복돼서 나타난다.

**다이내믹 프록시 적용**

클래스로 만든 프록시인 HelloUppercase를 다이내믹 프록시를 적용하여 만들어본다.

다이내믹 프록시가 동작하는 방식은 하기 그림과 같다.

<img width="656" alt="스크린샷 2023-09-12 11 00 20" src="https://github.com/Hoontudy/Toby-Spring/assets/50127628/b972f897-496c-4ce7-beea-159713114daf">

다이나믹 프록시는 프록시 팩토리에 의해 런타임시 다이나믹하게 만들어지는 오브젝트이다. 프록시 팩토리에게 인터페이스 정보만 전달해주면 해당 인터페이스를 구현한 클래스의 오브젝트를 자동으로 만들어준다.

다이나믹 프록시가 인터페이스 구현체는 만들어주지만 프록시로서 필요한 부가기능 제공 코드는 직접 작성해야 한다. 부가기능은 프록시가 아닌 InvocationHandler 인터페이스를 구현한 구현체에 남는다.

InvocationHandler는 `public Object invoke(Object proxy, Method method, Object[] args)` 한개의 메서드만 가지고 있다.

다이나믹 프록시는 클라이언트의 모든 요청을 리플렉션으로 변환을 하여 InvocationHandler 구현 오브젝트의 invoke method로 넘긴다. 타깃 인터페이스의 모든 요청의 invoke로 몰리기 때문에 메소드의 중복되는 기능을 효과적으로 제공할 수 있다.

<img width="630" alt="스크린샷 2023-09-19 11 18 35" src="https://github.com/Hoontudy/Toby-Spring/assets/50127628/953261a6-f42a-4c8c-b68d-36f302f49e6f">

그림과 같이 Hello 인터페이스를 제공하면서, 다이내믹 프록시 팩토리에게 다이내믹 프록시를 만들어달라고한다. 그러면 Hello의 모든 인터페이스를 구현한 구현체를 전달받는다. 

또, 다이내믹 프록시에게 InvocationHandler의 구현체를 전달하면, 다이내믹 프록시가 알아서 받는 요청을 모두 invoke에 보내준다.

이제 다이내믹 프록시를 만들어 보자.

우선 다이내믹 프록시에 전달할 InvocationHandler의 구현체를 먼저 만든다. 위의 HelloUppercase와 동일한 기능을 하는 InvocationHandler의 구현체를 만든다. (부가기능 수행)

```java
public class UppercaseHandler implements InvocationHandler {

    Hello target;

    public UppercaseHandler(Hello target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String ret = (String) method.invoke(target, args);
        return ret.toUpperCase();
    }
}
```

다이나믹 프록시로 부터 요청을 전달 받으려면 InvocationHandler를 구현해야하고, 모든 요청은 invoke로 들어오게 된다. 다이나믹 프록시를 통해 요청이 전달이 되면, 리플렉션 API를 이용해 타깃의 메소드를 호출한다. Hello의 모든 메소드가 String 타입이므로 String 타입으로 형변환한다. 추가로 부가적인 기능을 수행하는 코드까지 넣어준다. 리턴 값은 다이나믹 프록시가 전달을 받고, 해당값을 최종적으로 클라이언트에게 전달해준다.

이제 이 InvocationHandler를 사용하는 프록시를 만들어보자. 프록시 생성은 Proxy 클래스의 newProxyInstance 메서드를 사용하면 된다.

```java
Hello proxiedHello = (Hello)Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] {Hello.class},
                new UppercaseHandler(new HelloTarget())
        );
```

두번째 파라미터, 다이나믹 프록시는 두개 이상의 인터페이스를 구현할 수 있기 때문에, 인터페이스의 배열을 사용한다. 마지막 파라미터로는 InvocationHandler를 구현한 구현체를 전달해 준다.

newProxyInstance로 만들어진 프록시 오브젝트는, Hello interface를 구현한 구현체이기 때문에 Hello 클래스로 캐스팅 가능하다.

그런데 이렇게 구현하는데에 장점이 무엇일까? 실제로 구현체를 만들어서 진행하는 것보다 코드의 양이 줄어들지 않은 것 같고, invoke, InvocationHandler등 생소한 클래스의 사용으로 오히려 사용이 까다로워 보인다.

**다이내믹 프록시의 확장**

다이내믹 프록시는 직접 프록시를 만드는 것보다 장점이 있다. 만약 Hello interface가 메소드가 3개가 아니라 30개라면, upper를 적용하는 동일한 코드를 계속 반복해야할 것이다. 하지만 다이내믹 프록시를 사용하면 코드를 수정하지 않아도 된다. invoke에서 동일한 처리를 반복 할 것이다.

지금은 Hello interface의 모든 메서드가 String을 반환하고 있기에, String으로 강제 캐스팅해도 문제가 되는 것이 없었지만, 만약 다른 타입을 리턴할 경우 어떻게 해야할까?

따라서 타입 오브젝트의 리턴값이 String인 경우에만 String으로 캐스팅 해주고 나머지는 타겟 클래스에 그냥 넘겨주자.

또, 어떤 클래스든 상관없이 재사용 할 수 있도록 target도 Object로 수정하자

```java
public class UppercaseHandler implements InvocationHandler {

    Object target;

    public UppercaseHandler(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object ret = method.invoke(target, args);
        if (ret instanceof String) {
            return ((String)ret).toUpperCase();
        }
        return ret;
    }
}
```

만약 메서드에 따라 다르게 부가기능을 적용하고 싶다면, invoke 안에서 커스텀이 가능하다. 아래는 하나의 예시이다.

```java
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object ret = method.invoke(target, args);
        if (ret instanceof String && method.getName().startsWith("say")) {
            return ((String)ret).toUpperCase();
        }
        return ret;
    }
```

### 6.3.3 다이내믹 프록시를 이용한 트랜잭션 부가기능

UserServiceTx를 다이내믹 프록시 방식으로 변경해보자. UserServiceTx는 메소드마다 트랜젝션 기능이 중복되어 기술되어있는 문제점을 가지고 있다. InvocationHandler를 구현한 한개의 TransactionHandler로 처리해 보자

**트랜잭션 InvocationHandler**

```java
public class TransactionHandler implements InvocationHandler {
    private Object target; //어떤 target Object에서 적용할 수 있다
    private PlatformTransactionManager transactionManager;
    private String patter; //트랜잭션 적용 메소드를 구분하기 위한 패턴 

    public void setTarget(Object target) {
        this.target = target;
    }

    public void setTransactionManger(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public void setPatter(String patter) {
        this.patter = patter;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().startsWith(patter)) {
            return invokeTransaction(method, args);
        }

        return method.invoke(method, args);
    }

    private Object invokeTransaction(Method method, Object[] args) throws Throwable {
        TransactionStatus status = this.transactionManager.getTransaction(new DefaultTransactionDefinition());

        try {
            Object ret = method.invoke(target, args);
            this.transactionManager.commit(status);
            return ret;
        } catch (InvocationTargetException e) {
            this.transactionManager.rollback(status);
            throw e.getTargetException();
        }
    }
}
```

**TransactionHandler와 다이내믹 프록시를 이용하는 테스트**

UserServiceTx를 프록시로 사용하는 대신에 다이내믹 프록시를 사용하여 UserServiceTest, upgradeAllOrNothing 메소드를 테스트한다.

```java
@Test
    void upgradeAllOrNothing() throws Exception {
        TransactionHandler txHandler = new TransactionHandler();
        txHandler.setTarget(testUserService);
        txHandler.setTransactionManger(transactionManager);
        txHandler.setPatter("upgradeLevels");
        UserService userService = (UserService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{UserService.class}, txHandler);
    }
```
