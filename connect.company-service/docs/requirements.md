# 스파르타 물류 시스템 요구사항

## 1. 프로젝트 개요

### 프로젝트명

스파르타 물류

### 목적

Spring Boot와 Spring Cloud를 활용하여 **MSA 기반 국내 B2B 물류 관리 및 배송 시스템**을 구현한다.

시스템은 지역별 허브를 중심으로 다음 기능을 제공한다.

- 사용자 관리
- 허브 관리
- 허브 간 이동정보 관리
- 업체 관리
- 상품 관리
- 주문 관리
- 배송 관리
- Slack 메시지 발송

각 주요 도메인은 필요한 경우 독립적인 Microservice로 구성하고,
서비스 간 데이터가 필요한 경우 다른 서비스의 데이터베이스를 직접 조회하지 않고 API를 통해 통신한다.

---

# 2. 핵심 구현 범위

이번 프로젝트의 필수 구현 범위는 다음과 같다.

- MSA 기반 서비스 구성
- Eureka Service Discovery
- Spring Cloud Gateway
- Gateway 인증 및 인가 처리
- 자체 JWT 기반 인증
- FeignClient 기반 서비스 간 통신
- 서비스별 독립 Database 또는 Schema
- 실패 시 Rollback 처리 시나리오 1개 이상
- Docker Compose 기반 전체 서비스 실행
- Swagger / OpenAPI 기반 API 문서화
- ERD 작성
- API 명세 작성
- 인프라 설계도 작성

---

# 3. 시스템 구조

## 3.1 MSA

각 서비스는 자신의 도메인 데이터를 독립적으로 관리한다.

다른 Microservice의 Database에 직접 접근해서는 안 된다.

서비스 간 데이터 확인이 필요한 경우 REST API를 사용한다.

기본적인 서비스 간 통신 방식은 `FeignClient`를 사용한다.

```text
Service A
    ↓
FeignClient
    ↓
Service B API
    ↓
Service B Database
```

다음 구조는 허용하지 않는다.

```text
Service A
    ↓
Service B Database 직접 접근
```

---

## 3.2 API Gateway

모든 외부 요청은 Spring Cloud Gateway를 통해 각 Microservice로 전달한다.

Gateway는 다음을 담당한다.

- 외부 요청 라우팅
- 사용자 인증 확인
- 사용자 권한 확인

인증되지 않은 요청이 보호된 Microservice API에 전달되지 않도록 한다.

---

## 3.3 Service Discovery

Spring Cloud Eureka를 사용한다.

각 Microservice는 Eureka에 등록하며,
서비스 간 통신 시 Eureka를 통해 대상 서비스를 찾을 수 있도록 구성한다.

---

# 4. 공통 데이터 요구사항

## 4.1 식별자

주요 Entity의 식별자는 `UUID`를 사용한다.

서비스 간 데이터를 참조할 때도 UUID를 사용한다.

예:

```text
companyId
hubId
productId
orderId
deliveryId
userId
```

다른 서비스의 Entity 객체 자체를 공유하지 않는다.

---

## 4.2 서비스 간 연관관계

동일 Microservice 내부의 Entity 간 관계는 필요한 경우 Database Foreign Key와 JPA 연관관계를 사용할 수 있다.

다른 Microservice의 Entity와는 Database Foreign Key 및 JPA 연관관계를 사용하지 않는다.

다른 Microservice의 데이터는 해당 리소스의 UUID만 저장하고, 필요한 정보는 API 호출을 통해 조회한다.

예:

```text
Company ↔ Product
→ 동일 Company Service
→ FK / JPA 연관관계 사용 가능

Company → Hub
→ 서로 다른 Microservice
→ hubId UUID만 저장하고 Hub Service API를 통해 확인
```

---

## 4.3 Table 명명 규칙

모든 Table 이름에는 `p_` 접두사를 사용한다.

예:

```text
p_users
p_companies
p_products
p_orders
p_deliveries
```

---

## 4.4 Audit 필드

기본적으로 Entity에는 다음 Audit 필드를 둔다.

