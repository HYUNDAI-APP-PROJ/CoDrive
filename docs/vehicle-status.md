# 함께 주행 중 프론트

작업 브랜치: `feature/vehicle-status-ui`. 기존 `:app` 모듈, Java 17, Android View/XML 구조를 유지한다. 디자인은 작업 공간의 `Develop_docs/CoDrive_Vehicle_Status.html`, 동작은 `CoDrive_Frontend_Implementation_Spec.md`를 기준으로 한다. HTML을 WebView로 실행하지 않는다.

## 실행

Android Studio에서 이 저장소 루트를 열고 **Pleos Connect API 34**를 선택한 뒤 `app`을 실행한다. 기존 대기실이 없는 브랜치이므로 `com.codrive.app.DrivingStatusActivity`가 독립 진입점이다. 화면을 열거나 닫아도 실제 여행을 생성·종료하지 않는다. 이번 앱의 패키지는 `com.codrive`다. 기존에 설치돼 있던 별도 CoDrive는 Pleos 앱 라이브러리에서 제거했다.

이 개발 장비에 설치된 AVD는 `Pleos_Connect_API_34`이며 `android-34/pleos_car_emulator/arm64-v8a` 이미지를 사용한다. Android Studio 없이 터미널에서 실행할 수도 있다.

```sh
/Users/devcat/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_API_34 -no-snapshot-load
```

다른 터미널에서 빌드하고 설치한다. 설치 스크립트가 AVD 이름으로 Pleos를 찾으므로 실행 순서에 따라 기기 번호가 바뀌어도 같은 명령을 사용할 수 있다.

```sh
./gradlew :app:assembleDebug
bash scripts/install-pleos.sh
```

