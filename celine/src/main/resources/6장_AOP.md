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

### 6.3.4 다이내믹 프록시를 위한 팩토리 빈

이제 만들어진 TransactionHandler와 다이내믹 프록시를 스프링 DI를 통해 사용할 수 있도록 한다. 그런데 다이내믹 프록시는 스프링 빈으로 등록할 방법이 없다. 스프링은 빈을 일반적으로 **지정된 클래스**를 가지고 리플렉션을 사용해 오브젝트를 만든다. Class의 new Instance() 메소드는 파라미터가 없는 기본생성자로 생성된 클래스를 돌려주는 리플렉션 API이다. 

`Date now = (Date) Class.forName("java.util.Date").newInstance();`

문제는 다이나믹 프록시는 이러한 방법으로 프록시 오브젝트를 생성하지 않는다.

→ 왜 알 수 없지? 프록시는 Proxy 클래스의 newProxyInstance()를 통해서만 만들 수 있다.

**팩토리 빈**

사실 스프링은 클래스 정보를 가지고 디폴트 생성자를 통해 빈을 만드는 방법 외 다른 방법으로 빈을 만들 수 있는 방법을 제공한다.

팩토리 빈을 구현한 클래스를 스프링빈으로 등록 할 수 있다. 

```java
public interface FactoryBean<T> {
	String OBJECT_TYPE_ATTRIBUTE = "factoryBeanObjectType";
	@Nullable
	T getObject() throws Exception;
	@Nullable
	Class<?> getObjectType();
	default boolean isSingleton() {
		return true;
	}
}
```

스프링 빈에 등록하고 싶은 예제 클래스를 하나 만들어보자. 하기 Message 클래스는 생성자를 통해 오브젝트를 생성 할 수 없다. static 메서드를 통해 만들어야한다.

```java
public class Message {
    String text;

    private Message(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static Message newMessage(String text) {
        return new Message(text);
    }
}
```

그래서 다음과 같이 사용할 수 없다.

`<bean id="m" class="springbook.learningtest.spring.factorybean.Message"/>`

사실 스프링은 private으로 선언된 생성자라도 리플렉션을 통해서 오브젝트를 만들어 줄 수 있는데, 리플렉션은 private 접근이 가능하기 때문이다. 하지만, 생성자를 private으로 막아놓고 static을 통해 오브젝트를 생성하도록 유도한 것은 이유가 있을것이며, 이것을 무시하고 private 생성자로 오브젝트를 생성하는 것은 위험하며 제대로 동작하지 않을 가능성이 높다.

Message 오브젝트를 만들어주는 팩토리 빈 클래스를 만들어보자

```java
public class MessageFactoryBean implements FactoryBean<Message> {
    String text;

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public Message getObject() throws Exception {
        return Message.newMessage(this.text);
    }

    @Override
    public Class<?> getObjectType() {
        return Message.class;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }
}
```

스프링은 FactoryBean 클래스를 구현한 클래스가 빈으로 등록이 되면, 클래스의 getObject()를 통해서 오브젝트를 가져오고 해당 오브젝트를 빈으로 사용한다.

**팩토리 빈의 설정 방법**

```xml
<bean id="message" class="springbook.learningtest.spring.factoryBean.MessageFactoryBean">
	<property name="text" value="Factory Bean" />
</bean>
```

여기서 반환되는 빈타입은 클래스 어트리뷰트에 선언된 MessageFactoryBean가 아닌 Message라는 점이다. 반환되는 타입은 MessageFactoryBean의 getObjectType() 메소드가 돌려주는 타입으로 결정된다.

정말 그런지 하기의 테스트를 통해서 확인해보자

```java
@ContextConfiguration
class FactoryBeanTest {
    @Autowired
    ApplicationContext context;
    
    @Test
    void getMessageFromFactoryBean() {
        Object message = context.getBean("message");
        assertThat(message).isExactlyInstanceOf(Message.class);
        assertThat(((Message)message).getText()).isEqualTo("Factory Bean");
    }
}
```

팩토리 빈 자체를 가져오고 싶다면 하기와 같이 빈 이름 앞에 &를 붙여주면 된다.

```java
    @Test
    void getFactoryBean() {
        Object factory = context.getBean("&message");
        assertThat(factory).isExactlyInstanceOf(MessageFactoryBean.class);
    }
```

**다이내믹 프록시를 만들어주는 팩토리 빈**

**트랜잭션 프록시 팩토리 빈**

```java
public class TxProxyFactoryBean implements FactoryBean<Object> {
    Object target;
    PlatformTransactionManager transactionManager;
    String pattern;
    Class<?> serviceInterface;

    public void setTarget(Object target) {
        this.target = target;
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setServiceInterface(Class<?> serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    @Override
    public Object getObject() throws Exception {
        TransactionHandler txHandler = new TransactionHandler();
        txHandler.setTarget(target);
        txHandler.setTransactionManger(transactionManager);
        txHandler.setPatter(pattern);
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{serviceInterface}, txHandler);
    }

    @Override
    public Class<?> getObjectType() {
        return serviceInterface;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }
}
```

**트랜잭션 프록시 팩토리 빈 테스트**

```java
public class UserServiceTest {
    @Autowired
    ApplicationContext context;

    @Test
    @DirtiesContext
    public void upgradeAllOrNothing() {
        TestUserService testUserService = new TestUserService(users.get(3).getId());
        testUserService.setUserDao(userDao);
        testUserService.setMailSender(mailSender);

        TxProxyFactoryBean txProxyFactoryBean = context.getBean("&userService", TxProxyFactoryBean.class);

        txProxyFactoryBean.setTarget(testUserService);
        UserService txUserService = (UserService) txProxyFactoryBean.getObject();
        
        userDao.deleteAll();
        for(User user : users) userDao.add(user);
        
        try {
            txUserService.upgradeLevels();
            fail("TestUserServiceException expected")
        } catch (TestUserServiceException e) {
            
        }
        
        checkLevelUpgraded(users.get(1), false);
    }
}
```

### 6.3.5 프록시 팩토리 빈 방식의 장점과 한계

다이나믹 프록시를 생성해주는 팩토리 빈을 사용하는 것에는 많은 장점이 있다.

**프록시 팩토리 빈의 재활용**

TxProxyFactoryBean은 다양한 타겟을(Object)를 담을 수 있기때문에 코드의 수정없이 다양한 클래스에 활용 할 수 있다. 타깃 오브젝트를 프로퍼티로 설정해서 xml 빈 설정만 해주면 된다. 여러개를 등록해도 된다. 왜냐하면 생성되는 빈은 팩토리 빈 자체가 아닌 타겟의 인터페이스 타입과 일치하기 때문이다.

예를들어 UserService 외에 트랜젝션을 적용해야하는 Service가 있다고할 때 트랜젝션 적용 전 후를 비교해 보자

```xml
<!-- 전 --> 
<bean id="coreService" class="complex.module.CoreServiceImpl">
	<property name="coreDao" ref="coreDao" />
</bean>
```

```xml
<!-- 후 --> 
<bean id="coreServiceTarget" class="complex.module.CoreServiceImpl">
	<property name="coreDao" ref="coreDao" />
</bean>

<bean id="coreService" class="springbook.service.TxProxyFactoryBean">
	<property name="target" ref="coreServiceTarget" />
	<property name="transactionManager" ref="transactionManager" />
	<property name="pattern" value="" />
	<property name="serviceInterface" value="complex.module.CoreService" />
</bean>
```

사용하는 클라이언트는 코드의 수정 하나 없이 다이내믹 프록시를 이용한 트랜잭션이 적용된 coreService를 사용할 수 있다.

<img width="646" alt="스크린샷 2023-09-26 18 08 39" src="https://github.com/smartmediarep/KISS/assets/50127628/440e81d8-4b40-491a-9335-867d2da7d9f7">


**프록시 팩토리 빈 방식의 장점**

