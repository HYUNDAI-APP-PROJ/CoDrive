/**
 * 여러 도메인에서 공유하는 음성 인식과 음성 합성 코드를 배치하는 패키지.
 *
 * <p>STT로 음성을 텍스트로 변환하고 TTS로 텍스트를 음성으로 읽는다.
 * 인식한 문장의 의미와 후속 동작은 각 도메인에서 판단한다.
 * 차량 간 음성 채널 관리와 채널별 송신·수신 제어는 voice 도메인에서 담당한다.
 * 현재 SpeechInputController가 Android STT의 시작·취소·결과·오류를 처리한다. TTS는 아직 구현하지 않았다.
 */
package com.codrive.core.speech;