```text
created_at
created_by
updated_at
updated_by
deleted_at
deleted_by
```

Update가 발생하지 않는 기록형 데이터는 일부 필드를 생략할 수 있다.

---

# 5. 논리적 삭제

데이터 삭제 시 가능한 경우 실제 Row를 제거하지 않고 Soft Delete를 사용한다.

삭제 시:

```text
deleted_at
deleted_by
```

값을 저장한다.

조회 및 검색 시:

```text
deleted_at IS NULL
```

인 데이터만 정상 데이터로 취급한다.

논리 삭제된 데이터로 인해 비즈니스 로직에 문제가 발생하지 않도록 검증한다.

---

# 6. 조회 및 검색

CRUD 대상 Entity에는 요구사항에 따라 조회 및 검색 기능을 제공한다.

목록 조회 및 검색 API에는 Pagination을 적용한다.

기본 Query Parameter는 다음 형식을 사용한다.

```text
page
size
sort
```

`page`는 0부터 시작한다.

검색에는 다음 기능을 포함한다.

- 검색 조건
- 페이징
- 정렬

검색 조건과 Pagination을 함께 사용할 수 있어야 한다.

기본 정렬:

```text
생성일 역순
```

즉 최신 데이터가 먼저 조회되도록 한다.

기본 `size` 는 10이며 최대 `size`는 100이다.

---

# 7. 사용자 관리

## 7.1 사용자 정보

사용자 Entity는 사용자 정보를 관리한다.

필수 정보:

- username
- password
- Slack ID
- 소속 업체명 또는 허브명
- role

---

## 7.2 회원가입

회원가입 즉시 계정이 활성화된다.

별도의 관리자 승인 절차는 사용하지 않는다.

회원가입 요청 시 다음 정보를 입력받는다.

- 이름 또는 username
- password
- Slack ID
- 소속 업체명 또는 허브명

회원가입 시 사용자의 기본 Role은 `USER`로 고정한다.

회원가입 Request에서는 Role을 입력받지 않는다.

일반 사용자는 자신의 Role을 직접 변경할 수 없다.

`MASTER` 권한 부여 및 Role 변경은 관리자 기능을 통해서만 수행한다.

최초 `MASTER` 계정 생성 방식은 팀의 초기 데이터 또는 운영 설정 정책을 따른다.

### username 검증

username은 다음 규칙을 만족해야 한다.

```text
4자 이상
10자 이하
알파벳 소문자 a-z
숫자 0-9
```

### password 검증

password는 다음 규칙을 만족해야 한다.

```text
8자 이상
15자 이하
알파벳 대문자
알파벳 소문자
숫자
특수문자
```

비밀번호는 평문으로 저장하지 않는다.

BCrypt 등의 Hash Algorithm을 사용해 암호화한다.

---

## 7.3 로그인

사용자는 username과 password를 이용해 로그인한다.

로그인 성공 시 자체 JWT를 발급한다.

별도의 Keycloak 인증 서버는 사용하지 않는다.

---

## 7.4 사용자 권한

기본적인 권한은 다음 두 단계로 구분한다.

```text
MASTER
USER
```

### MASTER

관리자는 전체 기능 및 관리 기능에 접근할 수 있다.

### USER

로그인한 일반 사용자이다.

자신이 연관되거나 소유한 데이터에 대해서만 요구사항에 따라 수정할 수 있다.

---

## 7.5 사용자 CRUD 권한

사용자 관리 API의 권한은 다음과 같다.

| 기능 | USER | MASTER |
|---|---:|---:|
| 회원가입 | O | O |
| 본인 조회 | O | O |
| 전체 사용자 조회 | X | O |
| 관리 목적 사용자 생성 | X | O |
| 사용자 수정 | X | O |
| 사용자 삭제 | X | O |

회원가입과 관리자용 사용자 관리 기능을 구분한다.

사용자 삭제는 Soft Delete 방식으로 처리한다.

---

# 8. 허브 관리

## 8.1 허브 기본 정책

허브는 총 17개이며 위치 정보는 고정이다.

