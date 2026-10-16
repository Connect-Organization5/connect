# HANDOFF — Company Service (Product / Hub 연동)

작성일: 2026-10-16 · 브랜치: `feature/product` · 담당 범위: Product 관리, Hub 존재 확인

## 1. 목표와 현재 진행 상황

**목표**: Company Service 안에서 상품(Product) CRUD + 검색/페이지네이션 + 소유권 검증을 구현하고, Hub Service와 OpenFeign으로 연동해 Hub 존재 여부를 확인한다. Company CRUD는 다른 팀원 담당이다.

| 단계 | 상태 |
|---|---|
| Product Entity / Repository / DTO | 완료 (커밋·push) |
| ProductService CRUD + 권한 검증 + 단위 테스트(15) | 완료 (커밋·push) |
| ProductController + MockMvc 테스트(9) | 완료 (커밋·push) |
| Hub 존재 확인 (HubClient / HubValidator / HUB_NOT_FOUND) + 테스트(4) | 구현 완료, **미커밋** |
| Company 생성/수정에 Hub 검증 연결 | 대기 (CompanyService 없음) |
| 업체 삭제 시 Product + Hub Stock 연쇄 soft delete | 대기 (선행 조건 미충족, 6장 참고) |
| 공통 오류 응답 `{code, message}` 반영 | 대기 (팀원 작업 머지 후) |

마지막 전체 테스트: `./gradlew test` **29/29 통과** (contextLoads 1, ProductService 15, ProductController 9, HubValidator 4).

## 2. 내린 결정과 이유

- **패키지 구조** `global / company / product / client`: 팀 합의. DDD/Hexagonal 같은 구조는 도입하지 않았다.
- **Product → Company 단방향 `@ManyToOne(LAZY)`**, `company_id` FK: 같은 서비스 DB이므로 FK를 허용한다. Company Entity에 `List<Product>`는 추가하지 않았다(팀원 코드).
- **URL에 `companyId` 포함**, **Response에 `companyId` 제외**: 팀 확정. PDF 명세의 `/companies/products/...`는 작성 실수로 본다.
- **인증**: Gateway가 JWT를 검증한 뒤 `X-User-Id`, `X-User-Role` 헤더를 전달한다(팀 확정). Company Service는 JWT를 파싱하지 않는다. Role은 `MASTER`/`USER`만 쓴다(PDF의 `admin/user` 표기는 따르지 않음).
- **소유권 기준**: `Company.userId == X-User-Id` 또는 `MASTER`. affiliation(`affiliationType`/`affiliationId`)은 소속 구분용이고, 소유권 로직은 바꾸지 않는다(팀 확정).
- **권한 검증 위치**: Service의 `validateCompanyOwner` 한 곳에만 둔다. Controller는 값을 추출해 넘기기만 한다.
- **조회 검증 순서**: 업체 존재 → 권한 → 상품 조회. 다른 업체의 상품이나 삭제된 상품은 `PRODUCT_NOT_FOUND`(404)로 처리한다.
- **Soft Delete**: BaseEntity의 `softDelete()`를 재사용하고, 모든 조회에 `DeletedAtIsNull` 조건을 넣는다(BaseEntity에 `@SQLRestriction` 없음).
- **Pagination**: `@PageableDefault(size=10, sort=createdAt, DESC)`. size가 100을 넘으면 Controller에서 `BaseException(INVALID_INPUT)`으로 400을 반환한다.
- **성공 응답**: DTO를 그대로 반환하고 `ApiResponse`로 감싸지 않는다. 예외는 삭제 응답 하나로, 명세의 "200 + 삭제 메시지"를 맞추기 위해 기존 `ApiResponse.ok("삭제되었습니다.", null)`을 쓴다.
- **ErrorCode**: 팀원 파일이지만 상수만 추가했다(`FORBIDDEN`, `COMPANY_NOT_FOUND`, `PRODUCT_NOT_FOUND`, `HUB_NOT_FOUND`). Handler와 ApiResponse 구조는 건드리지 않았다(팀원이 공통 예외를 수정 중).
- **CompanyRepository**: Product가 Company를 조회해야 해서 최소 형태(`findByIdAndDeletedAtIsNull`)로 만들었다. 사용자 지시로 **Company 담당 코드가 develop에 반영될 때까지 커밋하지 않고 보류**한다.
- **Hub 연동**: `@FeignClient(name = "hub-service")`로 `GET /hubs/{hubId}`를 호출하고 `ApiResponse<HubResponse(hubId, name, address)>`를 받는다(`origin/feature/hub`에서 확인). **404만 `HUB_NOT_FOUND`로 바꾸고**, 500이나 연결 실패는 그대로 전파해 서버 오류로 처리한다. Retry, CircuitBreaker, fallback은 쓰지 않는다.

