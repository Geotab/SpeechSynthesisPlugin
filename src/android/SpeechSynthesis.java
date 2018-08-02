package org.apache.cordova.speech;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.AbstractList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.speech.tts.Voice;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

public class SpeechSynthesis extends CordovaPlugin implements OnInitListener, OnUtteranceCompletedListener {

    private static final String LOG_TAG = "TTS";
    private static final String ANDROID_TTS_PKG_STRING = "com.google.android.tts";
    private static final int STOPPED = 0;
    private static final int INITIALIZING = 1;
    private static final int STARTED = 2;
    private TextToSpeech mTts = null;
    private int state = STOPPED;
    private CallbackContext startupCallbackContext;
    private CallbackContext callbackContext;
    private boolean isGoogleTtsAvailable = true;

    private Set<Voice> voiceList = null;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        PluginResult.Status status = PluginResult.Status.OK;
        String result = "";
        this.callbackContext = callbackContext;

        try {
            if (action.equals("speak")) {
                JSONObject utterance = args.getJSONObject(0);
                String text = utterance.getString("text");

                String lang = utterance.optString("lang", "en");
                mTts.setLanguage(new Locale(lang));

                String voiceCode = utterance.optString("voiceURI", null);
                if (voiceCode == null) {
                    JSONObject voice = utterance.optJSONObject("voice");
                    if (voice != null) {
                        voiceCode = voice.optString("voiceURI", null);
                    }
                }
                if (voiceCode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    for (Voice v : this.voiceList) {
                        if (voiceCode.equals(v.getName())) {
                            mTts.setVoice(v);
                            //text+=" yay! found the voice!";
                        }
                    }
                }

                float pitch = (float)utterance.optDouble("pitch", 1.0);
                mTts.setPitch(pitch);

                float volume = (float)utterance.optDouble("volume", 0.5);
                // how to set volume

                float rate = (float)utterance.optDouble("rate", 1.0);
                mTts.setSpeechRate(rate);

                if (isReady()) {
                    HashMap<String, String> map = null;
                    map = new HashMap<String, String>();
                    map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, callbackContext.getCallbackId());
                    JSONObject event = new JSONObject();
                    event.put("type","start");
                    event.put("charIndex",0);
                    event.put("elapsedTime",0);
                    event.put("name","");
                    PluginResult pr = new PluginResult(PluginResult.Status.OK, event);
                    pr.setKeepCallback(true);
                    callbackContext.sendPluginResult(pr);
                    mTts.speak(text, TextToSpeech.QUEUE_ADD, map);
                } else {
                    fireErrorEvent(callbackContext);
                }
            } else if (action.equals("cancel")) {
                if (isReady()) {
                    HashMap<String, String> map = null;
                    map = new HashMap<String, String>();

                    mTts.speak("", TextToSpeech.QUEUE_FLUSH, map);
                    fireEndEvent(callbackContext);
                } else {
                    fireErrorEvent(callbackContext);
                }
            } else if (action.equals("pause")) {
                Log.d(LOG_TAG, "Not implemented yet");
            } else if (action.equals("resume")) {
                Log.d(LOG_TAG, "Not implemented yet");
            } else if (action.equals("stop")) {
                if (isReady()) {
                    mTts.stop();
                    callbackContext.sendPluginResult(new PluginResult(status, result));
                } else {
                    fireErrorEvent(callbackContext);
                }
            } else if (action.equals("silence")) {
                if (isReady()) {
                    HashMap<String, String> map = null;
                    map = new HashMap<String, String>();
                    map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, callbackContext.getCallbackId());
                    mTts.playSilence(args.getLong(0), TextToSpeech.QUEUE_ADD, map);
                    PluginResult pr = new PluginResult(PluginResult.Status.NO_RESULT);
                    pr.setKeepCallback(true);
                    callbackContext.sendPluginResult(pr);
                } else {
                    fireErrorEvent(callbackContext);
                }
            } else if (action.equals("startup")) {
                this.startupCallbackContext = callbackContext;
                if (mTts == null) {
                    state = SpeechSynthesis.INITIALIZING;
                    mTts = new TextToSpeech(cordova.getActivity().getApplicationContext(), this,
                            SpeechSynthesis.ANDROID_TTS_PKG_STRING);

                    if (!Arrays.asList(mTts.getEngines()).contains(SpeechSynthesis.ANDROID_TTS_PKG_STRING)) {
                        isGoogleTtsAvailable = false;
                    }
                } else if (isGoogleTtsAvailable) {
            		getVoices(callbackContext);
                }
                PluginResult pluginResult = new PluginResult(status, SpeechSynthesis.INITIALIZING);
                pluginResult.setKeepCallback(true);
                startupCallbackContext.sendPluginResult(pluginResult);
            } else if (action.equals("shutdown")) {
                if (mTts != null) {
                    mTts.shutdown();
                }
                callbackContext.sendPluginResult(new PluginResult(status, result));
            } else if (action.equals("isLanguageAvailable")) {
                if (mTts != null) {
                    Locale loc = new Locale(args.getString(0));
                    int available = mTts.isLanguageAvailable(loc);
                    result = (available < 0) ? "false" : "true";
                    callbackContext.sendPluginResult(new PluginResult(status, result));
                }
            }
            return true;
        } catch (JSONException e) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.JSON_EXCEPTION));
        }
        return false;
    }

    private void getVoices(CallbackContext callbackContext) {
        JSONArray voices = new JSONArray();
        JSONObject voice;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            if (isGoogleTtsAvailable) {
                this.voiceList = mTts.getVoices();
                for (Voice v : this.voiceList) {
                    Locale locale = v.getLocale();
                    voice = new JSONObject();
                    try {
                        voice.put("voiceURI", v.getName());
                        voice.put("name", locale.getDisplayLanguage(locale) + " " + locale.getDisplayCountry(locale));
                        voice.put("lang", locale.getLanguage()+"-"+locale.getCountry());
                        voice.put("localService", !v.isNetworkConnectionRequired());
                        voice.put("quality", v.getQuality());
                        voice.put("default", false);
                    } catch (JSONException e) {
                        // should never happen
                    }
                    voices.put(voice);
                }
            }
        } else {
            Locale[] list = Locale.getAvailableLocales();
            Locale locale;

            for (int i = 0; i < list.length; i++) {
                locale = list[i];
                voice = new JSONObject();
                if (isGoogleTtsAvailable && mTts.isLanguageAvailable(locale) > 0) {
                    try {
                        voice.put("voiceURI", locale.getLanguage()+"-"+locale.getCountry());
                        voice.put("name", locale.getDisplayLanguage(locale) + " " + locale.getDisplayCountry(locale));
                        voice.put("lang", locale.getLanguage()+"-"+locale.getCountry());
                        voice.put("localService", true);
                        voice.put("default", false);
                    } catch (JSONException e) {
                        // should never happen
                    }
                    voices.put(voice);
                }
            }
        }

        PluginResult result = new PluginResult(PluginResult.Status.OK, voices);
        result.setKeepCallback(false);
        startupCallbackContext.sendPluginResult(result);
        mTts.setOnUtteranceCompletedListener(this);
    }

    private void fireEndEvent(CallbackContext callbackContext) {
        JSONObject event = new JSONObject();
        try {
            event.put("type","end");
        } catch (JSONException e) {
            // this will never happen
        }
        PluginResult pr = new PluginResult(PluginResult.Status.OK, event);
        pr.setKeepCallback(false);
        callbackContext.sendPluginResult(pr);
    }

    private void fireErrorEvent(CallbackContext callbackContext)
            throws JSONException {
        JSONObject error = new JSONObject();
        error.put("type","error");
        error.put("charIndex",0);
        error.put("elapsedTime",0);
        error.put("name","");
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, error));
    }

    /**
     * Is the TTS service ready to play yet?
     *
     * @return
     */
    private boolean isReady() {
        return (state == SpeechSynthesis.STARTED) ? true : false;
    }

    /**
     * Called when the TTS service is initialized.
     *
     * @param status
     */
    public void onInit(int status) {
        if (mTts != null && status == TextToSpeech.SUCCESS) {
            state = SpeechSynthesis.STARTED;
            getVoices(this.startupCallbackContext);
        } else if (status == TextToSpeech.ERROR) {
            state = SpeechSynthesis.STOPPED;
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, SpeechSynthesis.STOPPED);
            result.setKeepCallback(false);
            this.startupCallbackContext.sendPluginResult(result);
        }
    }

    /**
     * Clean up the TTS resources
     */
    public void onDestroy() {
        if (mTts != null) {
            mTts.shutdown();
        }
    }

    /**
     * Once the utterance has completely been played call the speak's success callback
     */
    public void onUtteranceCompleted(String utteranceId) {
        fireEndEvent(callbackContext);
    }
}