허브에 대한 생성, 수정, 삭제 API는 구현하지 않는다.

애플리케이션 초기 실행 시 허브 데이터를 적재하고 조회 API만 제공한다.

---

## 8.2 허브 목록

다음 허브를 사용한다.

| 허브 | 주소 |
|---|---|
| 서울특별시 센터 | 서울특별시 송파구 송파대로 55 |
| 경기 북부 센터 | 경기도 고양시 덕양구 권율대로 570 |
| 경기 남부 센터 | 경기도 이천시 덕평로 257-21 |
| 부산광역시 센터 | 부산 동구 중앙대로 206 |
| 대구광역시 센터 | 대구 북구 태평로 161 |
| 인천광역시 센터 | 인천 남동구 정각로 29 |
| 광주광역시 센터 | 광주 서구 내방로 111 |
| 대전광역시 센터 | 대전 서구 둔산로 100 |
| 울산광역시 센터 | 울산 남구 중앙로 201 |
| 세종특별자치시 센터 | 세종특별자치시 한누리대로 2130 |
| 강원특별자치도 센터 | 강원특별자치도 춘천시 중앙로 1 |
| 충청북도 센터 | 충북 청주시 상당구 상당로 82 |
| 충청남도 센터 | 충남 홍성군 홍북읍 충남대로 21 |
| 전북특별자치도 센터 | 전북특별자치도 전주시 완산구 효자로 225 |
| 전라남도 센터 | 전남 무안군 삼향읍 오룡길 1 |
| 경상북도 센터 | 경북 안동시 풍천면 도청대로 455 |
| 경상남도 센터 | 경남 창원시 의창구 중앙대로 300 |

---

## 8.3 허브 권한

조회 및 검색은 모든 로그인 사용자가 가능하다.

---

# 9. 허브 간 이동정보 관리

허브 간 이동 경로를 P2P 방식으로 모델링한다.

모든 허브 사이에 직접 이동이 가능한 것을 기준으로 한다.

필수 정보:

- 출발 Hub ID
- 도착 Hub ID
- 예상 소요시간
- 이동거리

허브 간 이동정보는 초기 데이터를 적재한다.

생성 및 수정 API는 필수 구현 대상이 아니다.

조회 및 검색은 모든 로그인 사용자가 가능하다.

---

# 10. 배송 담당자

배송 담당자는 별도의 Service 또는 Entity로 구성하지 않는다.

가입한 사용자에게 배송 담당자 역할 또는 권한을 부여하는 방식으로 관리한다.

배송 담당자는 다음 업무를 모두 담당할 수 있다.

```text
허브 → 허브 이동

허브 → 업체 배송
```

시간적·공간적 제약은 고려하지 않는다.

순차 배정 로직도 필수 구현하지 않는다.

---

# 11. 업체 관리

## 11.1 업체 Entity

업체는 특정 허브에 소속된다.

업체를 생산업체와 수령업체로 별도 구분하지 않는다.

하나의 업체가 상황에 따라 생산업체 또는 수령업체가 될 수 있다.

필수 정보:

- 업체명
- 관리 Hub ID
- 업체 주소
- 사용자 ID

사용자 ID는 해당 업체를 관리하는 사용자를 의미한다.

---

## 11.2 업체 기능

업체에 대해 다음 기능을 구현한다.

- 생성
- 조회
- 수정
- 삭제
- 검색

삭제는 Soft Delete 방식으로 처리한다.

---

## 11.3 Hub 존재 확인

업체 생성 또는 수정 시 입력된 `hubId`가 실제 존재하는 Hub인지 확인한다.

Company Service에서 Hub Database를 직접 조회하지 않는다.

Hub Service API를 호출하여 확인한다.

기본 흐름:

```text
업체 생성 / 수정 요청
        ↓
hubId 확인
        ↓
Company Service
        ↓
FeignClient
        ↓
Hub Service
        ↓
Hub 존재 여부 확인
```

존재하지 않는 Hub를 이용하여 업체를 생성하거나 수정할 수 없다.

---