지금까지 확인한 프록시 팩토리 빈은 이전에 말했던 프록시 데코레이트 패턴의 두가지 문제점인, 1. 프록시를 만드려고 인터페이스를 매번 만들어 메소드를 모두 구현하는 문제, 2. 중복되는 데코레이트의 코드를 반복하여 작성해야하는 문제를 해결해 준다.

프록시 팩토리 빈을 사용하면 수많은 Target에 적용 할 수 있고, 하나의 핸들러를 구현하는 것만으로도 (invoke를 통해) 동일반복되는 코드의 구현도 없앨 수 있다. DI 사용하는 것까지 추가한다면 번거로운 다이내믹 프록시 생성코드도 없앨 수 있다.

**프록시 팩토리 빈의 한계**

하지만 이런 프록시 팩토리 빈에도 한계가 있다. invoke를 통한 중복제거는 클래스가 아닌 메소드 단위로 일어난다. 전체 클래스의 권한 체크나 동일하게 트랜젝션을 적용하는 것은 불가하다. 그렇다면 적용하고자하는 Service마다 중복된 xml 설정을 해주어야한다. (xml 설정 코드의 양이 늘어난다.)

여러개의 부가기능을 추가하고 싶을 때도 문제다. 지금은 Transaction 코드만 적용했지만, 보안, 기능검사 등등의 프록시를 추가하고 싶다면? 추가하고싶은 FactoryBean 설정코드 또한 늘어날 것이다. (xml 설정 코드의 양이 늘어난다.)

이로써 xml코드가 방대하게 늘어나고 오타 등 실수할 여지가 너무 늘어난다. 중복 설정코드, 비슷한 설정 코드도 많아진다.

또 한가지 문제점은 TransactionHandler가 Target을 프로퍼티로 가지고있기때문에, 타겟에 마다 TransactionHandler를 생성해주어야한다. 타깃이 다르다는 이유로 TransactionHandler가 많아지고, 또 다른 부가기능의 Handler가 생긴다면 동일하게 중복이 늘어난다.

## 6.4 스프링의 프록시 팩토리 빈

기존 코드의 수정없이 트랜잭션 부가기능을 추가할 수 있는 다양한 방법을 살펴봤다면, 스프링에서는 어떠한 해결책을 제시하는지 확인해보자

### 6.4.1 ProxyFactoryBean
스프링의 ProxyFactoryBean은 프록시를 생성해서 빈 오브젝트로 등록하게 해주는 팩토리 빈이다. 기존의 TxProxyFactoryBean과 다른점은 
ProxyFactoryBean은 프록시를 생성하는 작업만을 담당하고 부가기능은 포함하지 않는다는 것이다.
ProxyFactoryBean이 생성하는 프록시에서 사용할 부가기능은 MethodInterceptor 인터페이스를 구현해서 만든다.
MethodInterceptor의 invoke는 기존의 InvocationHandler와 다르게 타깃을 전달 받기때문에 타깃을 가지고있을 필요가 없다.
따라서 범용적으로 여러 프록시에서 공통으로 사용할 수 있고, 싱글통 빈으로 등록 가능하다.

앞에서 만들었던 다이내믹 프록시 테스트를 스프링의 ProxyFactoryBean을 이용하도록 수정한다.

``` java
    @Test
    void simpleProxy() {
            ...
            Hello proxiedHello = (Hello) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class[]{Hello.class},
            new UppercaseHandler(new HelloTarget()));
            ...
        }
        
    @Test
    void proxyFactoryBean() {
        ProxyFactoryBean pfBean = new ProxyFactoryBean();
        pfBean.setTarget(new HelloTarget()); // 타깃 설정 
        pfBean.addAdvice(new UppercaseAdvice()); // 부기가능 advice 설정
        Hello proxiedHello = (Hello) pfBean.getObject(); //생성된 proxy get

        assertThat(proxiedHello.sayHello("Toby")).isEqualTo("Hello Toby");
        assertThat(proxiedHello.sayHi("Toby")).isEqualTo("Hi Toby");
        assertThat(proxiedHello.sayThankYou("Toby")).isEqualTo("Thank You Toby");
    }
    
    static class UppercaseAdvice implements MethodInterceptor {

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            String ret = (String) invocation.proceed();
            return ret.toUpperCase();
        }
    }
```

**어드바이스: 타깃이 필요 없는 순수한 부가기능**

MethodInterceptor 오브젝트를 추가하는 메소드 이름은 addMethodInterceptor가 아니라 addAdvice이다. 
MethodInterceptor는 Advice 인터페이스를 상속하고 있는 서브인터페이스이기 때문이다.
MethodInterceptor처럼 타깃 오브젝트에 적용하는 부가기능을 담은 오브젝트를 스프링에서는 어드바이스라고 부른다. 
스프링에서는 단순히 메소드 실행을 가로채는 방식 외에도 부가기능을 추가할 수 있는 다른 방식을 제공한다.

추가로 ProxyFactoryBean을 사용하면서 Hello 인터페이스를 제공해주는 부분이 코드에서 사라졌다. ProxyFactoryBean은 제공받은 타깃 오브젝트를 사용해서 타깃 오브젝트가
구현하고 있는 인터페이스 정보를 알아낸다.

어드바이스는 타깃 오브젝트에 종속되지 않는!! 순수한 부가기능을 담은 오브젝트이다. (TransactionHandler 와는 다르게)

**포인트컷: 부가기능 적용 대상 메소드 선정 방법**

기존 InvocationHandler를 직접 구현했을 때 부가기능 적용 외에도 pattern을 전달받아 부가기능 적용 대상 메서드를 선별하는 작업을 했다.
ProxyFactoryBean과 MethodInterceptor에서는 어떻게 동작할까?
스프링에서는 메소드 선정 알고리즘을 담은 오브젝트를 포인트컷이라고 부르며 기존 InvocationHandler를 구현한 구현체에서 직접 pattern을 가지고 구별했던 것을
클래스 단위로 빼낸다.

어드바이스, 포인트컷 두가지 모두 빈으로 등록하여 여러 프록시에서 공유가능하도록 하고, 프록시에 DI로 주입되어 사용된다.

프록시는 클라이언트로부터 요청을 받으면 우선 
1. 포인트 컷에게 부가기능을 부여할 메소드인지 확인해 달라고 요청한다. 포인트컷은 Pointcut 인터페이스를 구현해서 만들면된다.
2. 부가기능을 적용할 대상 메소드인지 확인하면 MethodInterceptor 타입의 어드바이스를 호출한다. MethodInterceptor는 타깃을 가지고있지 않으므로(범용 빈이므로) 타깃 메소드 호출이 필요하면 프록시로부터 전달받은 MethodInvocation 타입 콜백 오브젝트의 proceed() 메소드를 호출하기만 하면된다.
3. 실제 타깃 오브젝트의 레퍼런스를 갖고있고 타깃 메소드를 직접 호출하는 것은 Invocation 콜백의 역할이다.

어드바이스와 포인트컷까지 적용되는 학습 테스트를 만들어본다. 스프링에서는 Pointcut 인터페이스를 구현한 다양한 pointcut 구현체가 있기때문에 활용해도 좋다.

``` java
    @Test
    void pointcutAdvisor() {
        ProxyFactoryBean pfBean = new ProxyFactoryBean();
        pfBean.setTarget(new HelloTarget());

        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedName("sayH*");

        pfBean.addAdvisor(new DefaultPointcutAdvisor(pointcut, new UppercaseAdvice()));

        Hello proxiedHello = (Hello) pfBean.getObject();

        assertThat(proxiedHello.sayHello("Toby")).isEqualTo("HELLO TOBY");
        assertThat(proxiedHello.sayHi("Toby")).isEqualTo("HI TOBY");
        assertThat(proxiedHello.sayThankYou("Toby")).isEqualTo("Thank You Toby");
    }
```

