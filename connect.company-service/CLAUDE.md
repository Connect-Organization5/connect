# CLAUDE.md

## 1. 프로젝트 개요

이 프로젝트는 Spring 기반 MSA 프로젝트이다.

각 도메인은 독립적인 Microservice로 구성하며, 다른 서비스의 데이터베이스에 직접 접근하지 않는다.

현재 내가 담당하는 범위는 **Company Service**이며 주요 담당 기능은 다음과 같다.

- 상품 관리
- Company Service에서 필요한 Hub 존재 여부 확인
- 위 기능에 필요한 Company Service 내부 로직 및 테스트

작업 범위를 불필요하게 확장하지 않는다.

---

## 2. 가장 먼저 확인할 것

작업을 시작하기 전에 반드시 다음 순서로 확인한다.

1. `docs/` 디렉토리의 요구사항 문서 확인
2. Company Service의 현재 코드 구조 확인
3. 기존 Entity / DTO / Repository / Service / Controller 구조 확인
4. 기존 예외 처리 방식 확인
5. 다른 서비스와 통신하는 기존 방식 확인
6. 테스트 코드가 있다면 테스트 작성 패턴 확인

구현 요구사항의 기준은 `docs/`에 있는 문서이다.

문서와 현재 코드가 충돌하는 경우 임의로 판단하여 큰 구조를 변경하지 말고,
어떤 부분이 충돌하는지 먼저 설명한다.

요구사항에 없는 기능을 임의로 추가하지 않는다.

---

# 3. 담당 범위

## Company Service

현재 주 작업 대상은 Company Service이다.

특히 다음 기능을 담당한다.

### 상품 관리

상품 관련 기능은 반드시 `docs/`의 요구사항과 API 명세를 기준으로 구현한다.

- 상품 등록
- 상품 단건 조회
- 상품 목록 조회
- 상품 검색
- 상품 수정
- 상품 삭제
- 목록/검색 조회 시 Pagination 및 정렬 적용

단, 실제 요구사항에서 지원하지 않는 기능은 추가하지 않는다.

Controller에서 비즈니스 로직을 직접 처리하지 않는다.

기본 흐름은 다음 구조를 따른다.

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
```

Request / Response DTO와 Entity를 분리한다.

Entity를 API Response로 직접 반환하지 않는다.

---

## Hub 존재 확인

Company Service에서 Hub 정보가 필요한 경우
**Hub Service의 DB를 직접 조회하지 않는다.**

서비스 간 데이터 참조는 UUID를 사용한다.

```text
Company Service
      ↓
Hub Client
      ↓
Hub Service API
      ↓
Hub 존재 여부 확인
```

프로젝트에서 FeignClient를 사용하고 있다면 기존 FeignClient 패턴을 따른다.

새로운 방식의 HTTP Client를 임의로 추가하지 않는다.

예:

```text
Company 생성 또는 수정 요청
        ↓
hubId 전달
        ↓
Hub Service에 존재 여부 확인
        ↓
존재
 → Company 로직 계속 진행

존재하지 않음
 → 적절한 비즈니스 예외 발생
```

Hub Service의 응답 형식과 Endpoint는 반드시 `docs/`의 API 명세 또는
현재 Hub Service의 공개 API를 확인한 뒤 사용한다.

Hub Service의 코드를 임의로 변경하지 않는다.

Hub 연동에 필요한 API가 존재하지 않을 경우
Hub Service를 직접 수정하지 말고 필요한 API와 이유를 먼저 알려준다.

네트워크 오류, Hub 없음, 서버 오류를 임의로 같은 상황으로 처리하지 않는다.

Hub 존재 여부 검증은 `docs/requirements.md`에 명시된 기능에서만 수행한다.

Product 기능에 불필요한 Hub 검증을 임의로 추가하지 않는다.

---

# 4. MSA 원칙

다른 Microservice의 Database를 직접 조회하지 않는다.

금지:

```text
Company Service
      ↓
Hub DB 직접 접근
```

허용:

```text
Company Service
      ↓
Hub Service API
      ↓
