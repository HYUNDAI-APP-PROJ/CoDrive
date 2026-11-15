# HTML 디자인 앱 이식·검증 기록

검증일: 2026-11-15 (한국 시간)

## 반영 방식

`feature/recommendation`의 `com.codrive.app.MainActivity`에서 완성된 HTML을 앱 내부 WebView로 실행합니다. [기준 HTML](route-recommendation.html)과 `app/src/main/assets/route-recommendation.html`은 같은 파일입니다. 브라우저 미리보기 서버나 외부 사이트에 의존하지 않습니다.

Android의 마이크 권한과 기존 `SpeechInputController`를 `native-adapter.js`로 연결했습니다. 기존 Java View 목업 클래스는 보존했으며 새 라이브러리는 추가하지 않았습니다. 앱 내부 상태 저장소에 입력·대화·그룹·경로·타이머를 저장해 화면 재생성 후 복원합니다.

Pleos에서 WebView의 픽셀 배율과 Android 화면 밀도가 달라지는 것을 확인했습니다. 실제 WebView 영역 너비와 Android 밀도로 HTML viewport를 맞춰 전체·분할 창에서 기준 디자인과 같은 크기 체계를 사용하도록 보정했습니다.

## 검사 결과

| 검사 | 결과 |
| --- | --- |
| 디버그 앱 빌드 | 통과 |
| 기존 모델 단위 검사 | 4개 통과, 실패·오류 0개 |
| Pleos 기기 검사 | 4개 통과, 실패·오류 0개 |
| HTML 상태·타이머 검사 | 통과 |
| 앱에 포함한 HTML과 기준 파일 일치 | 통과 |
| 음성 연결 어댑터 검사 | 부분·최종 결과, 취소 후 늦은 결과 차단, 저장 상태 복원 통과 |
| Lint | 오류 0개. 이전 View 클래스의 사용하지 않는 리소스 등 경고 9개 |

기기 환경은 Pleos Connect v2.1.0 에뮬레이터, Android 14입니다. 계측 검사로 실제 WebView의 DOM 상태와 화면 범위를 확인했습니다.

## 기기에서 확인한 동작

- OFF 전송 후 고정 추천지 3곳 표시, 장소 선택, 2초 반영 로딩과 개인 경로 표시.
- 그룹의 ON 전송 후 개인 경로와 충돌하는 팝업 표시, 거절 후 기존 경로 유지.
- 10초 확인 팝업 도중 화면 재생성 후 남은 시간 복원, 시간 만료 후 2초 반영과 그룹 별칭 표시.
- 그룹 경로를 개인 경로로 바꿀 때 수락 후 새 경로 표시.
- 입력 초안·대화·모드·토글·경로 복원.
- 그룹이 없는 상태의 그룹 생성, 이름 초안 복원, 예시 카드 즉시 전송과 생성한 그룹 별칭 유지.
- 1682·829·320dp 창, 글자 배율 1.0·1.5에서 가로 넘침과 입력·전송 버튼의 영역 확인.
- 앱에 포함한 SVG 아이콘이 실제로 렌더링되는지 확인.

## 재검증

```bash
node docs/design/verify-route-recommendation.cjs
node docs/design/verify-native-adapter.cjs
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest --no-configuration-cache
```

HTML을 바꾼 뒤에는 다음 명령으로 앱 파일을 동기화합니다.

```bash
cp docs/design/route-recommendation.html app/src/main/assets/route-recommendation.html
```

## 검증 범위

추천·위치·경로는 요청한 더미 데이터입니다. 실제 내비게이션 적용, 차량 SDK와 다른 차량의 동기화는 이번 작업에 포함하지 않습니다. Android 음성 인식 서비스 연결과 이벤트 전달은 구현했지만, 사람의 실제 발성에 대한 인식 품질은 이번 자동 검사로 판정하지 않았습니다.