포인트 컷이 필요없을 때는 addAdvice() 메소드를 호출해서 어드바이스만 등록했다. 하지만 포인트컷이 있을 때는 addAdvisor()로 포인트컷과 어드바이저를 묶어서 등록한다.
왜냐하면 여러 어드바이스와 포인트컷이 등록되면 어느 어드바이스 에 어떤 포인트컷이 사용되어야할지 명확해야하기 때문이다.
이렇게 어드바이스와 포인트컷을 묶은 것을 어드바이저라고 부른다.

**어드바이저 = 포인트컷(메소드 선정 알고리즘) + 어드바이스(부가기능)**

### 6.4.2 ProxyFactoryBean 적용
TxProxyFactoryBean을 스프링이 제공하는 ProxyFactoryBean을 이용하도록 수정하자

**TransactionAdvice**

부가기능을 담당하는 어드바이스는 Advice의 서브인터페이스인 MethodIntercepor를 구현해서 만든다. TransactionHandler에서 타깃과 메소드 선정부분을 제거해주면 된다.
```java

public class TransactionAdvice implements MethodInterceptor {
    PlatformTransactionManager transactionManager;

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        TransactionStatus status = this.transactionManager.getTransaction(new DefaultTransactionDefinition());

        try {
            Object ret = invocation.proceed();
            this.transactionManager.commit(status);
            return ret;
        } catch (RuntimeException e) {
            this.transactionManager.rollback(status);
            throw e;
        }
    }
}
```
JDK의 InvocationHandler를 이용해서 만들었을 때 보다 코드가 간결하다.

**스프링 XML 설정파일**

학습 테스트에 직접 DI해서 사용했던 코드를 단지 XML 설정으로 바꿔주기만 하면 된다.
어드바이스를 먼저 등록한다.
```xml
<bean id="transactionAdvice" class="springbook.user.service.TransactionAdvice">
  <property name="transactionManager" ref="transactionManager" />
</bean>
```

다음은 포인트컷 빈을 등록하자. 포인트컷 클래스는 스프링이 제공하는 클래스를 사용하므로 빈 설정만 해주면 된다.
```xml
<bean id="transactionPointcut" class="org.springframework.aop.support.NameMatchMethodPointcut">
  <property name="mappedName" value="upgrade*" />
</bean>
```

이제 어드바이스와 포인트컷을 담을 어드바이저를 등록한다.
```xml
<bean id="transactionAdvisor" class="org.springframework.aop.support.DefaultPoincutAdvisor">
  <property name="advice" ref="transactionAdvice"/>
  <property name="pointcut" ref="transactionPointcut"/>
</bean>
```

이제 최종적으로 ProxyFactoryBean을 등록해준다. property로 타깃과 어드바이저를 등록한다.
```xml
<bean id="userService" class="org.springframework.aop.framework.ProxyFactoryBean">
  <property name="target" ref="userServiceImpl" />
  <property name="interceptorNames">
    <list>
      <value>transactionAdvisor</value>
    </list>
  </property>
</bean>
```
어드바이저는 interceptorNames property를 통해 넣는다. 프로퍼티 이름이 advisor가 아닌 이유는 어드바이스와 어드바이저를 혼합해서 넣을 수 있기 때문이다.
value 태그에는 어드바이저나 어드바이스를 넣을 수 있다. 

**테스트**



**어드바이스와 포인트컷의 재사용**

ProxyFactoryBean은 스프링의 DI와 템플릿/콜백 패턴, 서비스 추상화 등의 기법이 모두 적용된 것이다. 따라서 여러 프록시가 공유할 수 있는 어드바이스와 포인트컷으로 확장 및 분리 할 수 있다.
이제 UserService 외에 새로운 비즈니스 로직을 담은 서비스 클래스가 생겨도 동일하게 어드바이스와 포인트컷을 활용할 수 있다.


-> 그런데 여기서 왜 그림에서 ProxyFactoryBean을 여러개 만들어주지?
-> Target을 지정해주니까 ProxyFactoryBean에
-> 결국 부가기능과 pattern 확인을 위한 두개의 기능이 빈 등록되어 범용사용이 가능한 것임


## 6.5 스프링 AOP

### 6.5.1 자동 프록시 생성

**중복 문제의 접근 방법**

ProxyFactoryBean을 설정하는 XML의 중복을 없애고싶다. 매번 타깃과 어드바이저를 XML에 등록하는 중복방법을 없애고싶다.

**빈 후처리기를 이용한 자동 프록시 생성기**

빈 후처리기를 스프링에 적용하는 방법은, 빈 후처리기를 빈으로 등록하면 된다. 빈 후처리기가 등록되어있으면, 빈 오브젝트가 생성될 때 마다 빈 후처리기에
보내져서 후 처리 작업이 수행된다. 이를 잘 이용하면 스프링이 생성하는 빈 오브젝트의 일부를 프록시로 포장하거나 프록시 빈으로 대신 등록할 수도있다.
빈 오브젝트가 생성되면 빈 후처리기에 빈이 보내지고 빈 후처리기는 등록된 어드바이저의 포인트컷을 이용해 전달받은 빈이 프록시 적용 대상인지 확인한다.
프록시 적용 대상이면 내장된 프록시 생성기에게 현재 빈에 대한 프록시를 만들게 하고

![](../../../../../../../../../var/folders/50/7ndqz7bx4dv6bnkwf1pydg780000gn/T/TemporaryItems/NSIRD_screencaptureui_2rxouv/스크린샷 2023-10-18 11.19.28.png)

**확장된 포인트컷**

이제까지 포인트컷은 타깃 오브젝트의 메소드중에 어떤 메소드에 부가기능을 추가할지를 선별하는 역할을 한다고했는데, 위에서는 어떤 빈에 프록시를 적용할지를 선택한다는 식으로
설명하고 있다. 어떤 말일까?

Pointcut 인터페이스는 클래스 필터와 메소드 매처 두 가지를 돌려주는 메소드를 가지고 있다.
```java
public interface Pointcut {
    ClassFilter getClassFilter(); // 프록시를 적용할 클래스인지 확인해준다.
    MethodMatcher getMethodMatcher(); // 어드바이스를 적용할 메서드인지 확인해준다.
}
```

여태까지는 Method만 판별하면 됐기 때문에 MethodMatcher를 사용하였다. Pointcut의 두 기능을 모두 사용한다면, 먼저 프록시를 적용할 클래스인지 판단하고 나서,
적용 대상 클래스인 경우에는 어드바이스를 적용할 메소드인지 확인하는 식으로 동작한다.

따라서 모든 빈에 대해 프록시 자동 적용 대상을 선별해야하는 빈 후처리기 클래스와 메소드 선정 알고리즘을 모두 갖고 있는 Pointcut이 필요하다. 

**포인트컷 테스트**

NameMatchMethodPointcut은 모든 클래스를 통과시켜버리기 때문에, 클래스를 확장해서 클래스도 고를 수 있게하고, 포인트컷을 적용한 ProxyFactoryBean으로 프록시를 만들도록 해서 
어드바이스가 적용되는지 아닌지 확인해보겠다.