Hub Service
```

동일 Microservice 내부 Entity 간 관계는 필요한 경우 FK와 JPA 연관관계를 사용할 수 있다.

다른 Microservice의 Entity와는 FK 및 JPA 연관관계를 사용하지 않고 UUID만 저장한다.

예:

```text
Company ↔ Product
→ 같은 Company Service이므로 FK / JPA 연관관계 사용 가능

Company → Hub
→ 다른 Microservice이므로 hubId UUID만 저장하고 API를 통해 확인
```

다른 서비스의 Entity를 Company Service에서 공유하지 않는다.

서비스 간에는 다음 데이터만 전달한다.

- UUID
- Request DTO
- Response DTO
- 필요한 최소 데이터

다른 서비스의 내부 구현에 의존하지 않는다.

---

# 5. API 변경 규칙

다음 항목을 변경하기 전에 영향 범위를 확인한다.

- API URL
- HTTP Method
- Request 형식
- Response 형식
- Enum
- Entity
- Column
- Table
- 서비스 간 API 계약

다른 서비스에서 사용 중인 API를 임의로 변경하지 않는다.

API 변경이 필요할 경우 코드부터 수정하지 말고 다음을 먼저 알려준다.

```text
변경 대상:
변경 이유:
영향받는 서비스:
기존 형식:
변경 예정 형식:
```

---

# 6. Database 규칙

각 Microservice는 자신의 Database만 관리한다.

Company Service에서는 Company Service가 소유하는 데이터만 직접 조회한다.

동일 Company Service 내부 Entity 간에는 필요한 경우 FK와 JPA 연관관계를 사용할 수 있다.

다른 서비스의 데이터는 UUID로 참조한다.

Entity 또는 Database 구조를 변경할 경우 기존 데이터를 고려한다.

기존 프로젝트에 migration 도구가 있다면 해당 방식을 따른다.

DDL 설정이나 Database 구조를 임의로 변경하지 않는다.

---

# 7. 예외 처리

프로젝트의 공통 예외 응답 형식을 따른다.

```json
{
  "code": "ORDER_NOT_FOUND",
  "message": "주문을 찾을 수 없습니다."
}
```

HTTP Status 기본 기준:

```text
400 Bad Request
→ 잘못된 요청 / Validation 실패

401 Unauthorized
→ 인증 실패

403 Forbidden
→ 인증은 되었지만 권한 없음

404 Not Found
→ 요청한 리소스가 존재하지 않음

409 Conflict
→ 현재 상태에서 수행할 수 없는 비즈니스 요청

500 Internal Server Error
→ 예상하지 못한 서버 오류
```

새로운 예외 형식을 임의로 만들지 말고 기존 Global Exception Handler와
Custom Exception 구조가 있다면 그대로 사용한다.

---

# 8. 인증 / 인가

기능 구현 전에 해당 API의 접근 권한을 `docs/`에서 확인한다.

Controller에서 단순히 사용자 요청을 신뢰하지 않는다.

다음과 같은 값은 반드시 서버에서 검증한다.

- 요청 사용자의 권한
- 요청 리소스에 대한 접근 권한
- UUID로 전달된 외부 리소스의 존재 여부

클라이언트에서 버튼이나 입력창을 숨기는 것으로 권한 검증을 대체하지 않는다.

인가 검증은 서버에서 수행한다.

## Gateway와 Microservice의 책임

Gateway와 각 Microservice에서 모두 인가 검사를 수행한다.

Gateway는 다음을 담당한다.

- JWT 유효성 확인
- 로그인 여부 확인
- API 접근에 필요한 기본 Role 확인

각 Microservice는 다음을 담당한다.

- 전달받은 사용자 Role 확인
- 실제 리소스 소유권 확인
- 도메인별 비즈니스 권한 확인

예:

```text
상품 수정 요청
    ↓
Gateway
→ JWT / 기본 Role 확인
    ↓
