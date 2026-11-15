# CoDrive 디자인 평가

2026-11-13. Pleos Playground 공식 가이드, 현재 Java View 구현, Pleos Connect v2.1.0 에뮬레이터를 비교했습니다. 평가 범위는 구현된 추천 화면과 그룹 생성 화면입니다.

## 판단

네이비·민트 색상, 개인/그룹 구분, 프리셋과 중앙 마이크는 추천 목업의 방향에 맞습니다. 핵심 문제는 작은 글자, 창 폭 계산, 사라지는 요청 말풍선, 조작 피드백과 실제 기능처럼 읽히는 안내였습니다. 아래 수정으로 개선했지만, 주간 테마와 차량 주행 제한 연동이 없어 차량 배포 준비가 끝난 상태는 아닙니다.

## 가이드와 대조한 결과

- **색상 대비:** 가이드는 텍스트·아이콘에 4.5:1 이상을 요구합니다. 현재 불투명 기본 색상으로 계산하면 보조 글자/입력 패널은 6.64:1, 본문/입력 패널은 14.51:1, 민트/선택 배경은 7.17:1입니다. 이 색상 조합은 유지했습니다. 비활성 상태와 모든 아이콘을 포괄하는 검사는 아닙니다. [UI 가이드](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro)
- **가독성:** 기존 보조 글자 11sp는 Typography의 최소 권장 12sp보다 작았습니다. 12~14sp 글자도 차량 화면에서 읽기 어렵습니다. 가이드의 가독성 권장에 맞춰 공통 텍스트 하한을 18sp, 입력을 20sp로 올렸습니다. 실차에서의 3mm/5mm 글자 높이는 별도 확인이 필요합니다. [Typography](https://document.pleos.ai/docs/connect/guide/docs-design/foundations/typography), [UI 가이드](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro)
- **창 분할:** 가이드는 portrait·landscape와 창 크기 변화에 대응하도록 요구합니다. 화면 너비에서 임의 상수를 빼던 계산을 실제 부모 영역에 맞추도록 바꿨습니다. 좁은 창에서는 탭과 문맥 정보를 나누고 마이크를 별도 행에 둡니다. [앱 구조 및 동작 모델](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model)
- **조작 상태:** 배경을 교체한 탭·프리셋에서 리플이 사라졌습니다. 리플을 전경에 두고 기본 버튼 의미와 햅틱을 제공했습니다. 버튼 최소 높이와 스위치 터치 영역은 64dp로 늘렸습니다. 이 64dp는 이번 구현 선택이며 공식 최소 수치라는 뜻은 아닙니다. 스위치의 장식적인 확대 애니메이션과 불필요한 영문 문구는 삭제했습니다. [Basic button](https://document.pleos.ai/docs/connect/guide/docs-design/components/actions/basic-button), [Tabs](https://document.pleos.ai/docs/connect/guide/docs-design/components/segmented-menu), [주행 안전을 고려한 설계](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline)

## 함께 수정한 버그

1. **요청 말풍선이 세로 막대로 표시됨:** 부모는 `WRAP_CONTENT`, 내부 텍스트는 기본 `MATCH_PARENT`인 측정 조합에서 텍스트 폭이 1px로 줄었습니다. 말풍선을 대화 영역 폭에 맞췄습니다.
2. **큰 글자에서 입력·버튼 잘림:** 입력창은 2~3줄에 맞춰 높이를 조절하고, 그룹 생성 버튼과 사이드 메뉴는 내용에 맞춰 늘어납니다.
3. **복원된 자동 경로 상태의 라벨 색상 불일치:** 최초 표시에도 현재 토글 값을 반영합니다.
4. **목업이 실제 내비게이션을 변경하는 듯한 안내:** 자동 설정 문구와 하단에 미리보기임을 명시했습니다.
5. **그룹 생성·탭 이동 뒤 남는 키보드:** 화면 교체 전에 기존 입력창의 키보드를 닫습니다.

## 남은 작업

- **주행 제한 연동:** `CarUxRestrictions`를 실제로 구독해 제한 상태에서 키보드·그룹 이름 입력과 정보량, 애니메이션 등을 조정해야 합니다. 현재 차량 SDK 연결이 없으므로 정차 여부를 임의로 판단하는 코드는 추가하지 않았습니다. [주행 안전을 고려한 설계](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline)
- **주간 테마:** `values`와 `values-night`가 같은 색상이고 Java에도 다크 색상이 고정되어 있습니다. 시스템 밝기 모드에 반응하는 색상 리소스가 필요합니다. [UI 가이드](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro)
- **실제 기능:** AI·내비게이션·차량 연동, 나머지 사이드 메뉴는 미구현입니다. 현재 동작을 보여주는 범위를 넘어 새 기능은 추가하지 않았습니다.

## 검증

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest --no-configuration-cache
```

빌드 성공. 단위 검사 4개, Pleos 기기 검사 4개 통과. Lint 오류 0개, 기존 경고 5개입니다. 회귀 검사는 320·560·720·1100dp 영역과 글자 배율 1.0·1.5에서 초기 화면·대화 화면·그룹 생성 화면의 가로 범위와 텍스트 폭을 확인합니다. 초기 화면의 마이크·전송·스위치 겹침도 확인합니다. Pleos에서는 1682dp 전체 앱 창과 829dp 분할 창을 직접 확인했습니다. 실차 글자 크기와 햅틱, 실제 음성 인식 품질은 이번 검증 범위에 포함되지 않습니다.

[수정 후 전체 화면](native-recommendation.png) · [수정 후 대화가 있는 분할 화면](native-recommendation-split.png)
