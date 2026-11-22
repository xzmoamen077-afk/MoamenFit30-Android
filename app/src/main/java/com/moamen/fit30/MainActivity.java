package com.moamen.fit30;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
public class MainActivity extends Activity {
 private WebView web;
 @Override public void onCreate(Bundle b){super.onCreate(b); web=new WebView(this); web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true); web.setWebViewClient(new WebViewClient()); web.setWebChromeClient(new WebChromeClient()); setContentView(web); if(b==null) web.loadUrl("file:///android_asset/index.html"); else web.restoreState(b);}
 @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);if(web!=null)web.saveState(b);}
 @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
}