Company Service
→ Product가 속한 Company 조회
→ Company.userId와 로그인 userId 비교
→ 본인 또는 MASTER인지 확인
```

## JWT Claim

Company Service에서 사용하는 JWT Claim 이름은 다음을 기준으로 한다.

```text
userId
role
```

- `userId`: 사용자 UUID
- `role`: `USER` 또는 `MASTER`

업체 관리자 여부는 별도의 Role을 추가하지 않고
Company의 `userId`와 JWT의 `userId`를 비교하여 판단한다.

JWT의 서명 방식, Secret, 사용 라이브러리 등은
User Service와 팀의 실제 구현을 우선한다.

임의로 다른 Claim 이름이나 JWT 구조를 만들지 않는다.

## JWT 전달

서비스 간 FeignClient 호출 시 인증이 필요한 경우 최초 요청의 JWT를
`Authorization` Header에 그대로 전달한다.

```http
Authorization: Bearer {JWT}
```

Gateway를 통과한 이후 Microservice 간 호출에서도 인증 정보가 필요한 경우
동일 JWT를 전달한다.

JWT 전달 방식은 팀에서 합의한 기존 구현을 우선하며,
새로운 Header 이름이나 별도 인증 방식을 임의로 만들지 않는다.

---

# 9. Transaction

데이터를 생성, 수정, 삭제하는 Service 로직은
기존 프로젝트의 Transaction 패턴을 따른다.

조회 메서드에 `readOnly = true`를 사용하는 기존 규칙이 있다면 동일하게 적용한다.

외부 서비스 호출이 포함된 경우
DB Transaction과 외부 API 호출의 경계를 확인한다.

FeignClient 호출 실패를 무조건 DB 오류처럼 처리하지 않는다.

동일 Microservice 내부의 DB 변경은 Spring Transaction으로 처리할 수 있다.

다른 Microservice의 데이터 변경은 Company Service의 로컬 Transaction으로
Rollback할 수 있다고 가정하지 않는다.

외부 서비스의 데이터가 이미 변경된 뒤 후속 작업이 실패하는 경우,
필요하면 별도의 보상 API를 통해 복구한다.

로컬 DB Transaction과 분산 서비스 간 실패 복구를 구분한다.

---

# 10. 코드 작성 원칙

기존 프로젝트의 코드 스타일을 최우선으로 따른다.

새로운 패턴을 임의로 도입하지 않는다.

다음 구조가 이미 존재하면 재사용한다.

- BaseEntity
- 공통 Response
- GlobalExceptionHandler
- Custom Exception
- FeignClient 설정
- Security 설정
- 공통 Enum
- UUID 처리 방식

불필요한 추상화를 만들지 않는다.

과제 요구사항을 해결하는 데 필요한 최소한의 코드만 작성한다.

코드 중복이 명확하게 발생하는 경우에만 리팩터링을 고려한다.

---

# 10-1. 단위별 구현 및 코드 설명 규칙

기능을 한 번에 전체 구현하지 않는다.

가능한 한 기능을 의미 있는 작은 작업 단위로 나누어 순서대로 구현한다.

기본 작업 단위 예시는 다음과 같다.

```text
1. 공통 기반
   - BaseEntity
   - Auditing
   - 공통 Exception

2. Entity
   - Company
   - Product
   - 연관관계

3. Repository
   - 기본 조회
   - Soft Delete 조회
   - Pagination / 검색

4. DTO
   - Request DTO
   - Response DTO
   - Validation

5. Service
   - 등록
   - 조회
   - 수정
   - 삭제
   - 권한 / 소유권 검증

6. Controller
   - API Endpoint
   - Request / Response 연결

7. 외부 서비스 연동
   - FeignClient
   - Hub 존재 확인
   - JWT Header 전달
   - 외부 호출 예외 처리

8. Swagger
   - API 설명
   - Request / Response Schema
   - 인증 설정

9. Test
   - 정상 케이스
   - 예외 케이스
   - 권한 케이스
   - 외부 서비스 실패 케이스
```

실제 프로젝트 구조에 따라 작업 단위는 조정할 수 있지만,
관련 없는 여러 계층과 기능을 한 번에 대규모로 작성하지 않는다.

---

## 각 작업 단위의 진행 방식

각 작업 단위는 반드시 다음 순서로 진행한다.

```text
현재 단계 설명
    ↓
관련 기존 코드 확인
    ↓
해당 단계 코드 구현
    ↓
컴파일 또는 관련 테스트 확인
    ↓
방금 작성한 코드 설명
    ↓
다음 단계 진행
```

한 번의 요청에 여러 단계가 포함되어 있더라도 다음 흐름을 유지한다.

```text
1단계 구현
→ 1단계 설명

