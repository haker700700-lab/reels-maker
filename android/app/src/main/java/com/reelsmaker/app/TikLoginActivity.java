package com.reelsmaker.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * تسجيل دخول عادي على صفحة التك توك الحقيقية جوه التطبيق (من جهازك أنت، فالتحقق لو اتطلب بيتم هنا).
 * بعد الدخول بنجيب الجلسة ونشفّرها ونبعتها للسيرفر بدل كلمة السر.
 * بنفتح الصفحة بنسخة الكمبيوتر (زي زر «Desktop site» في أي متصفح).
 */
public class TikLoginActivity extends Activity {
    static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    WebView web;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(null);   // نبدأ بجلسة نضيفة عشان الحساب ما يتلخبطش مع حساب تاني
        cm.setAcceptCookie(true);
        web = new WebView(this);
        WebSettings st = web.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setUserAgentString(DESKTOP_UA);
        st.setUseWideViewPort(true);
        st.setLoadWithOverviewMode(true);
        st.setBuiltInZoomControls(true);
        st.setDisplayZoomControls(false);
        cm.setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient());
        web.loadUrl("https://www.tiktok.com/login/phone-or-email/email");

        Button done = new Button(this);
        done.setAllCaps(false);
        done.setText("✅ خلصت تسجيل الدخول — احفظ الحساب");
        done.setTextColor(Color.WHITE);
        done.setBackgroundColor(MainActivity.ACC);
        done.setOnClickListener(v -> finishLogin());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(done, new LinearLayout.LayoutParams(-1, (int) (52 * getResources().getDisplayMetrics().density)));
        setContentView(root);
    }

    void finishLogin() {
        String c = CookieManager.getInstance().getCookie("https://www.tiktok.com");
        if (c == null || !c.contains("sessionid=")) {
            Toast.makeText(this, "لسه مسجّلتش دخول. كمّل تسجيل الدخول في الصفحة الأول.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent r = new Intent();
        r.putExtra("cookies", c);
        setResult(RESULT_OK, r);
        CookieManager.getInstance().removeAllCookies(null);   // مانسيبش الجلسة في التطبيق
        finish();
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