``` java
 @Test
    void classNamePointcutAdvisor() {
        NameMatchMethodPointcut classMethodPointcut = new NameMatchMethodPointcut() {
            @Override
            public ClassFilter getClassFilter() {
                return new ClassFilter() {
                    @Override
                    public boolean matches(Class<?> clazz) {
                        return clazz.getSimpleName().startsWith("HelloT");
                    }
                };
            }
        };
        classMethodPointcut.setMappedName("sayH*");

        //테스트
        checkAdviced(new HelloTarget(), classMethodPointcut, true);

        class HelloWorld extends HelloTarget {}
        checkAdviced(new HelloWorld(), classMethodPointcut, false);

        class HelloToby extends HelloTarget {}
        checkAdviced(new HelloToby(), classMethodPointcut, true);

    }

    private void checkAdviced(Object target, Pointcut pointcut, boolean adviced) {
        ProxyFactoryBean pfBean = new ProxyFactoryBean();
        pfBean.setTarget(target);
        pfBean.addAdvisor(new DefaultPointcutAdvisor(pointcut, new UppercaseAdvice()));
        Hello proxiedHello = (Hello) pfBean.getObject();
        
        if (adviced) {
            assertThat(proxiedHello.sayHello("Toby")).isEqualTo("HELLO TOBY");
            assertThat(proxiedHello.sayHi("Toby")).isEqualTo("HI TOBY");
            assertThat(proxiedHello.sayThankYou("Toby")).isEqualTo("Thank You TOBY");
        } else {
            assertThat(proxiedHello.sayHello("Toby")).isEqualTo("Hello Toby");
            assertThat(proxiedHello.sayHi("Toby")).isEqualTo("Hi Toby");
            assertThat(proxiedHello.sayThankYou("Toby")).isEqualTo("Thank You Toby");
        }
    }
```

NameMatchMethodPointcut를 확장했고 모든 클래스를 통과시켰던 getClassFilter() 메소드를 오버라이드하여 클래스명이 HelloT로 시작하는 클래스만을 선정해주는 필터로 만들었다.
이름이 다른 HelloTarget을 상속한 세개의 클래스를 선언하고 클래스에 모두 동일한 포인트컷을 적용하였다. 메소드 선정기준은 이전과 동일한 코드를 사용했다.
그런데 클래스 선정기준에서부터 HelloWorld 클래서는 탈락하기 때문에 메소드 선정기준조차 적용되지 않는다.

### 6.5.2 DefaultAdvisorAutoProxyCreator의 적용
테스트 코드를 만들었으니 실제 적용해보자

**클래스 필터를 적용한 포인트컷 작성**

만들어야할 클래스는 위 테스트에서 살짝 보았듯 NameMatchMethodPoincut이다. 상속받아 ClassFilter를 수정하자

```java
public class NameMatchClassMethodPointcut extends NameMatchMethodPointcut {
    @Override
    public void setMappedName(String mappedClassName) {
        this.setClassFilter(new SimpleClassFilter(mappedClassName));
    }
    
    static class SimpleClassFilter implements ClassFilter {
        String mappedName;
        
        private SimpleClassFilter(String mappedName) {
            this.mappedName = mappedName;
        }

        @Override
        public boolean matches(Class<?> clazz) {
            return PatternMatchUtils.simpleMatch(mappedName, clazz.getSimpleName());
        }
    }
}
```

**어드바이저를 이용하는 자동 프록시 생성기 등록**

자동 프록시 생성기인 DefaultAdvisorAutoProxyCreator는 등록된 빈 중에서 Advisor 인터페이스를 구현한 것들을 모두 찾고, 생성되는 모든 빈에 대해
어드바이저의 포인트컷을 적용해보면서 프록시 적용 대상을 선정한다. 빈 클래스가 프록시 선정 대상이라면, 프록시를 만들어 원래 빈 오브젝트랑 바꿔치기한다.
따라서 타깃 빈에 의존하는 것들은 타깃 빈 대신 프록시를 DI 받는다.
DefaultAdvisorAutoProxyCreator는 하기 한줄로 등록한다.
```xml
<bean class="org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator"/>
```
다른 빈에서 참조되지 않는다면 id를 등록할 필요가 없다.


**포인트컷 등록**

기존 포인트컷 설정을 삭제하고 새로운 포인트컷 설정을 등록한다.
```xml
<bean id="transactionPointcut" class="springbook.service.NameMatchClassMethodPointcut">
  <property name="mappedClassName" value="*ServiceImpl"/> <!-- 클래스 이름 패턴 -->
  <property name="mappedName" value="upgrade*"/> <!-- 메소드 이름 패턴 -->
</bean>
```

**어드바이스와 어드바이저**

transactionAdvice 어드바이스 빈 설정은 수정할 것이 없고, 어드바이저인 transactionAdvisor 빈도 수정할 필요가 없다.
다만 사용되는 방법이 바뀌었는데, ProxyFactoryBean을 등록한것처럼 명시적으로 어드바이저를 DI하지 않는다. 
앞에서 등록한 DefaultAdvisorAutoProxyCreator에 의해서 자동으로 어드바이저가 수집된다.

**ProxyFactoryBean 제거와 서비스 빈의 원상복구**

FactoryBean의 생성을 위해 사용했던 userSerivce id를 이제 다시 되찾을 수 있다. \
프록시를 사용하기 전의 상태로 돌아 왔다.
```xml
<bean id="userService" class="springbook.user.service.UserServiceImpl">
	<property name="userDao" ref="userDao" />
	<property name="mailSender" ref="mailSender" />
</bean>
```

**자동 프록시 생성기를 사용하는 테스트**

이전에는 ProxyFactoryBean이 있기 때문에 해당 빈을 가져와서 테스트에서 테스트용 클래스로 바꿔치기 했지만, 이제는 더이상
ProxyFactoryBean이 등록되어있지 않다. 자동 프록시 생성기가 등록되어있기 때문이다. \
따라서 테스트를 위한 빈을 등록해줘야한다. 

TestUserService 클래스를 이제는 빈으로 직접 등록해서 사용한다. 고려해야 할 점 1, 2는 하기와 같이 처리한다.
1. TestUserService는 UserServiceTest 클래스 내부에 정의된 static 클래스다
2. 포인트 컷이 트랜잭션 어드바이스를 적용해주는 대상 클래스 이름 패턴이 *ServiceImpl이다
   3. 따라서 TestUserService 클래스는 빈으로 등록해도 포인트컷이 프록시 적용 대상으로 선정해주지 않는다.

해결법
1. static 클래스 자체는 빈으로 등록되는 데 아무런 문제가 없다.
2. 클래스 이름이 포인트컷이 선정해 줄 수 있는 ~ServiceImpl로 변경하자


```xml
<bean id="testUserService" class="springbook.user.service.UserServiceTest$TestUserServiceImpl" parent="userService"/>
```
TestUserServiceImpl을 빈으로 등록할 수 있는데 static 멤버클래스이므로 $를 사용해서 지정해준다. \
또 parent값을 사용하면 다른 빈 설정의 내용을 상속받을 수 있다. parent="userService"라고하면 userService 빈의 모든 설정을
그대로 가져와서 사용하겠다는 뜻이다.

결론적으로 테스트 코드 자체는 단순해졌지만, 테스트의 내용을 이해하려면 설정파일의 DI 정보까지 참고해야 하니
테스트의 내용을 이해하기는 조금 어려워졌다. 


**자동생성 프록시 확인**
지금까지 트랜잭션 어드바이스를 적용한 프록시 자동생성기를 빈 후처리기 메커니즘을 통해 적용했다. \
로직에 따라 하기 최소 2가지를 확인해보아야한다.
1. 필요한 빈에 트랜잭션 부가기능이 적용되었는지
2. 아무빈에나 트랜잭션 부가기능이 적용된것은 아닌지
   3. 클래스 필터가 제대로 동작하는지를 확인해본다.
      4. 이전 테스트에서 적용된 클래스의 이름을 변경해서 적용되지 않으면, 정상동작하고 있는 것!


### 6.5.3 포인트컷 표현식을 이용한 포인트 컷
이전까지는 클래스나 메소드의 이름을 가지고 포인트 컷을 적용할지 아닐지를 구분했다. 
하지만 더 다양하고 세밀한 방법으로도 포인트 컷을 적용할 수 있다. 스프링에서는 정규식같은 것을 제공해서 간편하게 포인트 컷을 적용하도록 지원한다. 

**포인트컷 표현식**
포인트컷 표현식을 지원하는 포인트컷을 적용하려면 AspectJExpressionPointcut 클래스를 사용하면 된다.
AspectJExpressionPointcut은 정규표현식을 사용해서 클래스, 메소드 패턴을 한꺼번에 적용하도록 할 수 있다.