검사 명령:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-configuration-cache
./gradlew :app:connectedDebugAndroidTest --no-configuration-cache
```

두 번째 명령은 실행 중인 에뮬레이터/기기가 필요하다. 기존 설정의 Gradle 9.6.0, AGP 9.5.1, compileSdk 37, daemon JDK 25를 사용한다. 빌드 환경을 맞추기 위해 프로젝트 버전을 임의로 낮추지 않았다.

디버그 빌드에서 상단 **시연 데이터**를 누르면 시나리오 선택기가 열린다. 기본·저전력·주행가능거리·요청 실패·지연·연결 끊김·미지원/비공유·목적지 불일치·마커 밀집·도착·0거리·1/2대·정보 없음·경고·음성 실패를 선택할 수 있다. 추가로 로딩·권한 없음·조회 실패·재접속·일행 요청·PHEV·안내 종료·조작 제한을 제공한다. 정상 시나리오에서 휴식/충전 요청과 취소를 직접 누른다. 실패 시나리오는 첫 시도만 실패하고 재시도는 성공한다.

시연 시작 시 기기의 시간대를 한 번 선택하고 Mock Clock을 그 시간대의 `2026-11-14 14:30`으로 고정한다. 시간대는 세션과 함께 복원된다. 따라서 기본 ETA는 15:18 / 15:06 / 15:32이며, 벽시계가 흐른다는 이유로 첫 화면이 지연 상태가 되지 않는다. **11초 경과**는 관측값을 갱신하지 않고 시연 시계만 진행한다. **연결·정보 복구**는 새 모의 거리·시간 관측을 입력하며 활성 경고를 지우지 않는다. 개발 메뉴는 릴리스 빌드에서 비활성화되지만 모의 출처 라벨은 유지한다.

```sh
bash scripts/install-pleos.sh CLOSE_MARKERS
bash scripts/install-pleos.sh OFFLINE
bash scripts/install-pleos.sh NORMAL
```

`demo_scenario`는 디버그 빌드에서만 반영한다. `RESTRICTED`는 요청·음성·상세·개발 메뉴를 잠그므로 마지막 명령으로 정상 시연에 복귀한다. 시나리오 선택은 개발용 초기화이며 실제 여행 기능이 아니다.

## 구현 구조

| 파일/패키지 | 역할 |
| --- | --- |
| `app/DrivingStatusActivity` | 실행 진입점, 시스템 영역 처리, ViewModel 생성, 저장 상태 복원 |
| `convoy/ui/DrivingStatusScreen` | 목적지, 카드, 알림, 요청·음성 버튼 조립 |
| `convoy/ui/RemainingDistanceTrack` | 자동차·이름표와 목적지 깃발, 실제 비율 좌표 유지, 최대 3개 레인, 제외 차량 안내 |
| `convoy/ui/VehicleStatusCard` | 가로 카드/세로 목록, 품질·에너지·요청·경고 표현 |
| `convoy/ui/DrivingStatusViewModel` | 생명주기와 독립적인 여행·요청·음성 상태, LiveData |
| `convoy/ui/DrivingStatusUiState`, `DrivingText` | 문구, 알림 우선순위, 현지 시간 표시 |
| `convoy/domain/usecase/TripComparison` | 필드별 비교 참여 조건, 거리 비율, 절대 ETA 차이 |
| `convoy/data/model` | 불변 측정값/여행/차량/요청 모델; 미제공 값은 null |
| `convoy/data/repository` | 관찰·요청 계약, 모의 비동기 처리, 품질·연결 판정 |
| `convoy/data/source` | 고정 fixture, 주입 가능한 시계/스케줄러, 실데이터 연결 인터페이스 |
| `voice/data` | 마이크 접근 없는 음성 참여·실패·음소거·나가기 시뮬레이션 |

새로 생성된 실제 센서·네트워크·녹음 권한은 없다. ViewModel은 Activity/View를 보관하지 않고 화면 관찰은 LifecycleOwner에 연결한다. 회전/창 변경은 ViewModel이 유지하며, 시스템 재생성은 Bundle에 담은 세션 스냅샷으로 복구한다. 전달 중 작업은 원래 ID와 결과 조건을 유지해 다시 대기한다. 이전 ViewModel의 종료된 콜백과 오래된 요청 응답은 상태를 덮어쓰지 않는다. 앱 강제 종료 뒤 영구 복원은 이번 범위가 아니다.

## 브랜치 변경 범위와 통합 경계

2026-11-15 `git fetch --no-tags origin`으로 확인한 `origin/feature/vehicle-status-ui`의 기준 커밋은 `b3b21f4`다. 구현 커밋 전 로컬 HEAD와 원격 HEAD는 동일했다. 이번 변경은 기존 파일 3개 수정과 새 파일 38개 추가로 구성된다.

| 범위 | 확인 결과 |
| --- | --- |
| 기존 `app/build.gradle.kts` | 개발 메뉴의 `BuildConfig.DEBUG` 사용을 위해 BuildConfig 생성만 활성화. 의존 라이브러리·SDK 버전·applicationId 변경 없음 |
| 기존 `AndroidManifest.xml` | 독립 시연용 `DrivingStatusActivity`를 launcher로 추가. 기존 Activity 교체나 권한 추가 없음 |
| 기존 `README.md` | 현재 구현 상태와 실행 안내 반영 |
| 신규 코드 | `app`의 진입점, `convoy`의 화면·상태·모의 데이터, `voice/data`의 모의 음성 상태, 해당 화면 전용 리소스·테스트 |
| 다른 기능 | `group`, `route`, `recommendation`, `history`, `core`의 기존 파일 변경 및 코드 참조 없음 |

독립 실행 가능한 **한 페이지**이며 기존 `:app` 모듈과 앱 패키지를 사용한다. 별도 Gradle 모듈로 격리한 구현은 아니다. 기능 간 참조는 `app → convoy → voice`이고, `voice`의 지연 실행 인터페이스를 자체 소유하도록 정리해 `voice → convoy` 역방향 참조를 제거했다. 현재 ViewModel은 모의 저장소와 음성 상태를 조립하며 실제 SDK·서버·녹음은 연결하지 않는다.

원격 `feature/group-lobby`에는 `MainActivity` launcher와 대기실 Activity가 별도로 존재한다. 해당 브랜치를 병합하거나 수정하지 않았다. 향후 통합 시 Manifest의 launcher를 하나로 정하고 대기실에서 활성 여행을 전달해야 한다. 리소스는 `convoy_`/`Convoy`/`Theme.CoDrive.Driving` 이름으로 구분한다.

개인 IDE 설정 `.idea/`, APK와 빌드 산출물은 커밋 대상에서 제외한다. 작업 공간의 `Develop_docs`는 이 Git 저장소의 상위 디렉터리에 있으므로 그곳의 진행 기록은 로컬 문서로 유지하고, 저장소에서 필요한 실행 안내는 이 문서에 포함한다.

## 화면과 계산

- 카드 순서는 내 차 → 차량 A → 차량 B다. 차량 수와 음성 참여 수는 실제 모의 세션에서 계산한다.
- 비교선 좌표는 `1 - 남은 거리 / 최대 남은 거리`다. 완료율이나 차량 간 실제 거리로 표시하지 않는다. 자동차와 이름표는 겹침에 따라 세로 레인을 바꾸며, 자동차와 연결선의 가로 좌표는 유지한다.
- 온라인·AVAILABLE·동일 목적지 ID/버전·유효한 안내 상태인 필드만 비교한다. 거리와 ETA의 참여 여부는 별개다.
- ETA는 관측 시각 + 남은 초다. 오래된 값을 현재 시각에 다시 더하지 않는다. 0거리만으로 도착을 확정하지 않는다.
- 모의 센서 지연은 `>10초`, 연결 끊김은 `>30초`다. heartbeat는 센서 관측 시각을 바꾸지 않는다. 실제 SDK에는 이 모의 시간 제한을 그대로 적용하지 않는다.
- 비공유 값은 저장소에서 수치 자체를 제거한다. 미지원·권한 없음·로딩·정보 없음·실패를 서로 구분한다. 경고 미조회는 정상으로 표시하지 않는다.
- 내 요청만 생성/교체/취소한다. 전달 중 중복 입력을 무시하며 실패해도 이전에 공유된 요청은 유지한다. 서버 성공을 다른 차량의 확인으로 표현하지 않는다.
- 음성은 기본 미참여이고, 참여 성공 시 마이크는 꺼져 있다. 나가기/연결 취소는 늦게 도착한 성공 콜백을 무효화한다.
- 넓은 창은 카드 3열, 약 1000dp 미만은 세로 목록이다. 짧은 창에서는 부가 정보를 상세 보기로 옮긴다. 밝은/어두운 색상 리소스를 제공한다.

## Pleos 실연동 경계

이번 구현은 프론트 시연이다. Pleos 라이브러리 의존성이나 검증되지 않은 SDK 호출을 추가하지 않았다. 확인한 공식 문서상 [Vehicle SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)는 현재 차량의 Vehicle Service와 연결하고, [NaviHelper SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)는 Navi Service의 안내 정보를 제공한다. 다른 차량은 각 차량 앱 → 그룹 서버 → 저장소 경로가 필요하다.

`VehicleStatusSource`의 구현체에서 설치된 SDK 버전의 capability·권한·단위를 검증한 뒤 아래 필드로 정규화한다. SDK 객체를 View에 전달하지 않는다.

| 입력 후보 | 정규화 대상 |
| --- | --- |
| EvBattery / Fuel 조회·구독 | `battery`, `fuel` (각각 0~100, 서로 합산하지 않음) |
| Odometer 현재 트립 | `drivingSeconds`, `tripDistance` |
| Tire / Safety의 검증된 경고 | 종류·심각도·관측 시각이 있는 `alerts` |
| NaviHelper 목적지 정보 | 목적지 ID/버전, `distance`(m), `remainingSeconds`(s), 해당 관측 시각 |
| [목적지 도착 이벤트](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners) | `navigation=ARRIVED` |
| 실제 CarUxRestrictions 처리 | `actionsAllowed`, `detailsAllowed` |

각 필드에 MOCK/VEHICLE_SDK/NAVIHELPER 등 원출처와 UTC 관측 시각을 보관한다. 전달한 서버를 센서의 원출처로 바꾸지 않는다. 실제 연결 실패를 모의 수치로 숨기지 않는다. 주행가능거리/실제 충전 여부는 제공 경로 검증 전 기본 화면에서 숨긴다. 차량 제어 set API, 임의 DO 선언, 주행 제한 우회는 추가하지 않았다.

실연동 시 남은 작업은 SDK 의존성과 조회 권한 구성, 현재 차량 어댑터, 다른 차량의 그룹 통신과 요청 응답, 실제 음성 서비스, 주행 제한 연결, 대기실에서 활성 여행을 전달하는 앱 조립 코드다.

## 검증 기록

2026-11-15 커밋 전 의존성 정리 후 `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`를 한 번 실행했다. **APK 빌드 성공, 단위 테스트 29개 통과, lint 오류 0개·경고 6개**다. 경고는 기존 SDK/빌드 도구 업데이트 안내와 레이아웃·미사용 색상 개선 항목이며, 이번에는 에뮬레이터 UI 테스트를 반복하지 않았다.

2026-11-14 자동차 표시 개선: `:app:assembleDebug --offline --no-configuration-cache` 성공. 2026-11-15 설치 스크립트가 `emulator-5554`의 Pleos AVD를 확인하고 최신 APK 설치(`Success`) 및 Activity 시작 응답까지 확인했다. 시각 확인은 사용자 확인 대기다. 작업 공간의 `Develop_docs/CoDrive_Frontend_Progress.md`와 `CoDrive_Frontend_Test_Guide.md`에 진행사항과 직접 테스트 절차를 기록한다. 아래 결과는 자동차 표시 개선 이전의 초기 검증 기록이다.

- 로컬 JDK에서 JUnit **28개 통과**: 거리/ETA, 관측 시각 차이, 품질/연결 제외, ID/버전, 0/누락/NaN/무한대, privacy, 시간 경계, 요청 중복/취소 실패/늦은 응답, 구독 해제, 직렬화 복원, 음성 실패/취소/음소거, 경고 보존, 서울/로스앤젤레스/UTC의 14:30 시연 시작.
- Gradle `:app:testDebugUnitTest` **29개 통과**(위 28개 + 기존 예제 1개). `:app:lintDebug` **오류 0개, 경고 10개**: 기존 target SDK/Gradle 업데이트 안내 2개와 그리기 할당·배경 중복·기준선 정렬·미사용 색상에 대한 개선 제안 8개. 보고서는 `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html`에 생성된다.
- 설치된 Android 37 SDK 및 캐시된 AndroidX 라이브러리로 **전체 앱 Java 컴파일 통과**.
- AAPT2로 **앱 XML 리소스 컴파일 및 의존 리소스와의 링크 통과**.
- Android Studio의 Gradle 빌드로 **디버그 APK 생성 및 Pleos 설치·실행 성공**. Pleos의 차량 정보·공조 영역과 함께 앱이 표시되는 것을 확인했다. 휴식 요청과 음성 참여를 직접 조작해 공유 상태, 취소 버튼, 3/3 참여 및 기본 마이크 꺼짐을 확인했다.
- **Pleos Connect API 34에서 계측 테스트 2개 통과**. `DrivingStatusScreenTest`가 세 창 크기(1280×720dp, 960×600dp, 640×600dp)의 7개 시나리오에서 텍스트 높이와 64dp 버튼 영역을 검사하고, Activity 재생성 시 여행·요청 ID 유지를 검사한다. 결과는 `app/build/reports/androidTests/connected/debug/index.html`에 생성된다.
- 설치된 Android Studio Layoutlib로 동일 네이티브 View의 세 창 크기와 야간·오프라인·밀집 마커·0거리 화면을 추가 렌더링해 확인했다. 이 크기들은 앱 창 레이아웃 검사용이며 Pleos의 물리 해상도를 뜻하지 않는다.
- 에이전트의 셸에서는 Gradle/ADB의 소켓 사용이 제한돼 직접 실행할 수 없었다. Android Studio의 빌드·실행·테스트 기능으로 검증했다. Pleos 첫 부팅은 에뮬레이터 프로세스 SIGSEGV로 종료됐으나 재실행 후 설치·화면 조작·계측 테스트가 정상 완료됐다.
- 차량 SDK·서버·실제 통화·실차 주행 검증은 아직 수행하지 않았다.

## 화면 기록

아래 이미지는 자동차 표시 개선 이전의 화면이다.

- [Pleos에서 휴식 요청·음성 참여 확인](screenshots/pleos-request-voice.png): 실제 에뮬레이터 캡처. 시연 시각의 시간대 보정 전 캡처이므로 ETA 시각은 현재 빌드와 다르다.
- 네이티브 Layoutlib 창 크기 확인: [1280×720](screenshots/layoutlib-1280x720-normal.png), [960×600](screenshots/layoutlib-960x600-normal.png), [640×600](screenshots/layoutlib-640x600-normal.png), [야간 경고](screenshots/layoutlib-1280x720-warnings-night.png).
