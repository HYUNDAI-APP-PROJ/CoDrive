# CoDrive 기획서의 Pleos 가이드 대조 검토

검토일: 2026년 11월 13일

CoDrive 공모전 기획서에서 Pleos 가이드와 충돌할 수 있는 기능, SDK 제약, 필수 설계가 빠진 항목을 정리했습니다. 우선 결정할 사항은 앱 카테고리와 주행 중 기능 범위입니다. 이 결정에 따라 오락 기능의 포함 여부와 주행 제한 구현 범위가 달라집니다.

## 검토 범위와 판단 기준

- 원본 문서: `/Users/kyw10987/Desktop/공모전/대회 양식.docx`
- 주요 검토 대상: 문서 02의 앱 주요 기능, 사용 시나리오, 개발 계획
- 공식 근거: Pleos Playground의 앱 설계, 개발, SDK, UI 및 App Market 심사 가이드
- 보조 확인: 현재 CoDrive 소스의 주행 제한, 테마, 음성 입력 구현
- 검토 방법: 문서와 소스의 정적 검토. 실차 및 런타임 동작 시험은 포함하지 않음

| 판단 | 의미 |
| --- | --- |
| 명확한 오류 | 문서의 기술 용어가 공식 SDK의 기능 정의와 다름 |
| 조건부 정책 충돌 | 앱 카테고리나 실제 구현 방식에 따라 금지 정책에 해당함 |
| 심사 위험 | 정책 취지에 비춰 위험이 있으나 현재 설명만으로 위반을 확정할 수 없음 |
| 기술 제약 | SDK가 허용하는 범위에 맞춰 구현 범위를 구체화해야 함 |
| 설계 누락 | 가이드 준수 여부를 판단하는 데 필요한 정책이나 구현 계획이 없음 |

문서에 설계가 없다는 사실만으로 실제 앱의 정책 위반을 확정하지 않습니다. App Market의 출시 심사 기준과 공모전의 기획서·목업 제출 요건도 구분합니다.

## 항목 요약

| 번호 | 검토 항목 | 판단 | 우선순위 |
| --- | --- | --- | --- |
| 1 | 내비게이션 카테고리와 룰렛·사다리타기 결합 | 조건부 정책 충돌 | 최우선 |
| 2 | 주행 중 기능 범위와 CarUxRestrictions 연동 | 설계 누락 | 최우선 |
| 3 | 연비·도착·알림 횟수 기반 랭킹과 업적 | 심사 위험 | 높음 |
| 4 | 음성 입력을 TTS로 표기 | 명확한 오류 | 즉시 수정 |
| 5 | LLM 연동 방식과 제공 종료된 SDK 구분 | 기술 계획 보완 | 높음 |
| 6 | 전체 일정과 내비게이션 경유지 수 | 기술 제약 | 높음 |
| 7 | 차량별 데이터 지원 여부와 차량 간 공유 | 설계 누락 | 높음 |
| 8 | 공동 결정 이후 차량별 경로 반영 결과 | 설계 누락 | 높음 |
| 9 | 음성채팅·STT·내비 안내의 오디오 처리 | 설계 누락 | 높음 |
| 10 | 위치·음성·여행 기록의 동의와 보관 정책 | 설계 누락 | 높음 |
| 11 | AI 생성 결과의 필터링과 신고 | 심사 요건 누락 | 높음 |

## 1. 내비게이션 카테고리와 오락 기능의 결합

**문서 위치:** 3번 주요 기능의 룰렛·사다리타기, 4번 사용 시나리오의 주차비를 낼 차량 선정

4번 시나리오는 룰렛을 정차 중 사용하는 것으로 설명합니다. 그러나 Maps/Navigation 카테고리의 개발 가이드는 영상·게임 요소 포함을 즉시 반려 항목으로 명시합니다. 해당 카테고리로 등록한다면 정차 중에만 제공한다는 조건만으로 해결되지 않습니다.