## 11.4 업체 권한

### 생성 / 수정

다음 사용자만 가능하다.

- 해당 업체의 사용자 ID와 로그인 사용자 ID가 일치하는 사용자
- MASTER

### 조회 / 검색

모든 로그인 사용자가 가능하다.

---

# 12. 상품 관리

## 12.1 상품 관리 위치

상품은 별도의 Microservice로 분리하지 않는다.

**Company Service 내부의 업체 하위 리소스**로 관리한다.

```text
Company Service
├── Company
└── Product
```

---

## 12.2 상품 소속

모든 상품은 특정 업체에 소속된다.

또한 상품과 재고를 관리하는 Hub와 연관된다.

역할은 다음과 같이 구분한다.

```text
Company Service
→ 상품명
→ 상품 정보
→ 상품 가격

Hub Service
→ 해당 상품의 Hub별 재고
```

Company Service에서 Hub Service의 재고 정보를 직접 관리하지 않는다.

---

## 12.3 상품 기능

상품 관리 기능은 Company Service 내부에서 구현한다.

- 상품 등록
- 상품 조회
- 상품 수정
- 상품 삭제
- 상품 검색

상품 삭제 시 프로젝트 공통 Soft Delete 정책을 적용한다.

---

## 12.4 상품 데이터

상품은 최소한 다음 정보를 관리한다.

- Product ID
- Company ID
- 상품명
- 상품 정보
- 가격

필요한 Hub 참조값이 있다면 UUID로 관리한다.

다른 서비스의 Entity 객체를 직접 참조하지 않는다.

---

## 12.5 상품 권한

상품은 업체의 하위 리소스이므로 상품을 생성하거나 수정할 때
해당 업체에 대한 접근 권한을 확인해야 한다.

```text
로그인 사용자
        ↓
상품 소속 Company 조회
        ↓
Company.userId와 로그인 userId 비교
        ↓
본인 또는 MASTER인지 확인
```

조회 및 검색은 로그인 사용자를 대상으로 제공한다.

---

# 13. 주문 관리

## 13.1 주문 Entity

주문에는 최소한 다음 정보가 포함된다.

- 요청 업체 ID
- 공급 업체 ID
- 상품 ID
- 수량
- 배송 ID
- 요청사항

---

## 13.2 주문 생성

주문 생성 시 배송 데이터도 같이 생성해야 한다.

주문 생성 시 관련 상품의 재고가 감소한다.

재고가 부족한 경우 주문 생성은 실패해야 한다.

---

## 13.3 주문 취소

주문 취소 요청 시 Delivery Service를 통해 현재 배송 상태를 확인한다.

배송이 시작되지 않은 상태에서만 주문을 취소할 수 있다.

배송이 이미 진행 중이거나 완료된 경우 주문 취소는 실패한다.

정확한 취소 가능 상태는 팀에서 합의한 Delivery Status Enum 및 상태 전이 규칙을 따른다.

주문 취소가 정상 처리되면 감소했던 재고를 복구한다.

서비스 간 데이터 변경이 포함되는 경우 `# 17. Transaction 및 실패 복구` 정책을 따른다.

---

## 13.4 주문 삭제

주문 삭제는 Soft Delete 방식으로 처리한다.

```text
deleted_at
deleted_by
```

를 기록한다.

---

## 13.5 주문 권한

### 생성

모든 로그인 사용자가 가능하다.

### 조회 / 검색

로그인 사용자만 가능하다.

일반 주문자는 자신의 주문만 조회할 수 있도록 제한한다.

관리자는 필요한 경우 전체 주문을 조회할 수 있다.

---

# 14. 배송 관리

## 14.1 배송 Entity

배송에는 최소한 다음 정보가 포함된다.

- 주문 ID
- 현재 상태
- 출발 Hub ID
- 목적지 Hub ID
- 배송지 주소
- 수령인
- 배송 담당자 ID

---

## 14.2 배송 경로 기록

배송 경로 기록에는 최소한 다음 정보가 포함된다.

