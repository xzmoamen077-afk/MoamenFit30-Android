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