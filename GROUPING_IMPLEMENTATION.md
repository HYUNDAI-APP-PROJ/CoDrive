# Grouping / Waiting 구현

## 실행
Android Studio의 app 실행 구성으로 실행하면 MainActivity → GroupLobbyActivity로 진입합니다.
저장된 유효 그룹이 있으면 GroupWaitingActivity로 복원합니다.
대기실에서 뒤로 가거나 앱을 종료해도 탈퇴·연결 끊김으로 처리하지 않습니다.

## 변경 파일
기존 수정: app/src/main/AndroidManifest.xml (앱 진입점 및 두 Activity 등록).
Gradle 의존성과 기존 공통 테마 및 다른 도메인은 수정하지 않았습니다.
기존 package-info.java는 모두 보존했습니다.

추가 Java (app/src/main/java/com/codrive 기준):
- app/MainActivity.java
- group/data/model/GroupMember.java
- group/data/model/GroupSession.java
- group/data/repository/MockGroupRepository.java
- group/ui/GroupScreenActivity.java
- group/ui/lobby/GroupLobbyActivity.java
- group/ui/lobby/GroupLobbyViewModel.java
- group/ui/lobby/GroupLobbyUiState.java
- group/ui/waiting/GroupWaitingActivity.java
- group/ui/waiting/GroupWaitingViewModel.java
- group/ui/waiting/GroupWaitingUiState.java

추가 리소스 (app/src/main/res 기준):
- layout/activity_group_lobby.xml, activity_group_waiting.xml, item_group_member.xml
- layout-land/activity_group_lobby.xml, activity_group_waiting.xml, item_group_member.xml
- layout/activity_group_lobby_compact.xml, activity_group_waiting_compact.xml
- drawable/group_card.xml, group_field.xml, group_hero.xml, group_hero_code.xml
- color/group_button_fill.xml, group_button_text.xml
- values/group_colors.xml, group_strings.xml, group_styles.xml
- values-night/group_colors.xml

추가 테스트:
- app/src/test/java/com/codrive/group/GroupSessionTest.java
- app/src/androidTest/java/com/codrive/group/MockGroupRepositoryTest.java

## 구현 동작
- 생성·참여 탭, 독립적인 별칭·탑승 인원 초안, 그룹 이름·코드 초안 유지.
- SavedStateHandle로 Activity 재생성 및 시스템 프로세스 복원 지원.
- 입력 검증, 그룹 생성, 충돌 검사한 6자리 영숫자 코드, 리더 등록.
- 같은 설치 내 저장된 대기 그룹 코드로 참여. 없는 코드·시작된 그룹은 참여 거부.
- 코드 복사, 차량·인원 집계, 리더·내 차량·준비·연결 상태 표시.
- 준비 전환, 모든 차량 연결·준비 조건 및 리더 권한을 Repository에서도 검사.
- 여행 시작 후 STARTED 상태를 대기실 내부 완료 화면으로 표시.
- 확인 후 일반 차량만 탈퇴 / 리더는 전체 그룹 해산.
- SharedPreferences JSON 저장, LiveData 변경 관찰, 불변 모델 스냅샷.
- 리더 연결 끊김은 30초 기한을 저장. 재접속 시 취소, 시간 초과 시 종료.
  반복 연결 끊김 이벤트는 기한을 연장하지 않습니다. 재시작 시 기한 재검사.
- 달/해 아이콘을 누르면 주간·야간 모드 즉시 전환 및 선택 유지. 시스템 바·키보드·컷아웃 인셋 반영.
- 창 너비 1000dp 이상·높이 400dp 이상인 가로 창은 안내와 입력/차량 정보 2열.
- 50:50 분할 창은 큰 안내 패널을 줄이고 한 열의 폼/그룹 요약을 표시.
  600dp 미만의 더 좁은 창에서는 차량 별칭·탑승 인원·의사결정 카드도 세로 배치.