CoDrive가 실제 길 안내를 제공하는 Navigation 앱인지, 기본 내비게이션과 연동하는 여행·소통 앱인지 먼저 정해야 합니다. 다른 카테고리로 분류하더라도 실제 기능과 등록 카테고리가 일치해야 하며, 주행 제한은 별도로 적용해야 합니다.

- [ ] 앱의 주 카테고리와 자체 길 안내 제공 여부를 결정한다.
- [ ] Maps/Navigation으로 등록하는 경우 게임 요소를 제외한다.
- [ ] 다른 카테고리에서 오락 기능을 유지한다면 허용 범위와 주행 중 차단 정책을 확인한다.

근거: [Maps/Navigation 앱 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide), [앱 카테고리](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category)

## 2. 주행 중 기능 범위와 제한 상태 연동

**문서 위치:** 그룹 생성·참여, 추천 목록, 투표, 플래너 수정 및 주행 중 음성 요청 시나리오

각 기능을 주차·주행 상태에서 어떻게 제공할지 정의되어 있지 않습니다. 가이드는 플랫폼의 `CarUxRestrictions`와 `ActiveRestrictions`를 확인해 기능별 제한을 적용하도록 요구합니다. DO를 선언하지 않은 앱은 기본적으로 주행 중 사용할 수 없으며, DO를 선언해도 기능별 제한은 직접 반영해야 합니다.

음성 입력이라는 이유만으로 모든 기능을 주행 중 허용할 수는 없습니다. 공개 가이드에서 동승자 조작을 일반적인 제한 예외로 인정하는 근거도 확인하지 못했습니다. 지역별 정책을 임의로 고정하기보다 플랫폼이 전달하는 제한 상태에 따라 동작해야 합니다.

- [ ] 기능별 주차·주행 허용 범위를 표로 정의한다.
- [ ] 주행 사용을 목표로 하는 화면의 DO 적용과 제한 상태 구독 계획을 작성한다.
- [ ] 그룹 설정·상세 편집·오락 기능의 제한과 주행 시작 시 전환 동작을 정의한다.

근거: [주행 규제 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide), [지역별 주행 규제 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy)

## 3. 안전하지 않은 행동을 유도할 수 있는 랭킹과 업적

**문서 위치:** 3번 주요 기능의 차량 간 엔터 요소

검토가 필요한 항목은 다음과 같습니다.

- 연비를 가장 나쁘게 사용한 차량과 ‘최악의 연비’ 업적
- 가장 늦게 도착한 차량을 평가하는 ‘지각차’
- 푸시 알림을 가장 많이 보낸 사람을 선정하는 ‘찡찡 마스터’

가이드는 과속 등 운전자 안전을 저해하는 행동을 조장하거나 미화하는 콘텐츠를 금지합니다. 모든 랭킹이 금지되는 것은 아니지만, 위 항목은 비효율 운전·도착 경쟁·불필요한 알림을 부추기는 보상으로 해석될 위험이 있다는 검토 의견입니다.

시간당 알림 개수 제한과 함께 알림의 운전 관련성도 고려해야 합니다.

- [ ] 비효율 운전과 도착 경쟁을 유도할 수 있는 랭킹·업적을 제거하거나 변경한다.
- [ ] 공동 결정 참여나 여행 완료 등 안전 행동과 충돌하지 않는 기록으로 대체한다.
- [ ] 불필요한 알림 발송 횟수를 보상하는 구조를 제외한다.

근거: [콘텐츠 안전성](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-content-safety), [UI 가이드의 알림 원칙](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro)

## 4. 음성 입력의 STT와 TTS 표기

**문서 위치:** 3번 주요 기능의 ‘음성 → tts변환 후 키워드 전송’

음성을 텍스트로 변환하는 기능은 STT입니다. TTS는 텍스트를 음성으로 읽는 기능입니다. 해당 문구는 아래 흐름으로 수정해야 합니다.