- 배송 ID
- 출발 Hub ID
- 도착 Hub ID
- 예상 거리
- 실제 거리
- 예상 소요시간
- 실제 소요시간
- 현재 상태
- 담당자 ID

---

## 14.3 배송 생성

주문 생성 시 다음 데이터가 함께 생성되어야 한다.

```text
Order
Delivery
Delivery Route
```

관련 데이터 생성 중 문제가 발생할 경우 데이터 일관성을 유지해야 한다.

---

## 14.4 배송 삭제

배송 및 배송 경로 기록은 Soft Delete 정책을 적용한다.

---

## 14.5 배송 권한

### 수정

다음 사용자만 가능하다.

- 해당 배송의 담당자 ID와 로그인 사용자 ID가 일치하는 사용자
- MASTER

### 조회 / 검색

로그인 사용자만 가능하다.

배송 담당자는 자신의 배송만 조회할 수 있도록 제한한다.

---

# 15. Slack 메시지

별도의 Slack Message Entity 또는 CRUD Service를 만들지 않는다.

주문 생성 시점에 Slack Incoming Webhook을 호출하는 기능만 구현한다.

```text
Order 생성
    ↓
Slack Incoming Webhook 호출
```

Slack Webhook URL은 코드에 직접 작성하지 않고 환경변수로 관리한다.

---

# 16. 서비스 간 통신

서비스 간 데이터가 필요한 경우 REST API를 사용한다.

기본 통신 방식은 `FeignClient`이다.

예:

```text
Company Service
    ↓
Hub Service

Order Service
    ↓
Company Service

Order Service
    ↓
Hub Service
```

서비스 간 요청 실패 시 실패 원인을 구분할 수 있도록 처리한다.

다른 서비스의 Database를 직접 조회해서는 안 된다.

## 16.1 서비스 간 인증정보 전달

서비스 간 FeignClient 호출 시 인증이 필요한 경우 최초 요청의 JWT를 `Authorization` Header에 전달한다.

```http
Authorization: Bearer {JWT}
```

Gateway를 통과한 이후의 Microservice 간 호출에서도 인증 및 인가가 필요한 요청은 동일한 JWT를 전달한다.

서비스 간 호출에서 JWT를 임의로 새로 발급하지 않는다.

FeignClient를 통해 전달받은 JWT의 처리 방식은 팀 공통 인증 정책을 따른다.

---

# 17. Transaction 및 실패 복구

동일 Microservice 내부의 여러 Database 변경은 Spring Transaction을 이용하여 일관성을 유지한다.

동일 서비스의 로컬 Transaction 내부에서 오류가 발생하면 해당 Transaction의 변경사항을 Rollback한다.

서로 다른 Microservice의 Database 변경은 하나의 로컬 `@Transactional`로 전체 Rollback할 수 있다고 가정하지 않는다.

다른 서비스의 데이터가 이미 변경된 이후 후속 서비스 호출이 실패한 경우에는 필요한 보상 요청을 통해 이전 변경사항을 복구한다.

대표적인 실패 복구 예:

```text
Order Service 주문 생성 시작
    ↓
Hub Service 재고 차감 성공
    ↓
Delivery Service 배송 생성 실패
    ↓
Hub Service 재고 복구 요청
    ↓
Order Service 주문 생성 실패 처리
```

프로젝트 전체에서 최소 하나 이상의 실패 복구 시나리오를 구현한다.

Soft Delete가 포함되는 로컬 Transaction에서도 오류 발생 시 Rollback 상황을 고려한다.

보상 처리 도중 추가 실패가 발생하는 경우에는 오류를 숨기지 않고 로그 및 예외를 통해 확인할 수 있도록 한다.

---

# 18. 인증 및 인가

Spring Security와 자체 JWT를 사용한다.

각 요청에서 사용자 인증 정보와 권한을 확인한다.

클라이언트가 전달한 권한 값을 그대로 신뢰하지 않는다.

인가가 필요한 기능은 서버 측에서 반드시 검증한다.

## 18.1 Gateway와 Microservice의 책임

