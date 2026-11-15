package com.codrive.core.speech;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;

/** Android STT 수명과 취소 이후 도착하는 콜백을 관리합니다. 메인 스레드에서 호출합니다. */
public final class SpeechInputController {
    public interface Listener {
        default void ready() { }
        void partial(String text);
        void processing();
        void result(String text);
        void error(String message);
        void level(float rms);
    }
    private final Context context;
    private SpeechRecognizer recognizer;
    private int generation;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeout;

    public SpeechInputController(Context context) {
        this.context = context;
    }

    public void start(Listener listener) {
        cancel();
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.error("이 기기에 음성 인식 서비스가 없어요. 텍스트로 입력해주세요.");
            return;
        }
        int session = generation;
        timeout = () -> {
            if (session != generation)
                return;
            cancel();
            listener.error("음성 인식 응답이 없어요. 다시 시도하거나 텍스트로 입력해주세요.");
        };
        handler.postDelayed(timeout, 15000);
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(new RecognitionListener() {
                private boolean current() {
                    return session == generation && recognizer != null;
                }
                @Override
                public void onReadyForSpeech(Bundle params) {
                    if (current()) {
                        listener.ready();
                        handler.removeCallbacks(timeout);
                        handler.postDelayed(timeout, 60000);
                    }
                }
                @Override
                public void onBeginningOfSpeech() {}
                @Override
                public void onRmsChanged(float rms) {
                    if (current())
                        listener.level(rms);
                }
                @Override
                public void onBufferReceived(byte[] buffer) {}
                @Override
                public void onEndOfSpeech() {
                    if (current()) {
                        handler.removeCallbacks(timeout);
                        handler.postDelayed(timeout, 10000);
                        listener.processing();
                    }
                }
                @Override
                public void onPartialResults(Bundle results) {
                    if (current())
                        listener.partial(first(results));
                }
                @Override
                public void onResults(Bundle results) {
                    if (!current())
                        return;
                    String text = first(results);
                    cancel();
                    if (text.trim().isEmpty())
                        listener.error("음성이 인식되지 않았어요. 다시 말씀해주세요.");
                    else
                        listener.result(text);
                }
                @Override
                public void onError(int error) {
                    if (!current())
                        return;
                    cancel();
                    listener.error(switch (error) {
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한을 허용해주세요.";
                        case SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "음성 인식 연결을 확인해주세요.";
                        case SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "음성이 인식되지 않았어요. 다시 말씀해주세요.";
                        default -> "음성 입력을 시작하지 못했어요. 다시 시도하거나 텍스트로 입력해주세요.";
                    });
                }
                @Override
                public void onEvent(int type, Bundle params) {}
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            recognizer.startListening(intent);
        } catch (RuntimeException exception) {
            cancel();
            listener.error("음성 인식을 사용할 수 없어요. 텍스트로 입력해주세요.");
        }
    }
    private static String first(Bundle bundle) {
        ArrayList<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return results == null || results.isEmpty() ? "" : results.get(0);
    }
    public void cancel() {
        generation++;
        if (timeout != null) {
            handler.removeCallbacks(timeout);
            timeout = null;
        }
        if (recognizer != null) {
            SpeechRecognizer previous = recognizer;
            recognizer = null;
            previous.cancel();
            previous.destroy();
        }
    }
}