학습테스트로 표현식의 사용방법을 살펴보자
```java
public class Target implements TargetInterface{
    @Override
    public void hello() {}

    @Override
    public void hello(String a) {}

    @Override
    public int minus(int a, int b) throws RuntimeException { return 0; }

    @Override
    public int plus(int a, int b) { return 0;}
    public void method() {}
}
```
상기 메소드들에서 원하는 메소드만 선정하는 방법을 알아본다. 클래스 선정기능을 알아보기 위해 클래스도 하나 더 추가한다.
```java
public class Bean {
    public void method() throws RuntimeException {
        
    }
}
```
이제 두 개의 클래스와 총 6개의 메소드를 대상으로 포인트컷 표현식을 적용해보자

**포인트컷 표현식 문법**
AspectJ 포인트컷 표현식은 포인트컷 지시자를 이용해 작성한다. 포인트컷 지시자 중에서 가장 대표적으로 사용되는 것은 execution()이다.
포인트컷 표현식의 문법구조는 기본적으로 다음과 같다.

[]괄호는 옵션항목이기 때문에 생략이 가능하다는 의미이며, |는 OR 조건이다.
![](../../../../../../../../../var/folders/50/7ndqz7bx4dv6bnkwf1pydg780000gn/T/TemporaryItems/NSIRD_screencaptureui_vLYACB/스크린샷 2023-11-02 10.34.18.png)
복잡해보이지만, 메소드의 풀 시그니처를 문자열로 비교하는 개념이라고 생각하면 간단하다.

`System.out.println(Target.class.getMethod("minus", int.class, int.class))`
를 출력한 결과를 보면 이해하기 쉽다. 

`public int springbook.learningtest.spring.pointcut.Target.minus(int,int) throws java.lang.RuntimeException`

- pulbic : 생략가능
- int : 필수항목. 혹은 *를 써서 모든 타입을 선택할 수 있다.
- springbook.learningtest.spring.pointcut.Target : 패키지 및 클래스의 타입패턴. 생략가능. 
  - 패키지이름과 클래스 또는 인터페이스 이름에 *를 사용할 수 있다. 또 '..'를 사용하면 한 번에 여러개의 패키지를 선택할 수 있다. 
- minus : 메소드 이름 패턴. 필수항목. 마찬가지로 *를 써서 모든 타입을 선택할 수 있다.
- (int, int) : 메소드 파라마터 타입. 필수항목. 파라미터가 없는 메소드를 지정하고 싶다면 ()로 적고, 타입과 개수에 상관없이 모두 다 허용하게 한다면 '..'를 넣으면 된다. '..'를 이용해서 뒷부분의 파라미터 조건만 생략할 수도 있다.
- throws java.lang.RuntimeException: 예외 이름에 대한 타입 패턴. 생략 가능

Target 클래스의 minus() 메소드만 선정해주는 포인트컷 표현식을 만들고 이를 검증하는 테스트를 작성해보자.
```java
public class PointcutExpressionTest {
    @Test
    public void methodSignaturePointcut() throws SecurityException, NoSuchMethodException {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("execution(public int com.example.ecstest.toby.Target.minus(int,int) throws java.lang.RuntimeException)");

        //Target.minus()
        assertThat(pointcut.getClassFilter().matches(Target.class) &&
                pointcut.getMethodMatcher().matches(Target.class.getMethod("minus", int.class, int.class), null)).isTrue();

        //Target.plus()
        assertThat(pointcut.getClassFilter().matches(Target.class) &&
                pointcut.getMethodMatcher().matches(Target.class.getMethod("plus", int.class, int.class), null)).isFalse();

        //Bean.method()
        assertThat(pointcut.getClassFilter().matches(Bean.class) &&
                pointcut.getMethodMatcher().matches(Target.class.getMethod("method", int.class, int.class), null)).isFalse();
    }
}
```
포인트컷의 선정 방식은 클래스 필터와 메소드 매처를 각각 비교해보는 것이다. 두 가지 조건을 모두 만족시키면 해당 메소드는 포인트컷의 대상이 된다.

**포인트컷 표현식 테스트**
메소드 시그니처를 그대로 사용한 포인트 표현식을 위의 문법구조를 참고해서 다시 정리해보자. 옵션 항목을 생력하면 다음과 같이 만들 수 있다.

`execution(int minus(int,int))`

이 표현식은 어떤 접근제한자를 가졌든, 어떤 클래스에 정의됐든, 어떤 예외를 던지든 상관없이 정수 값을 리턴하고 두개의 정수형 파라미터를 갖는 
minus라는 이름의 모든 메소드를 선정하는 좀 더 느슨한 포인트컷이 됐다.

`execution(* minus(int,int))`
-> 리턴타입이 상관없다.

`execution(* minus(..))`
-> 리턴타입이 상관없고 파라미터 개수와 타입도 상관 없다.

`execution(* *(..))`
-> 리턴타입, 파라미터, 메소드 이름에 상관없이 모든 메소드 조건을 다 허용한다.

다양한 표현식을 더 테스트해본다. 테스트를 돕는 테스트 헬퍼 메서드를 작성한다. 그리고 클래스 + 메서드를 조합한 6가지 방식을 모두 테스트해본다.

``` java
 public void pointcutMatches(String expression, Boolean expected, Class<?> clazz, String methodName, Class<?>... args) throws NoSuchMethodException {
    AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
    pointcut.setExpression(expression);

    assertThat(pointcut.getClassFilter().matches(clazz) &&
           pointcut.getMethodMatcher().matches(clazz.getMethod(methodName, args), null)).isEqualTo(expected);
    }
    
 public void targetClassPointcutMatches(String expression, boolean... expected) throws Exception {
    pointcutMatches(expression, expected[0], Target.class, "hello");
    pointcutMatches(expression, expected[1], Target.class, "hello", String.class);
    pointcutMatches(expression, expected[2], Target.class, "hello", int.class, int.class);
    pointcutMatches(expression, expected[3], Target.class, "minus", int.class, int.class);
    pointcutMatches(expression, expected[4], Target.class, "plus", int.class, int.class);
    pointcutMatches(expression, expected[5], Bean.class, "method");

    }
    
 @Test
 void pointcut() throws Exception {
     targetClassPointcutMatches("execution(* *(..))",true, true, true, true, true, true);
 }
```

**포인트컷 표현식을 이용하는 포인트컷 적용**
위에 설명한 방법 외에도 더 다양한 포인트컷 적용방식이 많다. 

메소드 시그니처를 비교하는 방식인 execution() 외에도 빈의 이름으로 비교하는 bean()이 있다.
`bean(*Service)` 라고 쓰면 Service로 끝나는 모든 빈을 선택한다. \
또 특정 애노테이션이 타입, 메소드, 파라미터에 적용되어 있는 것을 보고 메소드를 선정하게 하는 포인트컷도 만들 수 있다.
`ex) @Transaction`

테스트를 해봤으니 직접 적용할 시간이다. 이제 NameMatchclassMethodPointcut과 같이 직접 만든 포인트컷 구현 클래스를 사용할 일은 없다.
기존 빈 프로퍼티 선언에 담긴 조건들을 다시 살펴본다.

```xml
<property name="mappedClassName" value="*ServiceImpl"/> <!-- 클래스 이름 패턴 -->
<property name="mappedName" value="upgrade*"/> <!-- 메소드 이름 패턴 -->
```

AspectJExpressionPointcut 빈을 등록하고 expression 프로퍼티에 표현식을 넣어주면 된다.
표현식은 다음과 같다. `expression(* *..*ServiceImpl.upgrade*(..))` \
이를 적용한 빈 설정은 다음과 같다.
```xml
<bean id="transactionPointcut" class="org.springframework.aop.aspectj.AseprctJExpressionPointcut">
    <property name="expression" value="expression(* *..*ServiceImpl.upgrade*(..))" />
</bean>
```
코드가 단순해진다는 장점이 있지만, 적용되는 패턴이 문자열로 이루어져있기때문에 컴파일 시점에 검증할 수 없고 런타임 시점에
알수있다는 단점이 존재한다. 때문에 다양한 테스트를 통해 검증된 표현식을 가져다 써야한다. \
정확하게 원하는 빈에만 적용되었는지는 추후 스프링에서 제공하는 툴을 사용하면 한눈에 알 수 있다. 