2단계 구현
→ 2단계 설명

3단계 구현
→ 3단계 설명
```

모든 코드를 먼저 작성한 뒤 마지막에 한꺼번에 설명하지 않는다.

---

## 단계별 코드 설명 형식

각 작업 단위 구현이 끝나면 다음 형식으로 설명한다.

```markdown
## 이번 단계에서 구현한 것

## 생성 / 수정한 파일

## 코드 흐름

## 주요 코드 설명

## 사용한 Spring / Java 개념

## 이렇게 구현한 이유

## 주의해서 볼 부분

## 다음 단계
```

단순히 "구현 완료"라고만 보고하지 않는다.

---

## 코드 흐름 설명

클래스별 역할만 나열하지 말고
실제 요청이 어떤 순서로 처리되는지도 설명한다.

예:

```text
POST /companies/{companyId}/products
        ↓
ProductController
        ↓
ProductService
        ↓
CompanyRepository
→ Company 존재 여부 / 권한 확인
        ↓
Product 생성
        ↓
ProductRepository.save()
        ↓
ProductResponse 반환
```

가능하면 실제 구현한 클래스와 메서드 이름을 이용해 설명한다.

---

## 주요 코드 설명

중요한 코드는 단순히 무엇을 하는 코드인지 설명하는 데 그치지 않고,
왜 해당 방식으로 구현했는지도 함께 설명한다.

예:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "company_id", nullable = false)
private Company company;
```

위와 같은 코드를 작성했다면 최소한 다음 내용을 설명한다.

```text
- Product 여러 개가 하나의 Company에 속하므로 ManyToOne을 사용한 이유
- Company와 Product가 같은 Microservice이므로 실제 FK를 사용할 수 있는 이유
- LAZY Loading을 사용한 이유
- company_id가 어떤 방식으로 FK가 되는지
```

Annotation을 사용했다면 이번 구현을 이해하는 데 중요한 Annotation을 설명한다.

예:

```text
@Entity
@Table
@Id
@GeneratedValue
@ManyToOne
@JoinColumn
@Transactional
@Valid
@NotBlank
@FeignClient
```

Java 또는 Spring의 모든 문법을 사전식으로 설명하지 않는다.

이번 구현에서 중요한 개념과 실제 동작을 중심으로 설명한다.

---

## 기존 코드와 연결해서 설명

새로운 코드만 독립적으로 설명하지 않는다.

가능하면 다음 내용도 함께 설명한다.

```text
이 코드가 이전 단계에서 만든 어떤 코드와 연결되는지

이 코드가 다음 단계에서 어떻게 사용되는지

실제 요청이 들어왔을 때 어떤 순서로 실행되는지

DB에는 어떤 변화가 발생하는지

다른 Microservice와 어디에서 통신하는지
```

---

## 학습을 위한 설명 수준

설명은 Spring을 학습 중인 개발자가
작성된 코드를 다시 혼자 읽고 이해할 수 있는 수준으로 작성한다.

특히 이번 구현에 해당되는 경우 다음 내용을 생략하지 않는다.

```text
왜 이 Annotation을 사용했는지

왜 Entity와 DTO를 분리했는지

왜 이 Repository 메서드가 필요한지

@Transactional의 범위

JPA 연관관계 방향

LAZY Loading

Soft Delete 처리

권한 검증 위치

FeignClient 호출 흐름

JWT에서 사용자 정보를 사용하는 과정

예외가 GlobalExceptionHandler까지 전달되는 흐름
```

단순히 코드 한 줄씩 한국어로 번역하는 식으로 설명하지 않는다.

코드가 전체 시스템에서 어떤 역할을 하는지를 중심으로 설명한다.

---

## 기존 코드를 수정한 경우

기존 코드를 수정했다면 가능하면 다음 내용을 설명한다.

```text
기존 코드가 어떻게 동작했는지

무엇을 변경했는지

왜 변경했는지

변경으로 영향을 받는 부분
```

---

## 여러 파일을 동시에 수정하는 경우

하나의 작업 단위에서 서로 직접적으로 연결되는 파일은 함께 수정할 수 있다.

예:

```text
상품 등록 기능
├─ ProductCreateRequest
├─ ProductService
├─ ProductController
└─ ProductResponse
```

