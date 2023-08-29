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