- 첨부 스크린샷의 화이트·블랙 팔레트, 어두운 안내 패널과 입력/차량 패널 구조 적용.
- Connect-S-17(Default) 기준: 전체 디스플레이 2560×1440 / 160dpi,
  가로 앱 영역 1700×1184dp. 앱 영역에 41:59 패널과 큰 화면 전용 dp/sp 적용.
- 그룹 이름은 필수, 차량 별칭은 선택(공백·null은 내 차량으로 저장).
- 기본 탑승 인원 2명, 1~9명 증감 버튼. 생성·참여별 입력 상태는 분리 유지.
- 넓은 대기실은 차량 카드 2열과 빈 차량 초대 안내 표시.
- WebView와 사용자용 시연 버튼은 없습니다.

## 로컬 저장소의 범위와 향후 연결
초대 코드는 현재 설치의 저장소에서만 검색합니다. 다른 실제 차량과 통신하지 않습니다.
일반 사용자는 동시에 한 그룹에만 참여합니다. 같은 기기의 리더가 자기 그룹에
일반 차량으로 중복 참여하는 경로는 제공하지 않습니다.
REST/WebSocket 연동은 Repository 경계에서 교체하고 차량 SDK의 주행 제한은
입력·생성·참여 이벤트 진입점에서 적용해야 합니다. 실제 주행 상태는 생성하지 않습니다.

## 테스트용 상태 변경 (일반 UI에 노출하지 않음)
Repository의 addLocalVehicle(groupId, alias, count)로 다른 차량 추가,
updateReadyStatus / setConnected로 준비·연결 상태를 변경할 수 있습니다.
clearLocalIdentityForTesting()은 차량을 삭제하지 않고 테스트 클라이언트만 전환해
joinGroup의 일반 참여 차량 흐름을 검증하는 용도입니다.
MockGroupRepositoryTest는 참여, 권한, 준비/연결 조건, 탈퇴/해산,
리더 재접속·실제 30초 시간 초과를 검증합니다.

## 50:50 분할 화면
- 전체 앱 영역 1700×1184dp, 절반 창 약 840×1184dp를 기준으로 구성했습니다.
- MainActivity와 그룹 Activity는 resizeableActivity=true로 분할 실행을 명시합니다.
- 실제 두 앱의 50:50 배치는 차량/Android 런처에서 선택합니다.
  앱은 할당받은 창 크기에 맞춰 배치를 선택하며, 다른 앱의 창 비율을 강제하지 않습니다.
- layout-w1000dp-h400dp-land는 기존 전체 화면 2열 디자인을 유지합니다.
- layout-w600dp 및 layout-w600dp-h400dp-land는 절반 창용 한 열 배치입니다.
- values-w600dp-h800dp/group_dimens.xml은 절반 창의 글자·터치 크기·여백을 지정합니다.
- 폼과 차량 목록의 ScrollView에 같은 ID를 사용해 크기 변경 후 스크롤 복원을 지원합니다.
- 입력은 SavedStateHandle, 그룹은 Repository/ViewModel에 유지합니다.
  configChanges나 화면 방향 고정으로 Activity 재생성을 막지 않습니다.
- GroupWindowLayoutTest는 생성·참여 폼과 대기실 주요 조작의 절반 창 내 배치,
  전체→절반→전체 리소스 선택, 좁은 창의 차량 입력 세로 배치를 검사합니다.
  연결 기기가 없어 해당 기기용 테스트의 실제 실행은 남아 있습니다.

## 검증 범위
assembleDebug, testDebugUnitTest, lintDebug 실행 성공.
단위 테스트는 전원 연결·준비 조건, 빈 그룹/시작/종료 상태의 출발 거부,
불변 차량 목록과 인원 집계를 검증했습니다.
기기용 테스트는 작성했으며 연결 기기가 없어 실행하지 못했습니다.
실기기/에뮬레이터의 클릭 동작, 복사, 다이얼로그, 프로세스 재시작 복원,
1700×1184 / 840×1184dp 및 1200×600 / 1280×720에서 글자 배율·시스템 영역에 따른 실제 배치와
주간·야간 렌더링은 실행 검수가 남아 있습니다. Lint 성공은 경고 0개를 뜻하지 않습니다.