하지만 관련성이 낮은 기능을 한 단계에서 모두 구현하지 않는다.

예:

```text
Product CRUD 전체
+ Hub FeignClient
+ Security 전체
+ Swagger
+ 모든 Test
```

위와 같은 범위는 각각 의미 있는 작업 단위로 분리한다.

---

## 설명 없이 다음 단계로 넘어가지 않는다

각 작업 단위가 끝나면 해당 단계의 구현 내용을 설명한 뒤 다음 단계로 이동한다.

사용자가 특정 단계까지만 요청한 경우에는
그 이후 단계를 임의로 구현하지 않는다.

---

# 11. DTO 규칙

API별 Request / Response DTO를 사용한다.

사용자가 수정해서는 안 되는 값을 수정 DTO에 포함하지 않는다.

예:

```text
일반 사용자가 수정 가능한 필드
→ Request DTO에 포함

서버에서 결정해야 하는 필드
→ Request DTO에서 제외
```

`@Valid`, `@NotNull`, `@NotBlank` 등 Validation은
요구사항과 기존 프로젝트 패턴에 맞게 적용한다.

Validation과 비즈니스 검증을 구분한다.

예:

```text
@NotNull
→ 요청 형식 검증

Hub 존재 여부
→ Service 비즈니스 검증
```

---

# 12. Swagger / OpenAPI

Company Service의 API는 Swagger / OpenAPI로 문서화한다.

최소한 다음 API를 문서화한다.

- Company API
- Product API

각 API에는 가능한 범위에서 다음을 명시한다.

- API 기능 설명
- Request
- Response
- 필요한 권한
- HTTP Status
- 주요 오류 응답

JWT 인증이 필요한 API는 Swagger UI에서 Bearer Token을 입력하여
테스트할 수 있도록 한다.

Swagger 문서는 실제 구현과 일치하도록 유지한다.

API URL, Request, Response 또는 권한 조건이 변경되면
Swagger 문서도 함께 수정한다.

Entity를 Swagger Request / Response 모델로 직접 사용하지 않는다.

Swagger 관련 라이브러리나 설정이 이미 존재하면 기존 구성을 재사용한다.

임의로 다른 Swagger/OpenAPI 라이브러리를 추가하지 않는다.

---

# 13. 테스트

기능 구현 후 최소한 관련 테스트를 수행한다.

우선순위:

1. 정상 동작
2. 존재하지 않는 데이터
3. 잘못된 요청
4. 권한이 없는 요청
5. 외부 서비스 연동 실패
6. 비즈니스 규칙 위반

Hub Service 연동 테스트에서는 실제 Hub DB에 직접 접근하지 않는다.

단위 테스트라면 Hub Client를 Mock 처리한다.

프로젝트 전체 테스트를 깨뜨리지 않는지 확인한다.

Gradle 프로젝트라면 작업 완료 후 가능한 범위에서 다음을 확인한다.

```bash
./gradlew test
```

필요한 경우:

```bash
./gradlew build
```

테스트 실패 시 테스트를 삭제하거나 비활성화하여 통과시키지 않는다.

실패 원인을 확인하고 수정한다.

테스트 코드를 작성한 경우에도 다음 내용을 설명한다.

```text
무엇을 검증하는 테스트인지

왜 이 테스트가 필요한지

Mock 처리한 대상은 무엇인지

실제 코드의 어떤 동작을 검증하는지

정상 케이스와 예외 케이스의 차이
```

---

# 14. Git 규칙

브랜치 전략:

```text
main
└─ develop
   ├─ feature/*
   ├─ fix/*
   └─ refactor/*
```

규칙:

- `main` 직접 push 금지
- `develop` 직접 push 금지
- 기능 개발은 `feature/*`
- 버그 수정은 `fix/*`
- 리팩터링은 `refactor/*`
- 작업 완료 후 `develop` 대상으로 PR 생성

현재 브랜치를 먼저 확인하고 작업한다.

---

# 15. Commit Convention

다음 Prefix를 사용한다.

```text
feat: 기능 추가
fix: 버그 수정
refactor: 기능 변경 없는 구조 개선
test: 테스트 추가 또는 수정
docs: 문서 수정
chore: 설정 / 빌드 관련 수정
```