## 3. 수정한 파일 (connect.company-service 기준)

### 커밋·push 완료 (`origin/feature/product`)
| 파일 | 요지 |
|---|---|
| `product/entity/Product.java` | `p_company_products`. `create`/`update`(company 변경 불가)/`delete`(soft) |
| `product/repository/ProductRepository.java` | `findByIdAndCompany_IdAndDeletedAtIsNull`, `findByCompany_IdAndDeletedAtIsNull(Pageable)`, `findByCompany_IdAndNameContainingAndDeletedAtIsNull(Pageable)` |
| `product/dto/ProductCreateRequest` / `ProductUpdateRequest` / `ProductResponse` | record. name `@NotBlank @Size(100)`, description `@Size(200)`, price `@NotNull @DecimalMin(0) @Digits(8,2)` |
| `product/service/ProductService.java` | CRUD, 검색(name 없으면 전체), 소유권 검증, `PagedResponse` 반환 |
| `product/controller/ProductController.java` | 5개 API, 헤더 추출, size ≤ 100 검사, 등록 201 |
| `global/exception/ErrorCode.java` | `FORBIDDEN`, `COMPANY_NOT_FOUND`, `PRODUCT_NOT_FOUND` 추가 |
| `test/.../ProductServiceTest.java` | 15개 (등록/수정/삭제 × 소유자·MASTER·권한없음, 미존재, 검색, soft delete) |
| `test/.../ProductControllerTest.java` | `@WebMvcTest` + `@MockitoBean`, 9개 |

### 미커밋 (로컬에만 있음)
| 파일 | 요지 |
|---|---|
| `company/repository/CompanyRepository.java` | ⚠ **ProductService가 import함 → 원격 `feature/product`는 이 파일이 없어 컴파일 실패.** 보류 지시 상태 |
| `CompanyServiceApplication.java` | `@EnableFeignClients` 추가 |
| `client/hub/HubClient.java`, `client/hub/dto/HubResponse.java` | Hub 단건 조회 Feign |
| `client/hub/HubValidator.java` | `validateHubExists(UUID)` (404 → `HUB_NOT_FOUND`) |
| `global/exception/ErrorCode.java` | `HUB_NOT_FOUND` 추가 (미커밋 변경분) |
| `test/.../client/hub/HubValidatorTest.java` | 정상 / 404 / 500 / 연결 실패, 4개 |
| `src/main/resources/application.yaml` | 사용자가 기본 DB를 `connect_db`로 수정한 로컬 변경. 내가 만든 변경 아님 |

## 4. 남은 작업 / 알려진 이슈

1. **원격 브랜치 컴파일 실패**: `CompanyRepository` 미커밋 때문이다. Company 담당 코드가 develop에 들어오면 비교해서 정리한다.
2. **공통 오류 응답 전환**: `origin/feature/company`에 `18f289c feat: 에러 응답 형식 변경`이 올라와 있다. 머지할 때 해야 할 일:
   - 추가 ErrorCode 상수 4개 병합
   - 삭제 응답과 size 오류 응답을 `{code, message}` 기준으로 재검토
   - 테스트의 오류 본문 검증 수정