**타입 패턴과 클래스 이름 패턴**
앞서 클래스 및 메소드 이름 패턴으로 적용하는 방법과 AseprctJExpressionPointcut의 exepression을 사용하여 적용하는 법을 알아보았다.
그런데 이 두가지 방법에는 중요한 차이점이 있다. 포인트컷 표현식에는 TestUserService도 테스트를 통과한다. 왜일까?\
그 이유는 포인트컷 표현식의 클래스 이름에 적용되는 패턴은 클래스 이름 패턴이 아니라 **타입 패턴**이기 때문이다.
TestUserService는 TestUserServiceImpl을 상속하여 구현하였기 때문에 패턴이 적용된다.
포인트컷 표현식에서 **타입 패턴**이라고 명시된 부분은 모두 동일한 원리가 적용된다.


### 6.5.4 AOP란 무엇인가?
비지니스 로직을 담은 UserService에 트랜잭션을 적용해온 과정을 정리해보자

**트랜잭션 서비스 추상화**

트랜잭션 코드를 비지니스 로직안에 함께 구현한 코드는 특정 트랜잭션 기술에 종속되어 버린다.
JDBC 로컬 트랜잭션 방식을 적용한 코드를 JTA를 이용한 코드로 바꾸려면 트랜잭션을 적용한 모든 코드를 변경해야한다.

따라서 트랜잭션 적용의 내용은 유지하고 구체적인 구현 방법을 바꿀 수 있도록 서비스 추상화를 적용했고
비지니스 로직은 트랜잭션을 어떻게 처리한다는 구체적인 방법은 알지않아도 되었다. 
서비스 추상화란 결국 인터페이스와 DI를 통해 분리하고 구현체를 주입해주는 방식을 적용한 것이다.

**프록시와 데코레이터 패턴**



**다이내믹 프록시와 프록시 팩토리 빈**

**자동 프록시 생성 방법과 포인트컷**

**부가기능의 모듈화**

트랜잭션 같은 부가기능은 핵심기능과 같은 방식으로 모듈화하기가 매우 힘들다. 왜냐하면 부가기능은 핵심기능이 존재해야만 의미가 있기 때문이다.\
결국 지금까지 해온 모든 작업은 핵심기능에 부여되는 부가기능을 효과적으로 모듈화하는 방법을 찾는 것이었다.

**AOP: 애스펙트 지향 프로그래밍**
애스팩트란 그 자체로 애플리케이션의 핵심기능을 담고 있지는 않지만, 애플리케이션을 구성하는 중요한 한가지 요소이고, 핵심기능에
부가되어 의미를 갖는 특별한 모듈을 가리킨다.

애스펙트 : 어드바이스 + 포인트컷\
어드바이저는 아주 단순한 형태의 애스펙트라고 할 수 있다.

AOP는 OOP를 돕는 보조적인 기술이지 OOP를 대체하는 새로운 개념이 아니다.

### 6.5.5 AOP 적용기술
스프링은 스프링의 다양한 기술을 조합해 AOP를 지원하고 있으나, 핵심 기능은 프록시다

**바이트코드 생성과 조작을 통한 AOP**
AspectJ는 프록시를 통하지않고 JVM이 java 코드를 클래스 파일로 바꿀때 직접 타깃 코드를 수정해서 부가기능을 삽입한다.
이런 방법을 사용하는 이유는 두가지가 있는데,
1. 첫째는 스프링과 같은 DI 컨테이너를 사용하지않는 환경에서도 AOP 적용이 가능하며
2. 프록시가 AOP 대상을 클라이언트가 호출하는 메소드에 한정할 수 밖에 없는것 반면에, AspectJ는 바이트 코드를 직접 수정하기 때문에 다양한  순간에 부가기능 적용이 가능하다.

다만 일반적인 AOP 적용이라면 프록시방식을 이용하면 충분하다. 프록시 AOP 수준을 넘어서는 데에만 그때 AspectJ를 사용하면된다.

### 6.5.6 AOP의 용어
- **타깃** : 부가기능 부여할 대상
- **어드바이스** : 타깃에 제공할 부가기능을 담은 모듈
- **조인 포인트** : 어드바이스가 적용될 수 있는 위치
- **포인트컷** : 어드바이스를 적용할 조인 포인트를 선별하는 작업 또는 기능을 정의한 모듈 (포인트컷 정규표현식)
- **프록시** : 클라이언트와 타깃 사이에서 부가기능을 제공하는 오브젝트. 타깃에 위임하면서 부가기능 수행.
- **어드바이저** : 포인트컷 + 어드바이스인 오브젝트
- **애스펙트** : 한 개 또는 그 이상의 포인트컷과 어드바이스의 조합 (오브젝트) // 어드바이저는 아주 단순한 애스펙트

### 6.5.7 AOP 네임스페이스

스프링 프록시 방식 AOP를 적용하려면 최소한 네 가지 빈을 등록해야한다.
- 자동 프록시 생성기 : DefaultAdvisorAutoProxtCreator. 프록시 자동생성 기능
- 어드바이스 : 부가기능 구현한 클래스를 빈으로 등록
- 포인트컷 : AsepectJExpressionPointcut. expression에 포인트컷 표현식 넣음
- 어드바이저 : DefaultPointcutAdvisor. 어드바이스와 포인트컷 등록

**AOP 네임스페이스**

**어드바이저 내장 포인트컷**

## 6.6 트랜잭션 속성
PlatforTransactionManager을 설명할때 사용했던 DefaultTransactionDefinition 오브젝트의 용도를 알아보자
``` java
public Object invoke(MethodInvocation invocation) throws Throwable {
        TransactionStatus status = 
            this.transactionManager.getTransaction(new DefaultTransactionDefinition()); //트랜잭션 시작???

        try {
            Object ret = invocation.proceed();
            this.transactionManager.commit(status); // 트랜잭션 종료
            return ret;
        } catch (RuntimeException e) {
            this.transactionManager.rollback(status); // 트랜잭션 종료
            throw e;
        }
    }
```



### 6.6.1 트랜잭션 정의
트랜잭션의 기본개념인 더 이상 쪼갤 수 없는 최소단위의 개념은 유효하나, 
트랜잭션 동작방식을 제어할 수 있는 방법이 commit(), rollback() 외에도 존재한다.

DefaultTransactionDefinition이 구현하고 있는 TransactionDefinition 인터페이스는 트랜잭션 동작방식에 영향을 줄 수 있는 네 가지 속성을 정의하고 있다.

**트랜잭션 전파**

![](../../../../../../../../../var/folders/50/7ndqz7bx4dv6bnkwf1pydg780000gn/T/TemporaryItems/NSIRD_screencaptureui_gK6Eh3/스크린샷 2023-11-08 17.36.24.png)
A에서 트랜잭션이 시작되면 B는 A의 트랜잭션을 물려받아야할까 아니면 새로운 트랜잭션을 생성해야할까?
이것을 트랜잭션 전파 속성이라고 하고 해당 속성을 설정할 수 있다.

- PROPAGATION_REQUIRED
  - 가장 많이 사용되는 트랜잭션 전파 속성
  - 진행 중인 트랜잭션이 없으면 새로 시작하고 있으면 참여한다.
  - DefaultTransactionDefinition 전파 속성
- PROPAGATION_REQUIRES_NEW
  - 항상 새로운 트랜잭션을 시작
- PROPAGATION_REQUIRES_SUPPORTED
  - 트랜잭션 무시
- 그 외