예:

```text
feat: 상품 등록 기능 구현

feat: Hub 존재 확인 Client 추가

fix: 존재하지 않는 Hub 등록 오류 수정

test: 상품 등록 서비스 테스트 추가
```

하나의 Commit에 관련 없는 변경사항을 섞지 않는다.

Claude가 임의로 commit 또는 push하지 않는다.
사용자가 명시적으로 요청한 경우에만 수행한다.

---

# 16. PR 작성 규칙

PR 대상:

```text
feature/* → develop
fix/* → develop
refactor/* → develop
```

PR 설명:

```markdown
## 작업 내용
- 상품 등록 기능 구현
- Hub 존재 여부 확인 로직 추가

## 확인 사항
- 상품 등록 정상 동작 확인
- 존재하지 않는 Hub 요청 예외 확인
- 테스트 통과 확인

## 관련 서비스
- Company
- Hub
```

---

# 17. 환경변수 / 보안

민감 정보는 코드에 직접 작성하지 않는다.

금지:

```yaml
password: 1234
jwt-secret: my-secret
```

사용:

```yaml
spring:
  datasource:
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}

jwt:
  secret: ${JWT_SECRET}
```

`.env`는 Git에 Commit하지 않는다.

공유가 필요한 환경변수 이름은 `.env.example`을 사용한다.

다음 값은 로그에도 출력하지 않는다.

- Password
- JWT Secret
- Access Token
- Refresh Token
- API Key
- Slack Webhook URL
- Database Password

---

# 18. 작업 수행 방식

새로운 작업 요청을 받으면 바로 전체 코드를 작성하지 않는다.

먼저 요구사항과 기존 코드를 확인하고,
전체 작업을 의미 있는 작은 단위로 나누어 순서대로 구현한다.

---

## 1. 요구사항 확인

`docs/`에서 관련 요구사항을 찾는다.

특히 다음 내용을 확인한다.

```text
기능 요구사항
API 명세
ERD
권한
서비스 간 연동
예외 처리
```

---

## 2. 기존 코드 분석

관련 코드를 확인한다.

```text
Entity
DTO
Repository
Service
Controller
Exception
Security
FeignClient
Test
```

기존 구현이 있다면 최대한 재사용한다.

---

## 3. 영향 범위 판단

수정해야 하는 파일과 다른 서비스에 미치는 영향을 확인한다.

특히 다음을 확인한다.

```text
다른 팀원의 코드와 충돌할 가능성

API 계약 변경 여부

Entity / DB 구조 변경 여부

다른 Microservice와의 연동 여부
```

---

## 4. 작업 단위 나누기

전체 기능을 바로 구현하지 않는다.

이번 요청을 의미 있는 작업 단위로 나눈다.

예:

```text
Product 기능 구현

1단계 - Product Entity
2단계 - Product Repository
3단계 - Product DTO
4단계 - Product Service
5단계 - Product Controller
6단계 - 권한 / 소유권 검증
7단계 - Swagger
8단계 - Test
```

필요하면 더 작은 단위로 나눈다.

---

## 5. 현재 단계 구현

현재 단계에 필요한 코드만 작성한다.

해당 단계와 직접 관련 없는 이후 기능을 미리 대규모로 구현하지 않는다.

기존 코드가 있다면 새로 만들지 않고 재사용하거나 필요한 부분만 수정한다.

---

## 6. 단계별 검증

가능한 경우 현재 단계가 정상적으로 컴파일되는지 확인한다.

관련 테스트가 있다면 해당 테스트를 실행한다.

문제가 발생하면 테스트를 삭제하거나 우회하지 말고 원인을 확인한다.

---

## 7. 구현 직후 코드 설명

현재 단계 구현이 끝나면
다음 단계로 넘어가기 전에 방금 작성한 코드를 설명한다.

다음 형식을 사용한다.

```markdown
## 이번 단계에서 구현한 것

## 생성 / 수정한 파일

## 코드 흐름

## 주요 코드 설명

## 사용한 Spring / Java 개념

## 이렇게 구현한 이유

## 주의해서 볼 부분

## 다음 단계
```

설명에서는 최소한 다음 내용을 다룬다.

