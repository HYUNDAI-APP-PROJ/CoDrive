# pleos-app

현대자동차그룹 차량용 앱 개발 공모전을 위해 개발하는 Pleos 기반 Android 앱입니다.

## 프로젝트 정보

| 항목 | 내용 |
| --- | --- |
| 조직 | HYUNDAI-APP-PROJ |
| 플랫폼 | Pleos Connect / Android |
| 개발 도구 | Android Studio |
| 현재 상태 | 저장소 기본 문서 구성 단계 |

앱 소스 코드와 빌드 설정은 추후 추가됩니다.

## 개발 시작하기

```bash
git clone https://github.com/HYUNDAI-APP-PROJ/pleos-app.git
cd pleos-app
git switch develop
```

앱 소스 코드가 추가되면 Android Studio에서 프로젝트 루트를 열고 Gradle을 동기화한 뒤, Pleos 개발 환경에서 실행합니다. 필요한 JDK, Android SDK 및 Gradle 버전은 프로젝트 설정을 기준으로 사용합니다.

## 브랜치 전략

| 브랜치 | 역할 | PR 대상 |
| --- | --- | --- |
| `main` | 배포 기준 브랜치 | — |
| `develop` | `main` 반영 전 최종 통합본 | `main` |
| `feature/*` | 일반 기능 개발 | `develop` |
| `hotfix/*` | 긴급 수정 | `main` |

일반 개발 흐름: `feature/*` → `develop` → `main`

`main`에 직접 push하지 않으며, `develop` 또는 `hotfix/*`에서 올린 PR로만 반영합니다. 현재 비공개 Free 조직 저장소에서는 이 규칙을 팀 컨벤션으로 운영합니다.

자세한 규칙은 [Convention 프로젝트](https://github.com/orgs/HYUNDAI-APP-PROJ/projects/1)의 **GitHub Convention** 항목에서 확인하세요.

## 라이선스

본 저장소에서 직접 작성한 코드와 문서에는 [MIT License](LICENSE)를 적용합니다.
Pleos SDK, 외부 라이브러리 및 제3자 자산에는 각 제공자의 라이선스와 이용약관이 적용됩니다.
