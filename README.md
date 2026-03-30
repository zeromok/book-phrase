# 📖 BookPhrase

> **문구로 책을 발견하는 서비스**
> 
> 서점에서 패키징된 책의 앞면 문구만 보고 책을 선택하는 경험에서 착안.  
> 사용자는 책 제목/저자 없이 감성 문구만 보고 책을 발견하고, 탭하면 책이 공개됩니다.

---

## 서비스 흐름

```
오늘 기분 태그 선택 → 문구 카드 스와이프 → 카드 탭 → 책 Reveal → 구매 or 찜하기
```

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 4.0.5 |
| ORM | Spring Data JPA + Hibernate |
| DB | MySQL 8.0 |
| Security | Spring Security + JWT (jjwt 0.12.6) |
| Build | Gradle |
| API 문서 | Swagger (springdoc-openapi 2.8.3) |

---

## 주요 설계 결정

### 1. Reveal API 분리
피드 API(`/phrases/feed`)는 문구 텍스트만 반환하고, 책 정보는 카드 탭 시 별도 API(`/phrases/{id}/reveal`)를 호출해야 응답합니다.

- **Why**: 프론트에서 숨기는 방식은 개발자 도구로 노출 가능. 서비스 본질인 "모르고 선택한다"를 기술적으로 보장.
- **Trade-off**: API 호출이 1회 추가되지만, UX와 기술 구조가 일치하는 설계.

### 2. 도메인형 패키지 구조
`domain/`, `api/`, `global/`로 분리. 레이어형(controller/service/repository) 대신 도메인 응집도 우선.

- **Why**: 관련 코드가 한 폴더에 모여 유지보수성과 MSA 전환 용이성 확보.
- **Trade-off**: 초기 세팅 복잡도 증가. 도메인이 3개 이상이면 투자 대비 효과가 큼.

### 3. Tag는 Book이 아닌 Phrase에 연결
같은 책의 문구라도 감성이 다를 수 있어, 태그를 Phrase 단위로 관리.

- **Why**: Book 단위 태그는 한 책의 모든 문구가 동일한 태그를 가져 세밀한 큐레이션 불가.
- **Trade-off**: 문구 등록 시 태그 선택 작업 추가. 하지만 개인화 추천 품질 향상.

### 4. UserHistory에 단순 PK 사용
`(user_id, phrase_id)` 복합 PK 대신 auto increment id 사용.

- **Why**: 반복 열람 기록을 보존해 향후 "열람 빈도" 기반 개인화 추천 시그널로 활용.
- **Trade-off**: MVP에서는 중복 기록 발생. 2버전 알고리즘 확장 시 테이블 마이그레이션 없이 대응 가능.

---

## API 명세

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

| Method | URL | 설명 | 인증 |
|--------|-----|------|------|
| POST | `/api/v1/auth/signup` | 회원가입 | ❌ |
| POST | `/api/v1/auth/login` | 로그인 → JWT 발급 | ❌ |
| GET | `/api/v1/tags` | 태그 목록 | ❌ |
| GET | `/api/v1/phrases/feed?tagIds=1,2` | 카드 피드 10장 | ✅ |
| GET | `/api/v1/phrases/{id}/reveal` | 책 정보 공개 | ✅ |
| POST | `/api/v1/phrases/{id}/view` | 조회 기록 | ✅ |
| POST | `/api/v1/bookmarks/{phraseId}` | 찜 추가 | ✅ |
| DELETE | `/api/v1/bookmarks/{phraseId}` | 찜 해제 | ✅ |
| GET | `/api/v1/bookmarks` | 내 찜 목록 | ✅ |
| GET | `/api/v1/history` | 본 카드 목록 | ✅ |
| POST | `/api/v1/admin/tags` | 태그 등록 | ✅ ADMIN |
| POST | `/api/v1/admin/books` | 책 등록 | ✅ ADMIN |
| POST | `/api/v1/admin/phrases` | 문구 등록 | ✅ ADMIN |
| DELETE | `/api/v1/admin/phrases/{id}` | 문구 삭제 | ✅ ADMIN |

---

## 도메인 모델

```
User ──── UserBookmark ──── Phrase ──── Book
  └────── UserHistory  ────┘    └──── Tag (N:N via phrase_tags)
```

---

## 로컬 실행

```bash
# 1. MySQL 데이터베이스 생성
mysql -u root -e "CREATE DATABASE bookphrase CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. application.yml DB 설정 확인
# spring.datasource.password 본인 환경에 맞게 수정

# 3. 실행 (테스트 데이터 자동 삽입)
./gradlew bootRun
```

> 첫 실행 시 DataInitializer가 태그 4개, 책 3개, 문구 9개를 자동 삽입합니다.