Gateway와 각 Microservice에서 모두 필요한 인증 및 인가 검사를 수행한다.

Gateway는 공통 진입점에서 다음 항목을 확인한다.

- JWT 유효성
- 로그인 여부
- API 접근에 필요한 기본 Role

각 Microservice는 자신의 도메인 정보를 기준으로 다음 항목을 추가 확인한다.

- 요청 사용자의 Role
- 실제 리소스 소유권
- 해당 도메인의 비즈니스 권한

예:

```text
업체 수정
→ Gateway에서 JWT / 기본 권한 확인
→ Company Service에서 Company.userId와 로그인 userId 비교

상품 수정
→ Gateway에서 JWT / 기본 권한 확인
→ Company Service에서 Product가 속한 Company의 소유권 확인

배송 수정
→ Gateway에서 JWT / 기본 권한 확인
→ Delivery Service에서 Delivery 담당자 ID 확인
```

---

# 19. Validation

서버 측 요청값 검증에 Spring Validator를 사용한다.

### Request Validation

예:

```text
@NotNull
@NotBlank
@Size
@Pattern
```

### Business Validation

예:

```text
Hub가 실제 존재하는가?

Company가 존재하는가?

상품이 존재하는가?

사용자에게 해당 업체 수정 권한이 있는가?

재고가 충분한가?
```

---

# 20. API 설계

RESTful API 원칙을 기준으로 설계한다.

Entity와 DTO는 분리한다.

다음 DTO를 필요에 따라 구분한다.

```text
Request DTO
Response DTO
```

Entity를 API Response로 직접 반환하지 않는다.

API는 프론트엔드 또는 다른 Microservice가 명세만 보고 사용할 수 있을 정도로 문서화한다.

---

# 21. Swagger / OpenAPI 문서화

각 Microservice의 API는 **Swagger / OpenAPI 기반으로 문서화한다.**

Swagger UI를 통해 구현된 API를 확인하고 직접 요청을 테스트할 수 있도록 한다.

## 21.1 문서화 대상

각 API에는 가능한 범위에서 다음 내용을 명시한다.

- API 기능 설명
- HTTP Method
- Endpoint
- 요청 Header
- 인증 필요 여부
- 필요한 권한
- Path Variable
- Query Parameter
- Request Body
- Response Body
- HTTP Status Code
- 주요 오류 응답

---

## 21.2 인증 API

JWT 인증이 필요한 API는 Swagger에서도 JWT를 전달할 수 있도록 설정한다.

Swagger UI에서 다음 형식의 Authorization Header를 사용할 수 있어야 한다.

```text
Authorization: Bearer {JWT}
```

이를 통해 인증이 필요한 API도 Swagger UI에서 테스트할 수 있도록 한다.

---

## 21.3 API 설명

Controller의 Endpoint에는 API 역할을 확인할 수 있도록 설명을 작성한다.

예:

```text
업체 등록
업체 단건 조회
업체 검색
상품 등록
상품 수정
Hub 존재 여부 조회
```

단순히 Method 이름만 노출하는 문서가 아니라,
API 사용자가 기능과 입력값을 이해할 수 있도록 작성한다.

---

## 21.4 Request / Response Schema

Swagger에서 다음 DTO Schema를 확인할 수 있어야 한다.

- Request DTO
- Response DTO
- Error Response

필드명, 데이터 타입 및 필요한 경우 필드 설명을 제공한다.

Entity를 Swagger Request / Response 모델로 직접 사용하지 않는다.

---

## 21.5 오류 응답

프로젝트의 공통 오류 응답 형식을 문서화한다.

예:

```json
{
  "code": "COMPANY_NOT_FOUND",
  "message": "업체를 찾을 수 없습니다."
}
```

주요 HTTP Status는 다음 기준을 따른다.

```text
400 → 잘못된 요청
401 → 인증 실패
403 → 권한 없음
404 → 데이터 없음
409 → 비즈니스 충돌
500 → 예상하지 못한 오류
```

---

## 21.6 서비스별 Swagger

MSA 구조이므로 각 Microservice는 자신이 제공하는 API를 문서화한다.