```text
각 클래스의 역할

클래스 사이의 연결 관계

중요한 Annotation

실제 요청이 처리되는 흐름

DB와 연결되는 부분

JPA가 동작하는 방식

권한 검증 위치

예외 발생 및 처리 흐름

외부 서비스가 있다면 호출 과정
```

단순히 코드 한 줄씩 읽어주는 설명은 하지 않는다.

---

## 8. 다음 단계 진행

현재 단계의 구현과 설명이 끝난 뒤 다음 단계로 진행한다.

한 번의 요청에서 여러 단계를 처리하더라도 반드시 다음 흐름을 유지한다.

```text
구현
↓
검증
↓
설명
↓
다음 단계 구현
```

모든 구현을 먼저 끝낸 다음 마지막에 한꺼번에 설명하지 않는다.

---

## 9. 전체 검증

요청된 범위의 모든 단계가 끝나면 전체 테스트를 수행한다.

```bash
./gradlew test
```

필요한 경우:

```bash
./gradlew build
```

도 수행한다.

테스트 또는 빌드 실패가 남아 있다면 숨기지 말고
실패 원인과 현재 상태를 설명한다.

---

## 10. 최종 정리

전체 작업이 끝난 뒤 다음 형식으로 최종 요약한다.

```markdown
## 전체 구현 내용

## 생성한 파일

## 수정한 파일

## 전체 요청 흐름

## 핵심 Spring / Java 개념

## 주요 설계 이유

## 테스트 결과

## 다른 서비스와 연동이 필요한 부분

## 아직 구현하지 않은 부분

## 추가로 확인해야 할 부분
```

단계별 설명에서 이미 자세히 설명한 내용을
최종 정리에서 불필요하게 전부 반복하지 않는다.

---

# 19. 절대 하지 말 것

다음 작업은 명시적인 요청 없이 수행하지 않는다.

- 다른 Microservice의 DB 직접 접근
- 다른 Microservice Entity와 FK/JPA 연관관계 생성
- Hub Service 내부 코드 임의 수정
- 다른 팀원 담당 기능 대규모 수정
- API 명세 임의 변경
- Entity / Table 구조 임의 변경
- 기존 인증 / 인가 정책 변경
- 새로운 라이브러리 임의 추가
- 테스트 삭제 또는 비활성화
- `.env` Commit
- Secret 코드 하드코딩
- `main` 직접 push
- `develop` 직접 push
- 요구사항에 없는 기능 추가
- 전체 프로젝트 구조 리팩터링
- 분산 서비스 변경을 단일 `@Transactional`로 Rollback 가능하다고 가정
- Product 기능에 요구사항 없는 Hub 검증 임의 추가
- 여러 작업 단위를 설명 없이 한 번에 대규모로 구현
- 모든 코드를 먼저 작성하고 설명을 마지막에 몰아서 작성

현재 담당 기능을 구현하기 위해 다른 영역의 수정이 필요하면
먼저 수정이 필요한 이유와 영향 범위를 알려준다.

---

# 20. 가장 중요한 원칙

우선순위는 다음과 같다.

```text
1. docs의 요구사항
2. 팀에서 합의한 API 명세
3. 기존 프로젝트 구조와 코드 패턴
4. 협업 규칙
5. 최소한의 변경으로 기능 구현
6. 단위별 구현 후 코드 이해가 가능하도록 설명
```

추측해서 구현하지 않는다.

확인 가능한 내용은 코드와 문서를 먼저 확인한다.

특히 Company Service와 Hub Service 사이에서는
**서비스 경계를 지키고 API를 통해서만 데이터를 확인한다.**

기능 구현은 가능한 한 다음 흐름을 유지한다.

```text
요구사항 확인
    ↓
기존 코드 확인
    ↓
작업 단위 분리
    ↓
한 단계 구현
    ↓
검증
    ↓
작성한 코드 설명
    ↓
다음 단계 구현
    ↓
전체 테스트 / 빌드
    ↓
최종 정리
```

목표는 단순히 동작하는 코드를 만드는 것이 아니라,
**작성된 코드가 왜 필요한지, 어떤 흐름으로 동작하는지 사용자가 이해할 수 있도록 구현하는 것**이다.