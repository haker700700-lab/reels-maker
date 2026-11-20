package com.reelsmaker.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** نافذة تسجيل الدخول: بتفتح صفحة المنصة الرسمية، وأول ما المنصة ترجّعنا لرابط الرجوع بنقفل ونسلّم الرابط للشاشة اللي طلبته. */
public class OAuthActivity extends Activity {
    WebView wv;
    String redirect;
    boolean done = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        String url = getIntent().getStringExtra("url");
        redirect = getIntent().getStringExtra("redirect");
        if (url == null || redirect == null) {
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        TextView t = Ui.text(this, "🔐 تسجيل الدخول", 15, Ui.TXT, true);
        Button close = Ui.pill(this, "✕ إلغاء", Ui.CARD);
        top.addView(t, Ui.lp(0, -2, 1f));
        top.addView(close, Ui.lp(-2, Ui.dp(this, 36), 0f));
        root.addView(top);

        wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // نعرّف نفسنا كمتصفح موبايل عادي عشان صفحات الدخول تتفتح كويس
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return check(r.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView v, String u, Bitmap f) {
                check(u);
            }
        });
        root.addView(wv, Ui.lp(-1, 0, 1f));
        setContentView(root);

        close.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        wv.loadUrl(url);
    }

    boolean check(String u) {
        if (done || u == null || !u.startsWith(redirect)) return false;
        done = true;
        Intent i = new Intent();
        i.putExtra("url", u);
        setResult(RESULT_OK, i);
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        if (wv != null) {
            wv.stopLoading();
            wv.destroy();
        }
        super.onDestroy();
    }
}
