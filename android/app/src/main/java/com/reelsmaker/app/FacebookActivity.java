package com.reelsmaker.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.List;

/** إدارة أسماء صفحات فيسبوك وتجهيز/مشاركة الريلز يدويًا. النشر الآلي يحتاج Meta OAuth وصلاحيات معتمدة. */
public class FacebookActivity extends Activity {
    static final String FB_PACKAGE = "com.facebook.katana";
    SharedPreferences sp;
    LinearLayout pages;
    EditText pageName, caption;
    TextView info;
    Button next;
    File pending;
    boolean asked = false;

    int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    JSONArray savedPages() {
        try { return new JSONArray(sp.getString("fb_pages", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
    CheckBox option(LinearLayout root, String label, String key, boolean def) {
        CheckBox c = new CheckBox(this); c.setText(label); c.setTextColor(MainActivity.TXT);
        c.setChecked(sp.getBoolean(key, def));
        c.setOnCheckedChangeListener((b, v) -> sp.edit().putBoolean(key, v).apply());
        root.addView(c); return c;
    }
    void refreshPages() {
        pages.removeAllViews(); JSONArray a = savedPages();
        if (a.length() == 0) {
            TextView empty = new TextView(this); empty.setText("لسه مفيش صفحات محفوظة."); empty.setTextColor(MainActivity.DIM); pages.addView(empty);
        }
        for (int i=0;i<a.length();i++) {
            final String name = a.optString(i, ""); if (name.isEmpty()) continue;
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = new TextView(this); label.setText("📄 " + name); label.setTextColor(MainActivity.TXT);
            Button del = new Button(this); del.setText("حذف"); del.setAllCaps(false);
            del.setOnClickListener(v -> {
                JSONArray old=savedPages(), out=new JSONArray();
                for(int k=0;k<old.length();k++) if(!name.equals(old.optString(k))) out.put(old.optString(k));
                sp.edit().putString("fb_pages",out.toString()).apply(); refreshPages();
            });
            row.addView(label,new LinearLayout.LayoutParams(0,-2,1)); row.addView(del,new LinearLayout.LayoutParams(dp(82),dp(42)));
            pages.addView(row);
        }
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); sp=getSharedPreferences("reels",MODE_PRIVATE);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(22),dp(16),dp(20)); root.setBackgroundColor(MainActivity.BG); root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView title=new TextView(this); title.setText("📘 النشر على فيسبوك"); title.setTextSize(20); title.setGravity(Gravity.CENTER); title.setTextColor(MainActivity.TXT); root.addView(title);
        TextView note=new TextView(this); note.setText("أضف أسماء الصفحات المرتبطة بحسابك. عند المشاركة افتح فيسبوك واختر الصفحة المطلوبة من داخل التطبيق."); note.setTextColor(MainActivity.DIM); note.setPadding(0,dp(12),0,dp(8)); root.addView(note);
        Button login=new Button(this); login.setText("🌐 فتح فيسبوك لتسجيل الدخول"); login.setAllCaps(false); login.setTextColor(Color.WHITE); login.setBackgroundColor(MainActivity.ACC); login.setOnClickListener(v -> openFacebook()); root.addView(login,new LinearLayout.LayoutParams(-1,dp(46)));
        TextView h=new TextView(this); h.setText("صفحاتي المحفوظة في التطبيق"); h.setTextColor(MainActivity.ACC2); h.setPadding(0,dp(14),0,dp(5)); root.addView(h);
        pages=new LinearLayout(this); pages.setOrientation(LinearLayout.VERTICAL); root.addView(pages);
        pageName=new EditText(this); pageName.setSingleLine(true); pageName.setHint("اسم الصفحة (للتذكير والاختيار)"); pageName.setHintTextColor(MainActivity.DIM); pageName.setTextColor(MainActivity.TXT); root.addView(pageName);
        Button add=new Button(this); add.setText("➕ حفظ الصفحة"); add.setAllCaps(false); add.setTextColor(Color.WHITE); add.setBackgroundColor(MainActivity.ACC2);
        add.setOnClickListener(v->{String n=pageName.getText().toString().trim(); if(n.isEmpty()){toast("اكتب اسم الصفحة");return;} JSONArray a=savedPages(); for(int i=0;i<a.length();i++)if(n.equalsIgnoreCase(a.optString(i))){toast("الصفحة محفوظة بالفعل");return;} a.put(n); sp.edit().putString("fb_pages",a.toString()).apply(); pageName.setText(""); refreshPages(); toast("تم حفظ اسم الصفحة");});
        root.addView(add,new LinearLayout.LayoutParams(-1,dp(44)));
        TextView server=new TextView(this); server.setText("النشر التلقائي الرسمي"); server.setTextColor(MainActivity.ACC2); server.setPadding(0,dp(16),0,dp(4)); root.addView(server);
        TextView req=new TextView(this); req.setText("النشر التلقائي المباشر على الصفحات يحتاج Meta App ID وتسجيل دخول OAuth وصلاحيات pages_show_list وpages_manage_posts ومراجعة Meta. تسجيل الدخول هنا لا يقرأ كلمة السر ولا يحفظ كوكيز فيسبوك."); req.setTextColor(MainActivity.DIM); req.setTextSize(12); root.addView(req);
        option(root,"جهّز فيديوهات فيسبوك تلقائيًا بعد انتهاء الريلز","fb_on",false);
        option(root,"احتفظ بنسخة ZIP في التنزيلات","fb_keep",true);
        TextView hm=new TextView(this); hm.setText("النشر اليدوي عبر تطبيق فيسبوك"); hm.setTextColor(MainActivity.ACC2); hm.setPadding(0,dp(16),0,dp(4)); root.addView(hm);
        caption=new EditText(this); caption.setHint("وصف الفيديو (اختياري)"); caption.setHintTextColor(MainActivity.DIM); caption.setTextColor(MainActivity.TXT); caption.setMinLines(2); caption.setText(sp.getString("fb_caption","")); root.addView(caption);
        info=new TextView(this); info.setTextColor(MainActivity.DIM); info.setPadding(0,dp(12),0,dp(8)); root.addView(info);
        next=new Button(this); next.setAllCaps(false); next.setTextColor(Color.WHITE); next.setBackgroundColor(MainActivity.ACC); next.setOnClickListener(v->shareNext()); root.addView(next,new LinearLayout.LayoutParams(-1,dp(50)));
        TextView how=new TextView(this); how.setText("اضغط نشر الفيديو التالي، ثم اختر فيسبوك. من داخل فيسبوك اختار الصفحة الصحيحة واضغط نشر بنفسك. ارجع للتطبيق وأكد إتمام النشر حتى ينتقل للفيديو التالي. أسماء الصفحات المحفوظة هنا للتنظيم فقط؛ لا تعني أن فيسبوك سمح بالتبديل أو النشر الآلي."); how.setTextColor(MainActivity.DIM); how.setTextSize(12); how.setPadding(0,dp(12),0,0); root.addView(how);
        ScrollView scroll=new ScrollView(this); scroll.addView(root); setContentView(scroll); refreshPages(); refresh();
    }
    void openFacebook() {
        try { Intent i=getPackageManager().getLaunchIntentForPackage(FB_PACKAGE); if(i!=null) startActivity(i); else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/"))); }
        catch(Exception e){ startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.facebook.com/"))); }
    }
    @Override protected void onPause(){ super.onPause(); if(caption!=null)sp.edit().putString("fb_caption",caption.getText().toString()).apply(); }
    @Override protected void onResume(){ super.onResume(); if(pending!=null&&!asked){ asked=true; new AlertDialog.Builder(this).setTitle("هل أكملت النشر؟").setMessage("ارجع من فيسبوك بعد الضغط على نشر. هل تم نشر الفيديو بالفعل؟").setPositiveButton("أيوه، تم النشر",(d,w)->{if(pending!=null)pending.delete();pending=null;asked=false;refresh();}).setNegativeButton("لسه / فشل",(d,w)->{pending=null;asked=false;refresh();}).setOnCancelListener(d->{asked=false;}).show(); } refresh(); }
    void refresh(){int n=FacebookQ.list(this).size();info.setText(n==0?"مفيش فيديوهات في الطابور. فعّل التجهيز التلقائي وشغّل ريلز جديدة.":"فيديوهات جاهزة للنشر: "+n);next.setEnabled(n>0);next.setText(n>0?"انشر الفيديو التالي ("+n+" متبقي)":"لا يوجد فيديو");}
    void shareNext(){List<File> q=FacebookQ.list(this);if(q.isEmpty())return;File f=q.get(0);String cap=caption.getText().toString().trim();Intent i=new Intent(Intent.ACTION_SEND);i.setType("video/mp4");Uri uri=FacebookVideoUri.forFile(f);i.putExtra(Intent.EXTRA_STREAM,uri);if(!cap.isEmpty())i.putExtra(Intent.EXTRA_TEXT,cap);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("video",uri));
        try{getPackageManager().getPackageInfo(FB_PACKAGE,0);i.setPackage(FB_PACKAGE);}catch(PackageManager.NameNotFoundException ignored){}
        pending=f;try{startActivity(i.getPackage()!=null?i:Intent.createChooser(i,"مشاركة على فيسبوك"));}catch(Exception e){pending=null;toast("مقدرتش أفتح المشاركة. اتأكد إن فيسبوك متثبت.");}}
}