3. **잘못된 sort 파라미터(`?sort=foo`) → 500**: `PropertyReferenceException`이 처리되지 않는다. 공통 예외 단계에서 400으로 처리하기로 했다.
4. **Hub 검증 연결**: CompanyService가 생기면 생성/수정에서 저장 전에 `hubValidator.validateHubExists(request.hubId())`를 호출한다.
5. **업체 삭제 연쇄 soft delete**(Company → Product → Hub Stock): 선행 조건이 없어 보류했다.
   - 필요한 것: `CompanyService.deleteCompany`(팀원), Company soft delete 메서드(`softDelete`가 protected), Hub Stock Entity와 `productIds` 일괄 soft delete API(Hub 담당, 경로와 요청 형식 협의 필요).
   - 이후 내 작업:
     - `HubClient` 메서드 + 요청 DTO
     - `ProductRepository.findAllByCompany_IdAndDeletedAtIsNull(UUID)` (List)
     - ProductService 일괄 삭제(`productId` 목록 반환)
     - `deleteCompany` 연결과 테스트
   - 정책: Hub 실패 시 예외를 전파해 로컬 롤백한다. Saga/Outbox는 쓰지 않는다. Product가 없으면 Hub 호출을 생략한다.
6. **DB 환경변수**: yaml은 `COMPANY_DB_URL`을 읽는다. 팀 DB 이름은 `company-db`이고, `JPA_DDL_AUTO`는 미반영 상태다(공통 설정 정리 단계로 보류).
7. **PDF API 명세 갱신 필요**: Product URL에 `companyId`를 넣고 응답에서 `companyId`를 빼야 한다.
8. **Order 연동 참고**: 주문 생성 요청에는 `productId`만 있다. Order Service가 `companyId` 없이 상품을 조회하려면 별도 API가 필요할 수 있다(팀 협의).

## 5. 다음 세션을 위한 맥락

- **프로젝트 구조**: 루트는 Multi Module이 아니다. `connect.company-service`, `connect.eureka-server`, `connect.eureka-gateway`가 각각 독립 Gradle 프로젝트다(Java 21, Boot 4.1.1, Cloud 2025.1.3, Gradle 9.7.1).
- **원격 브랜치**: `master`, `develop`, `feature/company`(Company + 공통 코드), `feature/hub`(hub-service), `feature/order`, `feature/user`. PR 대상은 `develop`.
- **규칙 문서**: `connect.company-service/CLAUDE.md`, `docs/`(requirements.md, API 명세서.pdf, ERD 이미지). 로컬 전용이며 커밋하지 않는다.
- **테스트 실행**: PostgreSQL 컨테이너 `company-postgres`(DB `company-db`)가 필요하다.
  ```bash
  cd connect.company-service && set -a && . ./.env && set +a && ./gradlew test
  ```
  - `.env`에는 `COMPANY_DB_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`가 있어야 한다.
  - Gradle과 IntelliJ는 `.env`를 자동으로 읽지 않는다. IntelliJ는 Run Configuration 환경변수에 직접 넣는다.
- **로그의 Eureka 오류**: Eureka(8761)를 띄우지 않으면 등록 실패 경고가 나오지만 테스트에는 영향이 없다.
- **Boot 4 테스트 주의**:
  - `@WebMvcTest`는 `org.springframework.boot.webmvc.test.autoconfigure`에 있다.
  - `@EnableJpaAuditing` 때문에 `@MockitoBean(types = JpaMetamodelMappingContext.class)`가 필요하다.
  - Jackson 3을 쓰므로 테스트 JSON은 문자열로 작성한다.
- **작업 방식(사용자 선호)**:
  - 단계별로 구현 → 컴파일/테스트 → 학습용 설명 순서로 진행한다.
  - commit, push, merge, checkout은 명시적으로 요청받았을 때만 한다.
  - 팀원 영역(company, global 구조, Hub Service)은 수정 전에 먼저 보고한다.