예:

```text
User Service
→ User API

Hub Service
→ Hub API

Company Service
→ Company / Product API

Order Service
→ Order API

Delivery Service
→ Delivery API
```

다른 서비스의 Controller를 Swagger 문서화를 위해 직접 참조하지 않는다.

---

# 22. Database

권장 Database:

```text
PostgreSQL
```

각 Microservice는 논리적으로 독립된 Database 또는 Schema를 사용한다.

한 서비스가 다른 서비스의 Table을 직접 조회하지 않는다.

---

# 23. 프로젝트 구조

Layered Architecture 또는 이에 준하는 구조를 사용한다.

```text
Controller
→ HTTP 요청 / 응답

Service
→ Application / Business Logic

Domain
→ Entity 및 Domain Logic

Repository
→ 데이터 접근

Infrastructure
→ 외부 API / Database / 외부 시스템 연동
```

기존 프로젝트 구조가 있다면 해당 구조를 우선적으로 따른다.

---

# 24. Docker

이번 프로젝트는 별도 서버에 배포하지 않고 로컬 환경에서 실행한다.

모든 애플리케이션은 Docker Container로 실행할 수 있어야 한다.

다음 중 프로젝트 구조에 맞는 방식을 사용한다.

```text
Dockerfile
Docker Compose
```

전체 시스템은 Docker Compose를 통해 함께 실행할 수 있도록 구성한다.

---

# 25. 문서화

다음 문서를 작성한다.

- Swagger / OpenAPI API 문서
- ERD
- API 명세
- 인프라 설계도

ERD는 Microservice별 Database 구조와 서비스 경계를 확인할 수 있어야 한다.

API 명세에는 최소한 다음 내용을 포함한다.

- HTTP Method
- URL
- Request Header
- 인증 / 권한 조건
- Request Parameter
- Request Body
- Response
- Error Response

Swagger 문서는 실제 구현 코드와 일치하도록 유지한다.

API의 Request 또는 Response 형식이 변경되면 Swagger 문서도 함께 수정한다.

---

# 26. 테스트

각 Microservice의 주요 기능에 대해 가능한 범위에서 테스트를 작성한다.

권장 대상:

- Service 단위 테스트
- API 통합 테스트
- 서비스 간 통신 테스트
- 권한 검증
- Validation
- 예외 상황
- Rollback

Swagger UI에서도 주요 API의 요청 및 응답을 확인한다.

전체 테스트 커버리지를 높이는 것은 필수 요구사항이 아니다.

---

# 27. 선택 기능

다음 기능은 시간적 여유가 있는 경우 선택적으로 적용할 수 있다.

- Zipkin
- Redis Cache

Swagger는 본 프로젝트에서는 선택 기능이 아니라 **API 문서화를 위해 적용한다.**

---

# 28. 현재 프로젝트 범위에서 제외

다음 기능은 이번 프로젝트의 필수 범위에 포함하지 않는다.

- Kafka 기반 전환
- RabbitMQ 기반 전환
- AI 기능
- 배송 담당자 자동 최적 배차
- 거리 기반 담당자 자동 배정
- Redis 적용
- Zipkin 적용
- 전체 테스트 Coverage 확보
- 실제 서버 배포
- 공급업체 상품 자동 입고 시스템

요구사항에 없는 기능을 구현하기 위해 프로젝트 범위를 임의로 확장하지 않는다.

---

# 29. Company Service 핵심 요구사항

Company Service 작업 시 특히 다음 요구사항을 우선 확인한다.

## Company

- 업체 CRUD + Search
- 업체는 특정 Hub에 소속
- 업체 생성/수정 시 Hub 존재 여부 확인
- Hub 확인은 Hub Service API 사용
- `hubId`는 UUID로 참조
- 업체 관리 사용자 ID 저장
- 생성/수정은 해당 업체 사용자 또는 MASTER
- 조회/검색은 로그인 사용자
- 삭제는 Soft Delete

## Product

