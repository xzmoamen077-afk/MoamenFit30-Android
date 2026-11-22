package com.moamen.fit30;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView web;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private long lastAiRequestAt = 0L;

    public class PrayerBridge {
        @JavascriptInterface
        public void openChatGPT() {
            runOnUiThread(() -> {
                try {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://chatgpt.com/"));
                    intent.setPackage("com.openai.chatgpt");
                    startActivity(intent);
                } catch (Exception appNotAvailable) {
                    try {
                        android.content.Intent browser = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://chatgpt.com/"));
                        startActivity(browser);
                    } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public void loadPrayerTimes(String date) {
            if (date == null || !date.matches("[0-9]{2}-[0-9]{2}-[0-9]{4}")) {
                sendPrayerError("تاريخ غير صالح");
                return;
            }
            network.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL("https://api.aladhan.com/v1/timingsByCity/" + date + "?city=Cairo&country=Egypt&method=5");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(12000);
                    connection.setReadTimeout(12000);
                    connection.setRequestProperty("Accept", "application/json");
                    if (connection.getResponseCode() != 200) throw new Exception("Prayer service unavailable");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                    StringBuilder body = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                    reader.close();
                    JSONObject root = new JSONObject(body.toString());
                    JSONObject timings = root.getJSONObject("data").getJSONObject("timings");
                    JSONObject result = new JSONObject();
                    result.put("timings", timings);
                    sendPrayerResult(result.toString());
                } catch (Exception e) {
                    sendPrayerError("تعذّر الاتصال بمصدر المواقيت");
                } finally {
                    if (connection != null) connection.disconnect();
                }
            });
        }
        @JavascriptInterface
        public void ask(String requestId, String messagesJson) {
            if (requestId == null || !requestId.matches("[0-9]{1,20}_[0-9]{1,6}") || messagesJson == null || messagesJson.length() > 24000) {
                sendAiResult(requestId, "الطلب غير صالح. جرّب رسالة أقصر.", true);
                return;
            }
            synchronized (MainActivity.this) {
                long now = System.currentTimeMillis();
                if (now - lastAiRequestAt < 30000L) {
                    sendAiResult(requestId, "الخدمة المجانية محدودة بطلبين في الدقيقة. استنى نصف دقيقة وجرب تاني.", true);
                    return;
                }
                lastAiRequestAt = now;
            }
            network.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    JSONArray messages = new JSONArray(messagesJson);
                    JSONObject payload = new JSONObject();
                    payload.put("model", "Olala-v1.0-mini-7A1B");
                    payload.put("messages", messages);
                    payload.put("temperature", 0.5);
                    payload.put("max_tokens", 420);
                    URL url = new URL("https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(60000);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    connection.setRequestProperty("Accept", "application/json");
                    byte[] bytes = payload.toString().getBytes("UTF-8");
                    try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
                    int status = connection.getResponseCode();
                    java.io.InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                    StringBuilder body = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
                        String line;
                        while ((line = reader.readLine()) != null) body.append(line);
                    }
                    if (status < 200 || status >= 300) {
                        String msg = status == 429 ? "وصلنا للحد المجاني مؤقتًا. استنى دقيقة وجرب تاني." :
                            "الخدمة مش متاحة حاليًا (" + status + "). جرّب بعد شوية.";
                        sendAiResult(requestId, msg, true);
                    } else {
                        JSONObject response = new JSONObject(body.toString());
                        String answer = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
                        if (answer.trim().isEmpty()) throw new Exception("Empty model response");
                        sendAiResult(requestId, answer.trim(), false);
                    }
                } catch (Exception e) {
                    sendAiResult(requestId, "تعذّر الاتصال بالمساعد الذكي. تأكد من الإنترنت وجرب مرة تانية.", true);
                } finally {
                    if (connection != null) connection.disconnect();
                }
            });
        }
    }

    private void sendAiResult(String requestId, String message, boolean isError) {
        if (requestId == null) return;
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript("window.onAIResponse(" + JSONObject.quote(requestId) + "," + JSONObject.quote(message) + "," + (isError ? "true" : "false") + ")", null);
        });
    }

    private void sendPrayerResult(String json) {
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript("window.onPrayerTimes(" + JSONObject.quote(json) + ")", null);
        });
    }
    private void sendPrayerError(String message) {
        try {
            JSONObject error = new JSONObject();
            error.put("error", message);
            sendPrayerResult(error.toString());
        } catch (Exception ignored) {}
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.clearCache(true);
        web.addJavascriptInterface(new PrayerBridge(), "AndroidPrayer");
        web.addJavascriptInterface(new PrayerBridge(), "AndroidAI");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onSaveInstanceState(Bundle b) { super.onSaveInstanceState(b); }
    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
    @Override
    protected void onDestroy() { network.shutdownNow(); super.onDestroy(); }
}