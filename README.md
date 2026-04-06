# O:GU (오구, 오늘의 구절)

> **문구로 책을 발견하는 서비스**
>
> 책 제목과 저자 없이, 감성 문구만 보고 책을 발견합니다.  
> 카드를 탭하면 책이 공개되는 Reveal 구조로 서비스의 본질을 기술적으로 보장합니다.

🌐 **서비스 주소**: [www.todayogu.com](https://www.todayogu.com)  
🔗 **프론트엔드 레포**: [zeromok/book-phrase-frontend](https://github.com/zeromok/book-phrase-frontend)

---

## 서비스 흐름

```
기분 태그 선택 → 문구 카드 탐색 → 카드 탭 → 책 Reveal
```

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 4.0.5 |
| ORM | Spring Data JPA + Hibernate |
| DB | MySQL 8.0 |
| Security | Spring Security (HTTP Basic Auth, Admin 전용) |
| Build | Gradle |
| API 문서 | Swagger (springdoc-openapi 2.8.3) |
| AI | Anthropic Claude API (콘텐츠 파이프라인) |
| 도서 데이터 | 알라딘 TTB API |
| 배포 | Railway |

---

## 주요 설계 결정

### 1. 퍼블릭 서비스 구조 (인증 없음)
회원가입/로그인 없이 누구나 바로 사용 가능한 구조입니다. 어드민 API만 HTTP Basic Auth로 보호합니다.

- **Why**: 온보딩 마찰을 없애 첫 사용자 경험에 집중. MVP 단계에서 사용자 데이터보다 서비스 가치 검증이 우선.
- **Trade-off**: 개인화 추천 불가. 향후 소셜 로그인 등 가벼운 인증으로 확장 가능.

### 2. Reveal API 분리
피드 API(`/phrases/feed`)는 문구 텍스트만 반환하고, 책 정보는 카드 탭 시 별도 API(`/phrases/{id}/reveal`)를 호출해야 응답합니다.

- **Why**: 프론트에서 숨기는 방식은 개발자 도구로 노출 가능. 서비스 본질인 "모르고 선택한다"를 기술적으로 보장.
- **Trade-off**: API 호출 1회 추가. UX와 기술 구조가 일치하는 설계.

### 3. 태그 4종 고정
태그는 `위로받고싶다`, `자극받고싶다`, `쉬고싶다`, `성장하고싶다` 4개로 고정됩니다. AI가 콘텐츠 수집 시 기존 태그에서 선택하며, 새 태그를 생성하지 않습니다.

- **Why**: 태그가 늘어나면 UX 필터 복잡도가 증가. 4개의 감성 축이 대부분의 독서 동기를 커버.
- **Trade-off**: 세분화된 필터 불가. 대신 빠른 선택과 깔끔한 UI 유지.

### 4. 도메인형 패키지 구조
`domain/`, `api/`, `global/`로 분리. 레이어형(controller/service/repository) 대신 도메인 응집도 우선.

- **Why**: 관련 코드가 한 폴더에 모여 유지보수성과 MSA 전환 용이성 확보.
- **Trade-off**: 초기 세팅 복잡도 증가.

---

## API 명세

| Method | URL | 설명 | 인증 |
|--------|-----|------|------|
| GET | `/api/v1/tags` | 태그 목록 조회 | ❌ |
| GET | `/api/v1/phrases/feed?tagId=1` | 문구 카드 피드 | ❌ |
| GET | `/api/v1/phrases/{id}/reveal` | 책 정보 공개 | ❌ |
| POST | `/api/v1/admin/pipeline/category` | AI 콘텐츠 수집 (태그별) | ✅ ADMIN |
| POST | `/api/v1/admin/tags` | 태그 등록 | ✅ ADMIN |
| POST | `/api/v1/admin/books` | 책 등록 | ✅ ADMIN |
| POST | `/api/v1/admin/phrases` | 문구 등록 | ✅ ADMIN |
| DELETE | `/api/v1/admin/phrases/{id}` | 문구 삭제 | ✅ ADMIN |

> Admin API는 HTTP Basic Auth 사용. 운영 환경에서는 반드시 강한 크리덴셜을 환경변수로 설정하세요.

---

## 도메인 모델

```
Phrase ──── Book
  └──── Tag (N:N via phrase_tags)
```

---

## 환경 변수

로컬 실행 시 아래 환경변수를 설정해야 합니다. **실제 값은 코드에 직접 작성하지 마세요.**

| 변수명 | 설명 |
|--------|------|
| `SPRING_DATASOURCE_URL` | MySQL 접속 URL |
| `SPRING_DATASOURCE_USERNAME` | DB 사용자명 |
| `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | DDL 전략 (`update` 권장, 운영에서 `none`) |
| `ADMIN_USERNAME` | 어드민 계정 (운영 환경 필수 변경) |
| `ADMIN_PASSWORD` | 어드민 비밀번호 (운영 환경 필수 변경) |
| `ANTHROPIC_API_KEY` | Claude API 키 |
| `ALADIN_TTB_KEY` | 알라딘 TTB API 키 |
| `PORT` | 서버 포트 (기본 8080) |

---

## 로컬 실행

```bash
# 1. MySQL 데이터베이스 생성
mysql -u root -e "CREATE DATABASE bookphrase CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 환경 변수 설정 (application-local.yml 또는 IDE 환경변수)
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/bookphrase
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=your_password
ANTHROPIC_API_KEY=your_key
ALADIN_TTB_KEY=your_key

# 3. 실행
./gradlew bootRun
```

> 첫 실행 시 DataInitializer가 태그 4개를 자동 삽입합니다.  
> 문구 데이터는 Admin API(`/api/v1/admin/pipeline/category`)를 통해 AI로 수집합니다.

---

## 프로젝트 구조

```
src/main/java/com/bookphrase/
├── api/
│   ├── phrase/          # PhraseController, DTO
│   └── admin/           # AdminController (어드민 전용)
├── domain/
│   ├── phrase/          # Phrase, PhraseService, PhraseRepository
│   ├── book/            # Book, BookRepository
│   └── tag/             # Tag, TagRepository
└── global/
    ├── security/        # SecurityConfig (HTTP Basic Auth)
    └── config/          # DataInitializer, CORS 설정
```