getTransaction()메소드를 사용하는 이유는 트랜잭션 전파속성이 있기 때문이다. 항상 새로 시작하는게 아니라, 있으면 가져오기 때문

**격리수준(isolation level)**

모든 DB는 격리수준을 갖고있어야한다. 서버에서는 성능이슈로인해 트랜잭션이 여러게 동시에 진행될 수 있기때문에 문제가 발생하지 않게 제어가 필요하다.
격리수준은 기본적으로 DB에서 설정하지만 JDBC나 DataSource등에서 재설정할 수 있고, 트랜잭션 단위로도 조정할 수 있다.
DefaultTransactionDefinition의 격리수준은 ISOLATION_DEFAULT이다. 이는 DataSource에 설정되어 있는 디폴트 격리수준을 그대로 따른다는 뜻이다.

**제한시간**

트랜잭션을 직접 시작할 수 있는 PROPAGATION_REQUIRED, PROPAGATION_REQUIRES_NEW일때만 함께 사용해야 의미가 있다.

**읽기전용**

데이터 조작하는 시도를 막아준다.

위의 네 가지 속성을 이용해서 트랜잭션의 동작방식을 제어할 수 있다.
TransactionDefinition를 구현한 오브젝트를 빈으로 등록해 DI 받아서 사용한다. 그런데 그렇게하면 전체 트랜잭션 설정이 전부 변경된다.
원하는 곳만 원하는 메서드만 선택해서 트랜잭션 정의를 적용하고싶다.


### 6.6.2 트랜잭션 인터셉터와 트랜잭션 속성
어드바이스의 기능을 확장해서 메소드 이름 패턴에 따라 다른 트랜잭션 정의가 적용되도록 만들자

**TransactionInterceptor**

스프링에서는 트랜잭션 경계설정 어드바이스로 사용할 수 있도록 만들어진 TransactionInterceptor가 있다.
TransactionInterceptor 어드바이스는 기존 우리가 만들었던 TransactionAdvice에서 메소드 이름 패턴에 따라 트랜잭션 정의를 
따로 지정해 줄 수 있다는 점만 추가되었다. \
TransactionInterceptor는 PlatformTransactionManager와 Properties 타입의 두가지 프로퍼티를 가지고 있다.\
Properties 타입인 두 번째 프로퍼티 이름은 transactionAttibutes로 트랜잭션 속성을 정의한 프로퍼티다.
트랜잭션 속성은 TransactionDefinition의 네 가지 기본 항목에 rollback()이라는 메소드를 하나 더 갖고있는 TransactionAttribute로 정의된다.

```java
 public Object invoke(MethodInvocation invocation) throws Throwable {
        TransactionStatus status 
            = this.transactionManager.getTransaction(new DefaultTransactionDefinition()); // 트랜잭션 정의 4가지 속성

        try {
            Object ret = invocation.proceed();
            this.transactionManager.commit(status);
            
            return ret;
        } catch (RuntimeException e) { // 롤백 대상 예외종류 
            this.transactionManager.rollback(status);
            throw e;
        }
}

```

TransactionInterceptor에는 기본적으로 두 가지 종류의 예외처리 방식이 있다.
1. 런타임 예외가 발생하면 트랜잭션은 롤백된다.
2. 체크 예외가 발생하면 비지니스 로직에 따른 의미있는 롤백으로 인지하고 커밋된다.

그런데 그 외의 케이스가 발생할 수도 있기때문에 TransactionAttribute에서는 rollbackOn()이라는 속성을 둬서
상기의 원칙과 다른 예외처리가 가능하게 해준다.

TransactionInterceptor는 이런 TransactionAttribute를 Properties라는 일종의 맵 타입 오브젝트를 전달받는다.

**메소드 이름 패턴을 이용한 트랜잭션 속성 지정**

transactionAttributes 프로퍼티는 메소드 패턴을 키로, 트랜잭션 속성을 값으로 갖는 컬렉션이다.
트랜잭션 속상은 다음과 같은 문자열로 정의한다.
- PROPAGATION_NAME: 트랜잭션 전파 방식 (필수)
- ISOLATION_NAME: 격리수준 (생략가능)
- readOnly: 읽기전용 (생략가능)
- timeout_NNNN: 제한시간 (생략가능)
- -Exception1: 체크예외중에서 롤백 대상으로 추가할 것을 넣음
- +Exception2: 런타임 예외지만 롤백시키지 않을 예외들을 넣음

이중에서 생략가능한 것들은 DefaultTransactionDefinition의 디폴트 속성이 부여된다.
속성 설정은 한줄의 문자열로 설정이 되는데, 이렇게 설정한 이유는 중첩된 태그와 프로퍼티로 설정하기 번거롭기 때문이다.
설정 예는 하기와 같다.
```xml
<bean id="transactionAdvice" class="org.springframwork.transaction.interceptor.TransactionInterceptor">
    <property name="transactionManager" ref="transactionManager" />
    <property name="transactionAttributes">
        <props>
            <prop key="get*">PROPAGATION_REQUIRED,readOnly,timeout_30</prop>
            <prop key="update*">PROPAGATION_REQUIRED_NEW,ISOLATION_SERIALIZABLE</prop>
            <prop key="*">PROPAGATION_REQUIRED</prop>
        </props>
    </property>
    
</bean>
```

만약 읽기전용이 아닌 트랜잭션 속성을 가진 메소드에서 get으로 시작되는 메소드를 호출하면,
작업이 충돌될 것이라고 생각하겠지만 그렇지않다. readOnly나 timeout등은 트랜잭션이 처음 시작될 때가 아니라면 적용되지 않는다.

메소드 이름이 하나 이상의 패턴과 일치하는 경우에는, 그 중 가장 정확히 일치하는 것에 적용된다.
이렇게 메소드 이름 패턴을 사용하는 트랜잭션 속성을 활용하면, 하나의 트랜잭션 어드바이스를 정의하는 것만으로도 다양한 트랜잭션 설정이 가능해진다.

**tx 네임스페이스를 이용한 설정 방법**

<bean> 태그로 등록하는 경우에 비해 장점이 많아 tx 스키마 태그를 사용해 등록하도록 권장.. 하나 xml로 등록해야하나요? ㅎㅅㅎ

### 6.6.3 포인트컷과 트랜잭션 속성의 적용 전략

트랜잭션 부가기능 적용 메소드는 포인트컷에 의해, 트랜잭션 방식은 어드바이스의 트랜잭션 전파 속성에 따라 결정된다. 기본 설정은 바뀌지 않고 expression 표현식과 
<tx:attributes>의 트랜잭션 속성만 결정하면 된다.\
포인트컷 표현식과 트랜잭션 속성을 정의할 때 따르면 좋은 몇 가지 전략을 생각해보자

**트랜잭션 포인트컷 표현식은 타입 패턴이나 빈 이름을 이용한다**

트랜잭션을 적용할 타깃이 되는 메서드가 든 클래스는 모두 트랜잭션 적용 후보가 되는 것이 바람직하다. 일반적으로 비지니스 로직을 담고있는 클래스라면 메소드 단위까지 세밀하게 
포인트컷을 정의해줄 필요는 없다.\
UserService의 add()메소드도 트랜잭션 적용대상이 되어야한다. add() 메소드가 다른 트랜잭션에 참여할 가능성이 높기 때문이다.\
쓰기작업이 없는 단순한 조회작업에도 읽기전용 트랜잭션 속성을 설정하는 것이 좋다.\
따라서 트랜잭션용 포인트컷 표현식에는 메소드나 파라미터, 예외에 대한 패턴을 정의하지 않는게 바람직하다. 보통 해당 클래스들이 모여있는 패키지를 통째로 선택하거나,
비지니스 로직 담당 클래스 이름은 Service, ServiceImpl로 끝나는 경우가 많아서 `execution(**..*ServiceImpl.*(..))`과 같이 포인트컷을 정의한다.\
가능하면 클래스보다는 변경 빈도가 적은 인터페이스 타입을 기준으로 타입 패턴을 적용하는 것이 좋다.\
혹은 스프링의 bean() 표현식을 사용해도 좋다. bean id가 Service로 끝나는 모든 빈에 대해 적용하고 싶다면 포인트컷 표현식을 bean(*Service)라고하면 된다.