- 별도 Product Microservice를 만들지 않음
- Company Service 내부 하위 리소스로 관리
- 상품명 / 상품정보 / 가격은 Company Service에서 관리
- 상품은 특정 Company에 소속
- 상품 재고는 Hub Service에서 관리
- Product Entity와 DTO 분리
- 다른 서비스 Entity를 직접 참조하지 않고 UUID 사용
- 상품 수정 시 소속 Company에 대한 권한 확인
- 삭제는 Soft Delete

## Hub 연동

Company Service는 Hub Database를 직접 조회하지 않는다.

```text
Company Service
      ↓
FeignClient
      ↓
Hub Service
```

업체 생성 또는 수정 시 전달받은 Hub ID가 존재하지 않으면 요청을 실패시킨다.

---

## Company Service Swagger

Company Service에서는 최소한 다음 API를 Swagger에서 확인할 수 있도록 한다.

### Company API

- 업체 생성
- 업체 단건 조회
- 업체 목록 / 검색
- 업체 수정
- 업체 삭제

### Product API

- 상품 생성
- 상품 단건 조회
- 상품 목록 / 검색 (Pagination / 정렬 적용)
- 상품 수정
- 상품 삭제

각 API에는 다음 사항을 문서화한다.

```text
요청 값
응답 값
필요 권한
HTTP Status
주요 예외 응답
```

Hub 존재 여부 검증처럼 내부 FeignClient를 통해 처리되는 로직은
외부 API 사용자에게 내부 구현 방식을 노출할 필요는 없지만,
존재하지 않는 Hub ID를 전달했을 때 어떤 오류가 발생하는지는 API 문서에 명시한다.

---

# 30. 구현 우선순위

기능 구현 시 다음 순서로 판단한다.

```text
1. requirements.md의 필수 요구사항

2. 팀에서 합의한 API 명세

3. 현재 프로젝트의 코드 구조

4. 서비스 간 API 계약

5. 팀 협업 규칙

6. 선택 기능
```

필수 요구사항과 선택 기능이 충돌하면 필수 요구사항을 우선한다.

---

# 31. 완료 기준

최소한 다음 조건을 만족해야 한다.

## MSA / Infrastructure

- [ ] Eureka Server 구성
- [ ] API Gateway 구성
- [ ] Gateway 인증 / 인가 적용
- [ ] JWT 인증 구현
- [ ] 서비스별 독립 DB 또는 Schema
- [ ] FeignClient 서비스 간 통신 구현
- [ ] Docker Compose 전체 실행 확인

## Domain

- [ ] Hub 조회 구현
- [ ] 업체 CRUD + Search 구현
- [ ] 업체 Hub 존재 검증 구현
- [ ] 상품 관리 구현
- [ ] 주문 관리 구현
- [ ] 배송 관리 구현
- [ ] 주문과 배송 연동
- [ ] 재고 부족 시 주문 실패
- [ ] 주문 취소 시 재고 복구

## Common

- [ ] Soft Delete 적용
- [ ] 목록 / 검색 Pagination 적용
- [ ] 기본 검색 및 정렬 적용
- [ ] 동일 서비스 FK / 다른 서비스 UUID 참조 정책 적용
- [ ] 로컬 Transaction Rollback 및 서비스 간 실패 복구 시나리오 최소 1개 구현
- [ ] Request Validation 적용
- [ ] 회원가입 USER 기본 Role 적용
- [ ] Gateway 및 각 Microservice 인증 / 인가 검증 적용
- [ ] FeignClient 서비스 간 호출 시 JWT 전달 정책 적용

## Swagger / API Documentation

- [ ] Swagger / OpenAPI 적용
- [ ] Swagger UI 접근 확인
- [ ] API별 설명 작성
- [ ] Request / Response Schema 확인
- [ ] JWT 인증 설정
- [ ] Swagger UI에서 JWT 인증 API 호출 확인
- [ ] 주요 오류 응답 문서화
- [ ] 실제 API 구현과 Swagger 문서 일치 확인

## Documentation

- [ ] ERD 작성
- [ ] API 명세 작성
- [ ] 인프라 설계도 작성