`음성 → STT → 텍스트에서 의도·키워드 추출 → 그룹 전달 → 필요한 경우 수신 차량에서 TTS 출력`

SpeechToText SDK는 인식 결과를 텍스트로 반환하며, 휴식 요청 전송이나 투표 찬성 같은 앱 동작 자체를 실행하지 않습니다.

- [ ] 음성 입력의 TTS 표기를 STT로 수정한다.
- [ ] 음성 인식, 의도 해석, 앱 동작 실행, 음성 출력의 역할을 구분한다.

근거: [SpeechToText SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro), [TextToSpeech SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro)

## 5. LLM 연동 방식과 SDK 제공 범위

**문서 위치:** 3번 주요 기능의 LLM 기반 추천, 5번 개발 계획

Pleos LLM SDK는 2026년 10월 19일자로 제공이 종료됐습니다. 개발 계획을 해당 SDK에 의존해 작성하면 현재 제공 범위와 맞지 않습니다. 문서에 단순히 LLM을 사용한다고 적은 것 자체는 오류가 아닙니다.

Gleo AI와 연동하려면 앱 기능과 호출 방식을 스키마로 정의하는 Gleo Interface를 검토해야 합니다. 별도 LLM 서버를 사용할 경우에는 그 구조와 역할을 구분해 작성해야 합니다.

- [ ] Gleo 연동과 별도 LLM 서버 중 사용할 방식을 정한다.
- [ ] 제공 종료된 Pleos LLM SDK에 의존하지 않는 개발 계획을 작성한다.
- [ ] 인식된 명령을 어떤 앱 기능으로 연결할지 정의한다.