**공통된 메소드 이름 규칙을 통해 최소한의 트랜잭션 어드바이스와 속성을 정의한다.**

너무 다양한 트랜잭션 속성을 사용하면, 관리만 힘들다. 따라서 몇가지의 트랜잭션 속성만 정이하고, 그에 따른 메소드명명규칙을 만들어두면,
하나의 어드바이스만으로도 모든 서비스 빈에 트랜잭션 속성을 지정할 수 있다.

하기와 같이 모든 메서드의 디폴트 속성을 지정한 후, 개발이 진행함에 따라 단계적으로 속성을 추가한다. 
```xml
<tx:advice id="transactionAdvice">
    <tx:attributes>
        <tx:method name="*"/>
    </tx:attributes>
</tx:advice>
```

개발을 진행하다가 get~ 으로 시작하는 읽기전용 메서드에 읽기전용 트랜잭션을 추가할 경우 하기와 같이 xml 코드를 수정하면 된다.
혹시 get~ 메서드에 데이터 조작이 일어나면 에러가 발생하게 된다.

```xml
<tx:advice id="transactionAdvice">
    <tx:attributes>
        <tx:method name="get*" read-only="true" />
        <tx:method name="*"/>
    </tx:attributes>
</tx:advice>
```

트랜잭션 적용대상 클래스의 메소드는 일정한 명명 규칙을 따르게 해야하며, 일반화하기 어려운 경우 별도의 어드바이스와 포인트컷 표현식을 사용하는 것이 좋다.
```xml
<aop:config>
    <aop:advisor advice-ref="transactionAdvice" pointcut="bean(" />
</aop:config>
<tx:advice id="transactionAdvice">
    <tx:attributes>
        <tx:method name="get*" read-only="true" />
        <tx:method name="*"/>
    </tx:attributes>
</tx:advice>
```



**프록시 방식 AOP는 같은 타깃 오브젝트 내의 메소드를 호출할 때는 적용되지 않는다.**

프록시 방식의 AOP에서는 클라이언트가 호출했을 때만 부가기능이 적용되고 클래스 내부의 메소드를 호출할 때는 적용되지 않는다.
![](../../../../../../../../../var/folders/50/7ndqz7bx4dv6bnkwf1pydg780000gn/T/TemporaryItems/NSIRD_screencaptureui_QQ1gjM/스크린샷 2023-11-13 10.31.34.png)

타깃 오브젝트의 다른 메소드를 호출하는 경우에는 프록시를 거치지 않고 직접 타깃 메소드가 호출된다.
그림에서 예시를 든다면 [1]에 적용된 트랜잭션 속성은 실행이 되지만 [2]의 경우 update()에 적용된 트랜잭션은 실행되지 않는다.\
다시한번, 타깃 오브젝트 안에서 메소드 호출이 일어나는 경우에는 부가기능이 적용되지 않는다. 해결할 수 있는 방법은 두가지가 있다.\
1. 스프링 API를 이용해 프록시 오브젝트에 대한 레퍼런스를 가져온뒤에, 같은 오브젝트 메소드 호출도 프록시를 이용하도록 강제하는 방법. (복잡 비추천)
2. AspectJ와 같이 타깃의 바이트코드를 직접 조작하는 방식은 AOP 기술을 적용 (14장에서 설명)


### 6.6.4 트랜잭션 속성 적용
트랜잭션 속성과 전략을 UserService에 적용한다.

**트랜잭션 경계설정의 일원화**
비지니스 로직을 담고 있는 서비스계층 오브젝트의 메소드에 트랜잭션 경계를 부여하는 것으로 통일한다.
트랜잭션 경계를 서비스 계층으로 두었으니, 꼭 DAO는 서비스계층을 통해서만 접근하도록 하고 다른 메소드나 클래스에서 DAO로 바로 접근하는 것을 차단한다.\
(UserDao는 UserService에서만 접근한다.)
비지니스로직이 있다면 서비스계층을 두는 것이 맞고, 단순 입출력과 검색수준의 조회가 전부라면 서비스계층을 없애고 DAO를 트랜잭션 경계로 만드는 방법도 있다.\

기존 UserDao에서 트랜잭션이 적용될 메소드를 UserService에 추가한다.
```java
    public interface UserService {
        void add(User user); 
        User get(String id); //추가
        List<User> getAll(); //추가
        void deleteAll(); //추가
        void update(User user); //추가
}
```
다음은 UserServiceImpl에서 UserDao를 호출하도록 변경한다.

```java
    public class UserServiceImpl implements UsreService {
        UserDao userDao;
        
        public void deleteAll() { userDao.deleteAll(); }
        public User get(String id) { return userDao.get(id); }
        public List<User> getAll() { return userDao.getAll(); }
        public void update(User user) { userDao.update(user); }
}
```
이렇게 모든 User관련 데이터 조작은 UserService라는 트랜잭션 경계를 통해 진행한다.

**서비스 빈에 적용되는 포인트컷 표현식 등록**
모든 비지니스 로직의 서비스 빈에 트랜잭션이 적용되도록 포인트컷 표현식을 변경한다.
```xml
    <aop:config>
        <aop:advisor advice-ref="transactionAdvice" poincut="bean(*Service)"/>    
    </aop:config>
```

**트랜잭션 속성을 가진 트랜잭션 어드바이스 등록**
어드바이스 빈을 스프링의 TransactionAdvice 클래스로 정의했던 어드바이스 빈을 TransactionInterceptor를 사용하도록 변경한다.

```xml
<bean id="transactionAdvice" class="org.springframwork.transaction.interceptor.TransactionInterceptor">
    <property name="transactionManager" ref="transactionManager" />
    <property name="transactionAttributes">
        <props>
            <prop key="get*">PROPAGATION_REQUIRED,readOnly</prop>
            <prop key="*">PROPAGATION_REQUIRED</prop>
        </props>
    </property>
    
</bean>
```
~ 스키마 태그 ~ 

**트랜잭션 속성 테스트**
이제 학습 테스트를 만들어보자.
우리가 적용한 트랜잭션 속성에는 get* 메소드는 읽기전용이 적용되어있다. 쓰기작업을 진행하면 트랜잭션 적용이 안될까? 학습테스트를 통해 확인한다.\
예외적인 상황을 억지로 만들어야 하기 때문에 이전에 롤백 테스트를 위해 만든 TestUserService를 활용한다.\
새로추가한 getAll() 메소드를 오버라이드해서 강제로 DB에 쓰기 작업을 추가한다. 그러면 예외가 발생할까? 어떤 예외가 발생할까? 코드를 한번 돌려보고 어떤 예외가 발생했는지 확인한다.

```java
public class TestUserService extends UserServiceImpl{

    @Override
    public List<User> getAll() {
        for (User user : super.getAll()) {
            super.update(user); // 강제로 쓰기를 시도
        }
        
        return null;
    }
}
```
위와같이 TestUserService를 수정하고 getAll을 호출하는 Test 또한 작성한다.

```java
    @Test
    public void readOnlyTransactionAttribute() {
        testUserService.getAll();    // 예외발생?
    }   
```
테스트를 돌리면 `TransientDataAccessResourceException` 이라는 예외가 발생하는데, 해당 예외는 일시적인 제약조건 즉, 읽기전용 트랜잭션이 걸려있어서 실패했다 라는 의미를 내포한 예외이다.
이제 테스트가 예외가 발생할 것을 반영하여 재수정후 테스트하면 테스트 통과한다.



```java
    @Test(expected=TransientDataAccessResourceException.class)
    public void readOnlyTransactionAttribute() {
        testUserService.getAll();  
    }   
```