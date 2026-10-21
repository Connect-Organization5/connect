# Connect — MSA 기반 물류 관리 시스템

허브(물류 거점)·업체·상품·재고·주문·배송을 서비스별로 나누어 관리하는 **Spring Cloud 기반 마이크로서비스** 프로젝트입니다.
모든 외부 요청은 **API Gateway** 한 곳으로 들어와 JWT 인증을 거친 뒤, **Eureka**에 등록된 각 서비스로 전달됩니다.

<br>

## 목차

1. [프로젝트 소개](#1-프로젝트-소개)
2. [팀원 역할 분담](#2-팀원-역할-분담)
3. [기술 스택](#3-기술-스택)
4. [아키텍처](#4-아키텍처)
5. [ERD](#5-erd)
6. [서비스 구성](#6-서비스-구성)
7. [인증과 인가](#7-인증과-인가)
8. [주요 비즈니스 흐름](#8-주요-비즈니스-흐름)
9. [실행 방법](#9-실행-방법)
10. [API](#10-api)
11. [테스트](#11-테스트)
12. [프로젝트 구조](#12-프로젝트-구조)
13. [협업 규칙](#13-협업-규칙)

<br>

## 1. 프로젝트 소개

B2B 물류에서는 주문, 재고, 배송이 서로 다른 조직과 시스템에 흩어져 있어 하나의 주문이 어디까지 처리됐는지 추적하기 어렵습니다. Connect는 이 흐름을 서비스 단위로 나누어 구현하면서, **서비스 간 호출이 실패해도 데이터가 어긋나지 않는 구조**를 목표로 했습니다.

| 목표 | 구현 |
| --- | --- |
| 도메인 분리 | 사용자, 업체·상품, 허브·재고, 주문, 배송을 독립된 서비스와 DB로 분리. 다른 서비스의 데이터는 DB가 아닌 API로만 조회 |
| 일관된 인증·인가 | Gateway에서 JWT를 한 번 검증하고 사용자 정보를 헤더로 전달해, 각 서비스가 같은 사용자 정보로 권한 판단 (User Service는 JWT를 자체 재검증) |
| 실패에 강한 주문 처리 | 재고 차감 → 배송 생성 중 실패하면 보상 호출(배송 취소·재고 복원)로 앞 단계를 되돌림. 주요 내부 API는 재시도해도 한 번만 반영되도록 멱등 설계. Slack 알림은 주문 생성 후 비동기로 보내며, 알림 실패는 주문 결과에 영향을 주지 않음 |
| 추적 가능성 | Zipkin 분산 추적으로 한 요청이 여러 서비스를 거치는 흐름 확인 |

<br>

## 2. 팀원 역할 분담

| 팀원 | 담당 | 주요 작업 |
| --- | --- | --- |
| 김효진 | Product | 상품 CRUD와 검색, Gateway가 전달한 `X-User-*` 헤더 기반 업체 관리자 권한 검증, 주문·재고 연동을 위한 상품 조회 API, Gateway의 JWT 사용자 정보 헤더 전달 |
| 장수현 | Company | 업체 CRUD와 검색, MASTER 업체 등록·관리자 지정, 업체 삭제 시 상품·재고 연쇄 삭제 |
| 이안근 | Order | 주문 생성·수정·삭제 흐름 조율, 보상 처리, 재고 재조정 순번 관리, Slack 비동기 알림 |
| 조민영 | Delivery | 배송·배송 경로 기록 생성·수정·삭제, 조회와 검색, 담당자 자동 배정, 상태 전이 |
| 이창열 | Hub | 허브·허브 경로 조회, 재고 등록·입고, 재고 차감·조정·복구와 이력 기반 멱등 처리, 동시성 제어 |
| 이성호 | User / Auth | 회원가입·로그인, JWT 발급, 회원 CRUD와 권한 처리, 소속 검증 |

<br>

## 3. 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1, Spring Cloud 2025.1.3 |
| Gateway | Spring Cloud Gateway (WebFlux) |
| Service Discovery | Netflix Eureka |
| 서비스 간 통신 | Spring Cloud OpenFeign |
| 인증 | JWT (jjwt, HS512), Spring Security (user-service), BCrypt |
| Persistence | Spring Data JPA, PostgreSQL, QueryDSL 5.1 (delivery-service) |
| API 문서 | springdoc-openapi 3.1.1 (Gateway 통합 Swagger UI) |
| 모니터링 | Spring Boot Actuator, Micrometer Tracing + Zipkin |
| 알림 | Slack Incoming Webhook (주문 생성 알림, 비동기) |
| 테스트 | JUnit 5, Mockito, MockMvc, Testcontainers(PostgreSQL), H2 |
| Build | Gradle (모듈별 독립 빌드) |
| 실행 환경 | Docker, Docker Compose |

<br>

## 4. 아키텍처

```mermaid
flowchart LR
    Client([Client]) -->|JWT| GW[API Gateway :8080]
    GW -. 서비스 조회 .-> EU[(Eureka Server :8761)]

    GW --> US[User :8081]
    GW --> CS[Company :8082]
    GW --> HS[Hub :8083]
    GW --> OS[Order :8084]
    GW --> DS[Delivery :8085]

    US -- Feign --> CS
    US -- Feign --> HS
    CS -- Feign --> HS
    CS -- Feign --> US
    HS -- Feign --> CS
    OS -- Feign --> CS
    OS -- Feign --> HS
    OS -- Feign --> DS
    OS -- Feign --> US
    DS -- Feign --> HS
    DS -- Feign --> US

    OS -. 주문 알림 .-> SL[[Slack Webhook]]

    US --- DBU[(connect_user)]
    CS --- DBC[(connect_company)]
    HS --- DBH[(connect_hub)]
    OS --- DBO[(connect_order)]
    DS --- DBD[(connect_delivery)]
```

- **Database per Service**: 서비스마다 별도의 PostgreSQL DB를 사용하고, 다른 서비스의 데이터는 Feign(HTTP)으로만 조회합니다.
- **서비스 간 통신**: Eureka로 서비스 위치를 찾고, OpenFeign으로 HTTP 호출합니다.
- **내부 API**: `/internal/**` 경로는 Gateway 라우트에 등록하지 않고 서비스 간 통신에만 사용합니다. 서비스 포트에 직접 접근할 수 있는 환경에서는 별도의 네트워크 접근 제한이 필요합니다.

<br>

## 5. ERD

https://dbdiagram.io/d/6acc859c27cfb7f1bcdd5c07

```mermaid
erDiagram
    %% ===== User DB =====
    users {
        uuid id PK
        varchar username UK "NN"
        varchar password "NN, BCrypt"
        varchar email UK "NN"
        role_type role "NN, MASTER or USER"
        affiliation_type affiliation_type "NN, COMPANY or HUB"
        uuid affiliation_id "NN, 논리 참조 (업체 또는 허브)"
        varchar slack_id UK "NN"
        timestamp created_at
        uuid created_by
        timestamp updated_at
        uuid updated_by
        timestamp deleted_at
        uuid deleted_by
    }

    %% ===== Hub DB =====
    p_hubs {
        uuid id PK
        varchar name "NN, 100"
        varchar address "NN, 200"
        timestamp created_at
        uuid created_by
        timestamp updated_at
        uuid updated_by
        timestamp deleted_at
        uuid deleted_by
    }

    %% ===== Company DB =====
    p_companies {
        uuid id PK
        uuid user_id "논리 참조, 업체 관리자 (NULL 허용)"
        uuid hub_id "NN, 논리 참조, 생성 후 변경 불가"
        varchar name "NN, 100"
        varchar address "NN, 200"
        timestamp created_at
        uuid created_by
        timestamp updated_at
        uuid updated_by
        timestamp deleted_at
        uuid deleted_by
    }

    p_company_products {
        uuid id PK
        uuid company_id FK "NN"
        varchar name "NN, 100"
        varchar description "200"
        decimal price "NN, 10,2"
        timestamp created_at
        uuid created_by
        timestamp updated_at
        uuid updated_by
        timestamp deleted_at
        uuid deleted_by
    }

    %% ===== Delivery DB =====
    p_deliveries {
        uuid id PK
        uuid order_id UK "NN, 논리 참조"
        uuid orderer_id "NN, 논리 참조"
        uuid delivery_user_id "논리 참조, 미배정이면 NULL"
        uuid departure_hub_id "NN, 논리 참조"
        uuid arrival_hub_id "NN, 논리 참조"
        delivery_status delivery_status "NN"
        varchar delivery_address "NN, 200"
        varchar receiver "NN, 50"
        timestamp created_at "NN"
        uuid created_by "NN"
        timestamp updated_at
        uuid updated_by
        timestamp deleted_at
        uuid deleted_by
    }

    p_delivery_routes {
        uuid id PK
        uuid delivery_id "NN, 같은 DB지만 FK 없음"
        uuid delivery_user_id "논리 참조"
        uuid departure_hub_id "NN, 논리 참조"
        uuid arrival_hub_id "NN, 논리 참조"
        delivery_route_status delivery_route_status "NN"
        decimal estimated_distance "NN, 10,2"
        int estimated_duration "NN"
        decimal actual_distance "10,2"
        int actual_duration
        timestamp departed_at
        timestamp created_at "NN"
        uuid created_by "NN"
        timestamp updated_at
        uuid updated_by
        timestamp deleted_at
        uuid deleted_by
    }

    p_hub_routes {
        uuid id PK
        uuid departure_hub_id FK "NN, UK(departure_hub_id, arrival_hub_id)"
        uuid arrival_hub_id FK "NN"
        decimal distance "NN, 10,2"
        int duration "NN"
        timestamp created_at
        uuid created_by
        timestamp updated_at
        uuid updated_by
        timestamp deleted_at
        uuid deleted_by
    }

    %% ===== Hub DB (재고) =====
    p_stocks {
        uuid id PK
        uuid hub_id FK "NN, UK(hub_id, product_id)"
        uuid product_id "NN, 논리 참조"
        int quantity "NN, 0 이상"
        timestamp created_at
        uuid created_by
        timestamp updated_at
        uuid updated_by
        timestamp deleted_at
        uuid deleted_by
    }

    %% ===== Order DB =====
    p_order {
        uuid id PK
        uuid requester_company_id "NN, 논리 참조 (업체 또는 허브)"
        uuid supplier_company_id "NN, 논리 참조"
        uuid product_id "NN, 논리 참조"
        uuid delivery_id "논리 참조, 배송 생성 후 저장"
        int quantity "NN"
        varchar request_message "200"
        varchar receiver "NN, 50"
        order_status status "NN"
        int stock_adjust_seq "NN, 기본 0"
        timestamp created_at
        uuid created_by
        timestamp updated_at
        uuid updated_by
        timestamp deleted_at
        uuid deleted_by
    }

    p_stock_inbounds {
        uuid id PK
        uuid inbound_id UK "NN, 입고 멱등 키"
        uuid stock_id FK "NN"
        int quantity "NN, 0 초과"
        uuid inbound_by "NN, 입고 요청자"
        timestamp created_at "NN, 입고 일시"
    }

    p_stock_histories {
        uuid id PK
        uuid order_id "NN, 논리 참조"
        uuid stock_id FK "NN"
        stock_history_type type "NN, DECREASE or ADJUST or RESTORE"
        uuid idempotency_key "NN, UK(type, idempotency_key)"
        int quantity
        int quantity_delta
        timestamp created_at "NN"
    }

    %% ----- 물리적 FK (같은 서비스 DB, 실선) -----
    p_companies ||--o{ p_company_products : "company_id FK"
    p_hubs ||--o{ p_hub_routes : "departure_hub_id FK"
    p_hubs ||--o{ p_hub_routes : "arrival_hub_id FK"
    p_hubs ||--o{ p_stocks : "hub_id FK"
    p_stocks ||--o{ p_stock_inbounds : "stock_id FK"
    p_stocks ||--o{ p_stock_histories : "stock_id FK"

    %% ----- 논리 참조 (UUID만 저장, DB FK 없음, 점선) -----
    p_hubs ||..o{ p_companies : "hub_id 논리 참조"
    users |o..o{ p_companies : "user_id 논리 참조"
    p_companies ||..o{ users : "affiliation_id 논리 참조 (COMPANY)"
    p_hubs ||..o{ users : "affiliation_id 논리 참조 (HUB)"
    p_company_products ||..o{ p_stocks : "product_id 논리 참조"
    p_companies ||..o{ p_order : "requester_company_id 논리 참조"
    p_companies ||..o{ p_order : "supplier_company_id 논리 참조"
    p_company_products ||..o{ p_order : "product_id 논리 참조"
    p_order ||..o| p_deliveries : "order_id, delivery_id 논리 참조"
    p_order ||..o{ p_stock_histories : "order_id 논리 참조"
    p_deliveries ||..o| p_delivery_routes : "delivery_id 논리 참조"
    p_hubs ||..o{ p_deliveries : "departure_hub_id 논리 참조"
    p_hubs ||..o{ p_deliveries : "arrival_hub_id 논리 참조"
    users ||..o{ p_deliveries : "orderer_id 논리 참조"
    users |o..o{ p_deliveries : "delivery_user_id 논리 참조"
    p_hubs ||..o{ p_delivery_routes : "departure_hub_id 논리 참조"
    p_hubs ||..o{ p_delivery_routes : "arrival_hub_id 논리 참조"
    users |o..o{ p_delivery_routes : "delivery_user_id 논리 참조"
    users ||..o{ p_stock_inbounds : "inbound_by 논리 참조"
```

| 서비스 DB | 테이블 |
|---|---|
| User (`connect_user`) | `users` |
| Company (`connect_company`) | `p_companies`, `p_company_products` |
| Hub (`connect_hub`) | `p_hubs`, `p_hub_routes`, `p_stocks`, `p_stock_inbounds`, `p_stock_histories` |
| Order (`connect_order`) | `p_order` |
| Delivery (`connect_delivery`) | `p_deliveries`, `p_delivery_routes` |

- **실선**은 같은 서비스 DB 안의 물리적 FK, **점선**은 UUID만 저장하고 DB FK는 없는 **논리 참조**입니다. 서비스별로 DB가 분리되어 있어 다른 서비스 데이터는 API로 존재 여부를 확인합니다.
- 표기: `NN` = NOT NULL, `UK` = UNIQUE, `UK(a, b)` = 복합 UNIQUE, 숫자 = 문자열 길이 또는 `decimal` 정밀도. 공통 컬럼 `deleted_at` / `deleted_by`로 Soft Delete를 기록합니다.

## 6. 서비스 구성

| 모듈 | 서비스명 | 포트 | DB | 역할 |
|---|---|---|---|---|
| `connect.eureka-server` | eureka-server | 8761 | - | 서비스 등록·조회 |
| `connect.eureka-gateway` | eureka-gateway | 8080 | - | 단일 진입점, JWT 검증, 라우팅, 통합 Swagger |
| `connect.user-service` | user-service | 8081 | connect_user | 회원가입·로그인(JWT 발급), 회원 관리 |
| `connect.company-service` | company-service | 8082 | connect_company | 업체·상품 관리 |
| `connect.hub-service` | hub-service | 8083 | connect_hub | 허브·허브 간 경로·재고 관리 |
| `connect.order-service` | order-service | 8084 | connect_order | 주문 생성·수정·취소, Slack 알림 |
| `connect.delivery-service` | delivery-service | 8085 | connect_delivery | 배송·배송 경로·상태 관리 |

### 서비스별 핵심 기능

<details>
<summary><b>User</b></summary>

- 회원가입: 항상 `USER` 권한으로 생성, BCrypt 암호화, 소속 업체·허브 존재 여부를 Company·Hub Service에 조회해 확인
- 로그인 시 JWT 발급
- `MASTER`는 소속과 관계없이 전체 회원 조회·수정·삭제, `USER`는 본인 정보만 조회
- 시스템의 **마지막 MASTER**는 삭제·강등 불가, 회원 삭제는 Soft Delete
- 애플리케이션 시작 시 환경변수로 설정한 초기 MASTER 계정 생성 (`MasterAccountInitializer`)

</details>

<details>
<summary><b>Company · Product</b></summary>

- 업체 생성: MASTER만 가능, Hub Service에 조회해 소속 허브 존재 여부 검증
- 업체 수정: MASTER 또는 해당 업체 관리자만 가능. 업체명·주소는 수정할 수 있고, 생성 이후 소속 허브(`hubId`)는 변경할 수 없음 (다른 허브로 변경 요청 시 `409 COMPANY_HUB_CHANGE_NOT_ALLOWED`)
- 업체 삭제: MASTER 또는 해당 업체 관리자
- 업체 관리자 지정: MASTER만 가능, 대상이 그 업체 소속 사용자인지 User에 조회해 확인
- 상품 CRUD: 등록·수정·삭제는 업체 관리자 또는 MASTER
- 업체·상품 삭제 시 Hub의 관련 재고 삭제 요청

</details>

<details>
<summary><b>Hub · Stock</b></summary>

- 허브·허브 간 경로(거리, 소요 시간) 조회, 기동 시 초기 데이터 적재(`HUB_SEED_ENABLED`)
- 재고 등록·입고: MASTER 또는 해당 업체 관리자
- 주문용 재고 차감·복원·조정: orderId / adjustmentId 기준 **멱등 처리**, 재고 변경 이력(StockHistory) 기록

</details>

<details>
<summary><b>Order</b></summary>

- 주문 생성: 상품 검증 → 재고 차감 → 배송 생성. Hub가 재고를 거절하면(재고 부족·판매 중단·판매 준비 중) 주문을 `FAILED`로 기록하고 409 반환
- 실패 보상: 배송 생성 실패 시 배송 취소 요청 → 재고 복원 요청 → 주문 `FAILED`. 재고 차감 호출 오류 시 재고 복원 요청, 주문 저장 실패 시 배송 취소·재고 복원 요청 (보상 요청이 실패하면 로그만 남김)
- 주문 수정: 수량 차이만큼 재고 조정 후 수령인 변경, 수령인 변경이 실패하면 재고 조정을 되돌림 (되돌리기 실패 시 로그만 남김)
- 주문 취소: 배송 취소 → 재고 복원 요청 → Soft Delete (재고 복원이 실패해도 주문은 취소되고 로그만 남김)
- 내가 한 주문 / 내 업체가 받은 주문 조회, 주문 생성 후 Slack 알림(비동기, 알림 실패는 주문 결과에 영향 없음)

</details>

<details>
<summary><b>Delivery</b></summary>

- 배송·배송 경로 생성: Hub 경로 조회로 예상 거리·시간 저장, 같은 주문은 한 번만 생성
- 담당자 자동 배정: 출발 허브 소속 사용자 중 진행 중인 배송이 가장 적은 사람 (User 조회 실패·대상 없음이면 미배정)
- 상태 전이: `HUB_WAITING → HUB_MOVING → DEST_HUB_ARRIVED → OUT_FOR_DELIVERY → DELIVERED` (대기 중에만 `CANCELED`), 배송 담당자 또는 MASTER가 처리
- 허브 도착 시 실제 이동거리를 입력받고, 실제 소요시간은 허브 출발 ~ 도착 시각으로 자동 계산
- 미배정 배송 목록 조회 후 출발 허브 소속 사용자가 본인을 담당자로 배정, MASTER의 담당자 변경
- 수령인 변경, 조건 검색(QueryDSL), Soft Delete

</details>

<br>

## 7. 인증과 인가

### 사용자 역할

| 역할 | 설명 |
| --- | --- |
| MASTER | 전체 시스템 관리자. 업체 등록, 업체 관리자 지정, 회원·배송 담당자 관리 |
| 업체 관리자 | 업체(`Company.userId`)에 지정된 업체 소속 사용자. 업체·상품·재고 관리, 받은 주문 조회 |
| 업체 직원 | 업체 소속 사용자. 주문, 본인 주문·배송 조회 |
| 허브 직원(배송기사) | 허브 소속 사용자. 배송 담당, 배송 상태 변경 |

### 인증 흐름

```mermaid
sequenceDiagram
    participant C as Client
    participant G as Gateway
    participant U as User Service
    participant S as 각 서비스

    C->>G: POST /auth/login
    G->>U: (토큰 검사 없이 전달)
    U-->>C: data.access_token (JWT)

    C->>G: Authorization: Bearer {JWT}
    G->>G: 서명 · 만료 · 필수 claim 검증<br/>클라이언트가 보낸 X-User-* 헤더 제거
    G->>S: X-User-Id / X-User-Role /<br/>X-User-Affiliation-Type / X-User-Affiliation-Id
    S-->>C: 응답
```

| 항목 | 내용 |
| --- | --- |
| JWT claim | `username`, `userId`, `role`(`MASTER`/`USER`), `affiliationType`(`COMPANY`/`HUB`), `affiliationId` |
| 토큰 없이 통과 | `/auth/login`, `/auth/signup`, 통합 Swagger 문서 경로(`/docs/{서비스}/v3/api-docs`) |
| 인증 실패 응답 | `401 {"code":"UNAUTHORIZED" \| "INVALID_TOKEN", "message": "..."}` |

- Company·Hub·Order·Delivery는 JWT를 다시 파싱하지 않고 Gateway가 넣어 준 `X-User-*` 헤더로 권한을 판단합니다. Gateway를 거치는 요청에서는 클라이언트가 보낸 `X-User-*` 헤더를 Gateway가 제거하므로 위조할 수 없습니다.
- User Service는 Gateway가 전달한 `Authorization` 헤더를 자체 Spring Security 필터(`JwtAuthorizationFilter`)로 다시 검증하고, DB에서 회원을 조회해 권한을 판단합니다. (`/auth/login`, `/auth/signup`, `/internal/**`는 인증 없이 허용)
- **Gateway와 user-service는 같은 `JWT_SECRET_KEY`를 사용해야 합니다.**

<br>

## 8. 주요 비즈니스 흐름

### 주문 생성 (보상 처리 포함)

```mermaid
sequenceDiagram
    participant O as Order
    participant C as Company
    participant H as Hub
    participant D as Delivery

    O->>C: 주문자 소속 업체 / 상품 / 공급 업체 조회<br/>(허브 소속 주문자는 Hub 에서 허브 조회)
    O->>O: 주문 저장 (PENDING)
    O->>H: 재고 차감 (orderId 기준 멱등)
    alt 재고 거절 (재고 부족·판매 중단·판매 준비 중)
        O->>O: 주문 FAILED → 409
    else 차감 성공
        O->>D: 배송 생성 (/internal/deliveries)
        alt 배송 생성 실패
            O->>D: 배송 취소 요청 (보상)
            O->>H: 재고 복원 요청 (보상)
            O->>O: 주문 FAILED → 500 DELIVERY_CREATE_FAILED
        else 성공
            O->>O: 주문 CREATED + deliveryId 저장
            O-->>O: Slack 알림 (비동기)
        end
    end
```

- 분산 트랜잭션(Saga 프레임워크, 메시지 브로커)은 사용하지 않고 **동기 호출 + 실패 시 보상 호출** 방식입니다.
- 재고 거절 시 응답 코드는 사유에 따라 `OUT_OF_STOCK`, `PRODUCT_DISCONTINUED`, `PRODUCT_NOT_READY`이며 모두 409입니다.
- 재고 차감 호출 자체가 실패하면(결과를 알 수 없음) 재고 복원을 요청한 뒤 `500 STOCK_DECREASE_FAILED`, 주문 저장이 실패하면 배송 취소·재고 복원을 요청한 뒤 `500 ORDER_SAVE_FAILED`로 응답합니다.
- 보상 호출(배송 취소·재고 복원·재조정)이 실패해도 재시도하지 않고 로그만 남기므로, 이 경우 배송과 재고는 수동으로 확인해야 합니다.
- 주문 수정·취소는 배송이 `HUB_WAITING` 상태일 때만 가능합니다.

### 배송 상태 전이

```mermaid
stateDiagram-v2
    [*] --> HUB_WAITING: 배송 생성 (자동 배정)
    HUB_WAITING --> HUB_MOVING: 허브 출발
    HUB_MOVING --> DEST_HUB_ARRIVED: 목적지 허브 도착 (실제 이동거리 입력)
    DEST_HUB_ARRIVED --> OUT_FOR_DELIVERY: 업체 배송 출발
    OUT_FOR_DELIVERY --> DELIVERED: 배송 완료
    HUB_WAITING --> CANCELED: 주문 취소
```

<br>

## 9. 실행 방법

### 방법 1. Docker Compose (권장)

PostgreSQL, Zipkin, Eureka, Gateway와 5개 도메인 서비스를 컨테이너로 함께 실행합니다.

**1) 환경변수 파일 준비**

```bash
cp .env.example .env
```

| 변수 | 설명 |
| --- | --- |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | PostgreSQL 계정 |
| `JWT_SECRET_KEY` | JWT 서명 키 (Gateway·user-service 공용, Base64URL 64바이트 이상) |
| `MASTER_USERNAME`, `MASTER_PASSWORD`, `MASTER_EMAIL`, `MASTER_SLACK_ID` | 초기 MASTER 계정 |
| `MASTER_AFFILIATION_TYPE`, `MASTER_AFFILIATION_ID` | 초기 MASTER의 소속 (기본 `HUB`) |
| `SLACK_WEBHOOK_URL` | 주문 알림 Slack Webhook |

**2) 실행**

```bash
docker compose up -d --build
```

| 항목 | 주소 |
| --- | --- |
| API Gateway | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| Eureka | http://localhost:8761 |
| Zipkin | http://localhost:9411 |

- 서비스별 DB는 Postgres 컨테이너를 처음 만들 때 `docker/postgres/init.sql`이 생성합니다.
- 이미지 빌드 시 테스트는 실행하지 않습니다 (`docker/Dockerfile`, `bootJar -x test`).

**3) 동작 확인**

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"{아이디}","password":"{비밀번호}"}'
```

응답의 `data.access_token`을 이후 요청의 `Authorization: Bearer {토큰}` 헤더로 사용합니다.

### 방법 2. 모듈별 직접 실행

<details>
<summary>펼쳐 보기</summary>

**1) 사전 준비**: JDK 21, PostgreSQL(Docker 권장), (선택) Zipkin `localhost:9411`, Slack Webhook URL

**2) DB 생성** (테이블은 `ddl-auto: update`로 자동 생성)

```sql
CREATE DATABASE connect_user;
CREATE DATABASE connect_company;
CREATE DATABASE connect_hub;
CREATE DATABASE connect_order;
CREATE DATABASE connect_delivery;  -- 기본 설정은 localhost:5433
```

**3) 환경변수**

민감한 값은 환경변수 또는 모듈 폴더의 `.env`로 주입합니다. `.env`를 자동으로 읽는 모듈은 user-service, eureka-gateway이며, **실행 위치(working directory)가 해당 모듈 폴더**여야 합니다.

| 서비스 | 환경변수 | 설명 |
|---|---|---|
| eureka-gateway | `JWT_SECRET_KEY` | user-service와 동일한 키 |
| user-service | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | 기본 `localhost:5432/connect_user` |
| | `JWT_SECRET_KEY` | Gateway와 동일한 키 |
| | `admin.masters[n].*` | (선택) 초기 MASTER 계정, `user-service/.env.example` 참고 |
| company-service | `COMPANY_DB_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | 기본 `localhost:5432/connect_company` |
| hub-service | `HUB_DB_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | 기본 `localhost:5432/connect_hub` |
| | `HUB_SEED_ENABLED` | 허브·경로 초기 데이터 적재 여부 (기본 `true`) |
| order-service | `ORDER_DB_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | 기본 `localhost:5432/connect_order` |
| | `EUREKA_URL` | **필수** 예: `http://localhost:8761/eureka/` |
| | `SLACK_WEBHOOK_URL` | (선택) 비우면 알림을 보내지 않음 |
| delivery-service | `DELIVERY_DB_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | 기본 `localhost:5433/connect_delivery` |
| | `JWT_SECRET` | 설정 값 바인딩용 |

**4) 실행 순서**: Eureka Server → 각 도메인 서비스 → Gateway

```bash
cd connect.eureka-server   && ./gradlew bootRun
cd connect.user-service    && ./gradlew bootRun
cd connect.company-service && ./gradlew bootRun
cd connect.hub-service     && ./gradlew bootRun
cd connect.order-service   && ./gradlew bootRun
cd connect.delivery-service && ./gradlew bootRun
cd connect.eureka-gateway  && ./gradlew bootRun
```

각 명령은 별도 터미널에서 실행하고, Eureka 대시보드(`http://localhost:8761`)에서 모든 서비스가 `UP`인지 확인합니다.

</details>

<br>

## 10. API

### Swagger

Gateway에서 서비스별 OpenAPI 문서를 모아 하나의 Swagger UI로 제공합니다.

- 통합 Swagger UI: `http://localhost:8080/swagger-ui/index.html` (상단 드롭다운에서 서비스 선택)
- 현재 제공: **user-service**, **company-service**, **hub-service**, **order-service**, **delivery-service** (전체)
- 서비스별 Swagger UI 직접 접근(로컬 개발용): `http://localhost:{서비스 포트}/swagger-ui/index.html`
  - Gateway 를 거치지 않으므로 `X-User-*` 헤더를 직접 입력합니다. (user-service 는 JWT 를 직접 검증하므로 Bearer 토큰 필요)
- 문서 작성 방식: 컨트롤러별 `controller/docs/*ControllerDocs` 인터페이스에 설명을 두고 컨트롤러가 `implements` 합니다.
  - `@Min`/`@Max`, `@Valid` 같은 파라미터 검증 어노테이션은 **docs 인터페이스에만** 선언합니다. 컨트롤러에 다시 선언하면 Hibernate Validator HV000151 오류로 요청이 500 이 됩니다.
- 새 서비스 추가 방법
    1. 해당 서비스에 `springdoc-openapi-starter-webmvc-ui` 의존성 추가
    2. Gateway `application.yml`에 `/docs/{서비스}/v3/api-docs` 라우트(`StripPrefix=2`)와 `springdoc.swagger-ui.urls` 항목 추가

> 개발 환경용입니다. 운영에서는 `springdoc.swagger-ui.enabled=false` 등으로 문서를 비공개로 두는 것을 권장합니다.

### API 목록

외부 호출은 모두 Gateway(`http://localhost:8080`)를 통해야 하며, 로그인·회원가입 외에는 `Authorization: Bearer {JWT}`가 필요합니다.

<details>
<summary><b>User</b></summary>

| Method | Path | 설명 |
|---|---|---|
| POST | `/auth/signup` | 회원가입 (USER 권한) |
| POST | `/auth/login` | 로그인, JWT 발급 |
| GET | `/users/{userId}` | 회원 단건 조회 (USER는 본인, MASTER는 전체) |
| GET | `/users` | 회원 목록 조회 (MASTER) |
| PUT | `/users/{userId}` | 회원 수정 (MASTER) |
| DELETE | `/users/{userId}` | 회원 삭제 (MASTER, Soft Delete) |
| GET | `/users/user-info` | 로그인한 회원 정보 |

</details>

<details>
<summary><b>Company · Product</b></summary>

| Method | Path | 설명 |
|---|---|---|
| POST | `/companies` | 업체 생성 (MASTER) |
| GET | `/companies/{companyId}` | 업체 단건 조회 |
| GET | `/companies` | 업체 목록 조회 |
| PUT | `/companies/{companyId}` | 업체 수정 |
| DELETE | `/companies/{companyId}` | 업체 삭제 (상품·재고 함께 정리) |
| PATCH | `/companies/{companyId}/manager` | 업체 관리자 지정·변경 (MASTER) |
| POST | `/companies/{companyId}/products` | 상품 등록 |
| GET | `/companies/{companyId}/products/{productId}` | 상품 단건 조회 |
| GET | `/companies/{companyId}/products` | 상품 목록 조회 |
| PUT | `/companies/{companyId}/products/{productId}` | 상품 수정 |
| DELETE | `/companies/{companyId}/products/{productId}` | 상품 삭제 |

</details>

<details>
<summary><b>Hub · Stock</b></summary>

| Method | Path | 설명 |
|---|---|---|
| GET | `/hubs` | 허브 목록 |
| GET | `/hubs/{hubId}` | 허브 단건 조회 |
| GET | `/hubs/routes` | 허브 간 경로 목록 |
| GET | `/hubs/routes/{departureHubId}/{arrivalHubId}` | 두 허브 간 경로 조회 |
| POST | `/hubs/stocks` | 재고 등록 |
| POST | `/hubs/stocks/inbound` | 재고 입고 (수량 추가) |
| GET | `/hubs/stocks` | 재고 목록 |
| GET | `/hubs/stocks/{stockId}` | 재고 단건 조회 |

</details>

<details>
<summary><b>Order</b></summary>

| Method | Path | 설명 |
|---|---|---|
| POST | `/orders` | 주문 생성 |
| GET | `/orders/{orderId}` | 내 주문 단건 조회 (MASTER는 전체) |
| GET | `/orders` | 내 주문 목록 (`page`, `size` ≤ 100) |
| GET | `/orders/received/{orderId}` | 받은 주문 단건 조회 (공급 업체 담당자) |
| GET | `/orders/received` | 받은 주문 목록 (MASTER는 전체, 업체 관리자는 실패 주문 제외) |
| PUT | `/orders/{orderId}` | 주문 수정 (수량·요청사항·수령인) |
| DELETE | `/orders/{orderId}` | 주문 취소 |

</details>

<details>
<summary><b>Delivery</b></summary>

| Method | Path | 설명 |
|---|---|---|
| GET | `/deliveries/{deliveryId}` | 배송 단건 조회 |
| GET | `/deliveries` | 배송 검색 (상태·담당자·허브·수령인, `size` 10/30/50) |
| GET | `/deliveries/unassigned` | 내 소속 허브 출발 미배정 배송 목록 (HUB 소속) |
| POST | `/deliveries/{deliveryId}/hub-departure` | 허브 출발 |
| POST | `/deliveries/{deliveryId}/hub-arrival` | 목적지 허브 도착 (본문 `actualDistance` 필수) |
| POST | `/deliveries/{deliveryId}/delivery-start` | 업체 배송 출발 |
| POST | `/deliveries/{deliveryId}/complete` | 배송 완료 |
| POST | `/deliveries/{deliveryId}/delivery-user` | 배송 담당자 배정 (출발 허브 소속 본인) |
| PATCH | `/deliveries/{deliveryId}/delivery-user` | 배송 담당자 변경 (MASTER) |
| PATCH | `/deliveries/{deliveryId}/receiver` | 수령인 변경 |
| DELETE | `/deliveries/{deliveryId}` | 배송 삭제 (완료·취소된 배송만) |
| GET | `/deliveries/routes/{deliveryId}` | 배송 경로 기록 조회 |

</details>

<details>
<summary><b>내부 API</b> (<code>/internal/**</code>, Gateway 미노출)</summary>

| 제공 서비스 | Method | Path | 호출하는 서비스 |
|---|---|---|---|
| User | GET | `/internal/users/{userId}` | Order (Slack 알림), Company (업체 관리자 지정 시 소속 확인) |
| User | GET | `/internal/users/hub/{departureHubId}/delivery-users` | Delivery (담당자 자동 배정) |
| Company | POST | `/internal/companies` | 없음 (가입 시 업체 생성용, 현재 미사용) |
| Hub | POST | `/internal/hubs/stocks/decrease` · `/restore` · `/adjust` | Order |
| Hub | POST | `/internal/hubs/stocks/delete` | Company (업체·상품 삭제) |
| Hub | GET | `/internal/hubs/stocks/histories` | 없음 (재고 이력 조회용, 현재 미사용) |
| Delivery | POST | `/internal/deliveries` · `/internal/deliveries/cancel` | Order |

</details>

### 공통 응답 형식

<details>
<summary>성공 예시 (<code>POST /orders</code>)</summary>

```json
{
  "success": true,
  "message": "주문이 생성되었습니다.",
  "data": {
    "id": "6f1c2a3b-1d2e-4f50-8a9b-0c1d2e3f4a5b",
    "requesterCompanyId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
    "supplierCompanyId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
    "productId": "3c4d5e6f-7a8b-4c9d-0e1f-2a3b4c5d6e7f",
    "deliveryId": "4d5e6f7a-8b9c-4d0e-1f2a-3b4c5d6e7f8a",
    "quantity": 2,
    "requestMessage": "문 앞에 놓아주세요",
    "receiver": "홍길동",
    "orderStatus": "CREATED",
    "createdAt": "2026-10-20T12:00:00",
    "updatedAt": "2026-10-20T12:00:00"
  },
  "timestamp": "2026-10-20T12:00:00"
}
```

</details>

<details>
<summary>실패 예시 (<code>DELIVERY_NOT_FOUND</code>)</summary>

```json
{
  "code": "DELIVERY_NOT_FOUND",
  "message": "배송을 찾을 수 없습니다."
}
```

</details>

<br>

## 11. 테스트

모듈별로 실행합니다.

```bash
cd connect.order-service && ./gradlew test
```

| 서비스 | 테스트 DB | 비고 |
|---|---|---|
| user-service | H2 (`application-test.yml`) | 로컬 DB 불필요 |
| company-service | Testcontainers PostgreSQL | Docker 필요 |
| hub-service | `application.yml`의 PostgreSQL | 테스트 전용 DB 지정 권장 |
| order-service, delivery-service | Mockito 단위 테스트 + 컨텍스트 로딩 테스트 | 컨텍스트 로딩에 DB 필요 |
| eureka-gateway | JWT 필터 단위 테스트, Swagger 라우트 통합 테스트 | - |

- 다른 서비스 호출(Feign)은 테스트에서 Mock으로 대체하므로, 서비스 내부 동작만 검증합니다. **여러 서비스를 실제로 연결한 E2E 검증은 별도로(Postman 등) 수행**해야 합니다.
- 테스트는 반드시 개발·운영 DB와 분리된 DB에서 실행하세요.

<br>

## 12. 프로젝트 구조

```
connect
├── .github/ISSUE_TEMPLATE
├── docker-compose.yml         # 전체 서비스 일괄 실행
├── docker                     # 공통 Dockerfile, Postgres 초기화 SQL(init.sql)
├── connect.eureka-server      # Service Discovery
├── connect.eureka-gateway     # API Gateway + JWT 필터 + 통합 Swagger
├── connect.user-service       # 회원·인증
├── connect.company-service    # 업체·상품
├── connect.hub-service        # 허브·경로·재고
├── connect.order-service      # 주문
└── connect.delivery-service   # 배송
```

각 도메인 서비스는 대체로 다음 구조를 따릅니다.

```
src/main/java/connect/{service}
├── domain (또는 기능별 패키지)
│   ├── controller   # 외부 API, Internal*Controller(내부 API)
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
├── client           # 다른 서비스 호출용 Feign Client
└── global           # 공통 응답(ApiResponse), 예외(ErrorCode, GlobalExceptionHandler), 설정
```

<br>

## 13. 협업 규칙

### 브랜치 전략

| 브랜치 | 용도 |
|---|---|
| `master` | 배포 기준 브랜치 |
| `develop` | 통합 개발 브랜치 (기능 브랜치의 PR 대상) |
| `feature/{도메인 또는 기능}` | 기능 개발 (예: `feature/order`, `feature/hub`) |
| `fix/{내용}` | 버그·설정 수정 |

### 커밋 메시지

`type: 내용` 형식을 사용합니다.

| type | 의미 |
|---|---|
| `feat` | 기능 추가 |
| `fix` | 버그·설정 수정 |
| `docs` | 문서 |
| `refactor` | 리팩터링 |
| `test` | 테스트 |

### 공통 규칙

- `.env` 등 민감한 값은 커밋하지 않고 `.env.example`만 관리합니다.
- 삭제는 `deletedAt` / `deletedBy`를 기록하는 **Soft Delete**를 사용합니다.
- 서비스끼리만 쓰는 API는 `/internal/**` 경로에 두고 Gateway에 라우트를 추가하지 않습니다.