근거: [Pleos Playground 릴리스 노트](https://document.pleos.ai/docs/pleos-only/release-note/playground-release-note), [Gleo Interface](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts)

## 6. 전체 일정과 경유지 최대 개수

**문서 위치:** 3번 주요 기능의 전체 일정 자동 생성과 목적지·경유지 설정

NaviHelper에서 지정할 수 있는 경유지는 최대 2개입니다. 문서의 ‘휴게소 → 식당 → 최종 숙소’ 시나리오는 경유지 2개로 이 범위에 들어갑니다.

여러 방문지로 구성된 전체 일정을 한 번에 내비게이션에 등록하는 기능으로 확장하려면 이 제약을 반영해야 합니다. 전체 일정은 CoDrive가 관리하고, 내비게이션에는 다음 구간을 나누어 반영하는 방식을 제안합니다.

- [ ] 앱의 전체 일정과 현재 내비게이션에 적용하는 구간을 구분한다.
- [ ] 경유지 2개를 초과하는 일정의 적용 방식을 정의한다.
- [ ] 구간 전환과 방문지 변경 시 기존 경로 처리 방식을 정한다.

근거: [NaviHelper 경유지 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/waypoint-settings)

## 7. 차량 데이터의 지원 여부와 차량 간 공유

**문서 위치:** 차량 상태·위치 공유, 연료 부족 알림, 연비·이동 거리 기록, 도착 예정 시간 공유

데이터별 조회 API, 권한, 지원 여부 확인 방법과 데이터가 없을 때의 동작이 빠져 있습니다. 차량 모델과 프로퍼티에 따라 지원 범위가 다를 수 있으므로 모든 차량에서 같은 정보를 얻을 수 있다고 전제해서는 안 됩니다.

Vehicle SDK는 capability를, AAOS 프로퍼티는 실제 지원·접근 가능 상태를 확인해야 합니다. FusedLocation은 `isSupported()` 확인이 필요합니다. 각 차량의 SDK 조회와 여러 차량 사이의 데이터 공유도 별도의 구현 범위입니다.

- [ ] 데이터별 API·권한·지원 확인 방법을 정리한다.
- [ ] 미지원·권한 거부·데이터 없음 상태에서 기능 축소와 표시 방식을 정한다.
- [ ] 차량별 데이터를 서버로 전달하고 그룹에 공유하는 절차와 범위를 정의한다.

근거: [API 호환성 및 가용성](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability), [FusedLocation SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro)

## 8. 공동 결정과 차량별 경로 반영 결과

**문서 위치:** 4번 사용 시나리오의 가결된 장소가 세 차량에 반영되는 흐름

NaviHelper의 경로 요청 호출 성공은 경로 탐색 완료를 의미하지 않습니다. 최종 결과는 이벤트 콜백으로 확인해야 합니다. 투표 가결과 각 차량의 실제 내비게이션 반영 완료를 구분하는 설계가 필요합니다.

일부 차량만 반영에 실패한 상황, 통신 단절, 재접속 후 동기화도 정의되어 있지 않습니다. 오래된 위치나 도착 예정 시간을 현재 정보처럼 표시하지 않도록 갱신 시각과 연결 상태를 구분하는 방식을 제안합니다.

- [ ] ‘가결 → 차량별 반영 요청 → 완료·실패 확인’ 단계를 구분한다.
- [ ] 일부 차량 실패 시 안내·재시도·기존 경로 처리 정책을 정한다.
- [ ] 오프라인·재접속·오래된 데이터의 표시와 복구 정책을 정한다.

근거: [NaviHelper 목적지 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations), [하드웨어 접근 정책의 네트워크 대응](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/platform-api-architecture)

## 9. 음성 기능의 오디오 포커스와 생명주기

**문서 위치:** 그룹 음성채팅, 음성 요청·응답, 도착 알림

차량 간 대화, STT 입력, 알림, 내비 안내, 통화와 Gleo 호출이 겹칠 때의 처리 정책이 없습니다. 가이드는 재생 전 오디오 포커스를 요청하고 포커스 상실·일시 상실·볼륨 감쇄 이벤트에 대응하도록 요구합니다.

AI 음성 입력 중 그룹 마이크 송출 여부와 백그라운드·Suspend 진입 및 복귀 시 처리도 정의해야 합니다. TTS 등의 음성 안내 출력에는 150~200ms 선행 묵음을 권장하는 별도 가이드가 있습니다.

- [ ] 오디오 용도별 포커스 요청과 상실·복구 동작을 정의한다.
- [ ] 통화·내비 안내·Gleo 호출과 겹치는 상황을 검증 계획에 포함한다.
- [ ] STT 중 그룹 송출, 백그라운드 유지, Suspend 중단·복구 정책을 정한다.

근거: [오디오 포커스 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development), [음성 안내 선행 묵음 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/voice-guidance-preroll-silence-guide)

## 10. 개인정보 처리와 공유 동의

**문서 위치:** 위치·음성·차량 상태 공유, 여행 기록, QR 저장

수집 항목, 이용 목적, 공유 대상, 보관 기간, 삭제 및 동의 철회 방식이 빠져 있습니다. 양식 뒤의 개인정보 동의서는 공모전 운영을 위한 것으로, CoDrive 이용자의 앱 데이터 처리 동의와 구분해야 합니다.

위치·음성 등의 필요성을 이용자에게 설명하고 동의 절차를 제공해야 합니다. 저장이 필요한 민감 데이터의 암호화와 로그 마스킹도 개발 계획에 포함해야 합니다. QR 공유의 공개 범위와 만료·삭제 방식은 추가 설계 제안입니다.

- [ ] 앱 이용자 대상 개인정보 고지와 동의 절차를 설계한다.
- [ ] 데이터별 수집·공유·저장 범위와 보관·삭제·철회 정책을 정한다.
- [ ] 민감 데이터 보호와 여행 카드 공유 범위를 정의한다.

근거: [개인정보 보호 설계](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/security-privacy), [개인정보 심사 기준](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-user-privacy-security), [앱 보안 가이드](https://document.pleos.ai/docs/pleos-only/app-registration-guide/app-security-guide)

## 11. AI 생성 콘텐츠의 필터링과 신고

**문서 위치:** AI 장소·일정 추천

App Market 심사 가이드는 AI 생성 콘텐츠를 사용하는 앱에 부적절한 콘텐츠 필터링과 신고 기능을 요구합니다. 현재 기획서에는 해당 내용이 없습니다.

장소·좌표·영업 여부를 확인하는 절차도 추천 신뢰성을 위해 제안합니다. 이러한 개별 검증 절차가 각각 명시된 의무라는 뜻은 아닙니다.

- [ ] 위험하거나 부적절한 AI 결과의 필터링 정책을 정한다.
- [ ] 이용자가 문제 있는 결과를 신고할 수 있도록 한다.
- [ ] 장소의 실재 여부와 이용 가능성을 검증하는 범위를 정한다.

근거: [콘텐츠 안전성의 사용자 제작 및 AI 생성 콘텐츠 기준](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-content-safety)

## 개발 계획에 추가할 항목

현재 기획서의 5번 개발 계획은 비어 있습니다. 위 항목의 구현 방법과 함께 다음 내용을 작성해야 준수 계획을 검토할 수 있습니다.

| 항목 | 작성할 내용 | 공식 근거 |
| --- | --- | --- |
| SDK와 API | 기능별 API, 권한, 지원 여부 확인, 실패 처리 | [SDK 개요](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/) |
| 화면 구성 | 분할 창·전체 창 대응과 크기 변경 시 핵심 기능 유지 | [앱 구조 및 동작 모델](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model) |
| 가독성 | 짧은 문장, 충분한 대비, 실차 기준 글자 크기 검증 | [UI 디자인 가이드](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro) |
| 테마 | 주간·야간 조명 환경에 따른 밝기·대비 자동 조정 | [UI 디자인 가이드](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro) |
| 상태 복원 | 화면 전환·백그라운드 복귀·프로세스 재생성 대응 | [앱 구조 및 동작 모델](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model) |
| 백그라운드 | 위치 공유·음성 통신의 유지·중단·재개 조건 | [앱 구조 및 동작 모델](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model) |
| 성능 | 해당 카테고리의 CPU 기준과 RAM 400MB 이하 유지 계획 | [성능 및 리소스 효율](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/performance-resource-mgmt) |

## 현재 소스에서 추가로 확인한 사항

아래는 기획서 내용과 별도로 확인한 현재 목업의 구현 상태입니다.

| 항목 | 확인 결과 | 소스 근거 |
| --- | --- | --- |
| 주행 제한 | CarUxRestrictions 연동과 DO 선언이 확인되지 않음 | [AndroidManifest.xml](../app/src/main/AndroidManifest.xml), [MainActivity.java](../app/src/main/java/com/codrive/app/MainActivity.java) |
| 주간·야간 테마 | 두 테마 정의가 같고 Java 화면 색상도 고정됨 | [기본 테마](../app/src/main/res/values/themes.xml), [야간 테마](../app/src/main/res/values-night/themes.xml), [Ui.java](../app/src/main/java/com/codrive/core/ui/Ui.java) |
| 음성 입력 | 일반 Android SpeechRecognizer를 사용하며, 서비스가 없으면 텍스트 입력을 안내함. Pleos 음성 서비스와 주행 제한을 함께 검증할 필요가 있음 | [SpeechInputController.java](../app/src/main/java/com/codrive/core/speech/SpeechInputController.java) |

일반 Android SpeechRecognizer 사용 자체를 정책 위반으로 확정하지 않았습니다. 또한 공모전은 목업·프로토타입 제출을 인정하므로 현재 미구현 상태만으로 공모전 제출이 불가능하다고 판단하지 않았습니다. 참고: [공모전 공식 공고](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000001)

이 검토에서는 원본 Word 문서와 앱 소스를 변경하지 않았습니다.
