package com.reelsmaker.app;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.File;
import java.util.List;

/** Facebook Pages setup, automatic publishing preferences, and the manual share queue. */
public class FacebookActivity extends Activity {
    SharedPreferences sp; LinearLayout pages; TextView queueInfo; Button next, both; File pending; boolean alsoTikTok=false;
    static final String PREF="fb_pages";
    JSONArray saved(){try{return new JSONArray(sp.getString(PREF,"[]"));}catch(Exception e){return new JSONArray();}}
    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onCreate(Bundle b){super.onCreate(b);sp=getSharedPreferences("reels",MODE_PRIVATE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(20),dp(16),dp(16));root.setBackgroundColor(MainActivity.BG);root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView title=new TextView(this);title.setText("📘 النشر على صفحات فيسبوك");title.setTextSize(20);title.setTextColor(MainActivity.TXT);title.setGravity(Gravity.CENTER);root.addView(title);
        Button login=new Button(this);login.setText("🌐 دخول فيسبوك واختيار الصفحات");login.setAllCaps(false);login.setOnClickListener(v->{if(!TikCrypto.ready()){toast("مفتاح التشفير TT_PUB مش متضاف في إعدادات بناء التطبيق.");return;}startActivityForResult(new Intent(this,FacebookLoginActivity.class),61);});root.addView(login);
        TextView head=new TextView(this);head.setText("الصفحات المحفوظة");head.setTextColor(MainActivity.ACC2);head.setPadding(0,dp(12),0,dp(4));root.addView(head);
        pages=new LinearLayout(this);pages.setOrientation(1);root.addView(pages);refreshPages();
        EditText cap=new EditText(this);cap.setHint("وصف الفيديو (اختياري)");cap.setTextColor(MainActivity.TXT);cap.setHintTextColor(MainActivity.DIM);cap.setText(sp.getString("fb_caption",""));root.addView(cap);
        CheckBox auto=new CheckBox(this);auto.setText("انشر تلقائيًا على كل صفحات فيسبوك بعد انتهاء الريلز");auto.setTextColor(MainActivity.TXT);auto.setChecked(sp.getBoolean("fb_server",false));auto.setOnCheckedChangeListener((v,x)->sp.edit().putBoolean("fb_server",x).apply());root.addView(auto);
        CheckBox manual=new CheckBox(this);manual.setText("جهّز الفيديوهات للنشر اليدوي تلقائيًا بعد انتهاء الريلز");manual.setTextColor(MainActivity.TXT);manual.setChecked(sp.getBoolean("fb_on",true));manual.setOnCheckedChangeListener((v,x)->sp.edit().putBoolean("fb_on",x).apply());root.addView(manual);
        cap.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int before,int count){sp.edit().putString("fb_caption",s.toString()).apply();}public void afterTextChanged(android.text.Editable e){}});
        TextView h=new TextView(this);h.setText("النشر اليدوي عبر تطبيق فيسبوك");h.setTextColor(MainActivity.ACC2);h.setPadding(0,dp(16),0,dp(4));root.addView(h);
        queueInfo=new TextView(this);queueInfo.setTextColor(MainActivity.DIM);root.addView(queueInfo);
        next=new Button(this);next.setAllCaps(false);next.setTextColor(Color.WHITE);next.setBackgroundColor(MainActivity.ACC);next.setOnClickListener(v->shareNext());root.addView(next,new LinearLayout.LayoutParams(-1,dp(50)));
        both=new Button(this);both.setAllCaps(false);both.setText("انشر الفيديو التالي على فيسبوك + تيك توك");both.setOnClickListener(v->shareBoth());root.addView(both,new LinearLayout.LayoutParams(-1,dp(48)));refresh();
        TextView note=new TextView(this);note.setTextColor(MainActivity.DIM);note.setText("اضغط نشر الفيديو التالي، وسيتم فتح فيسبوك لمشاركة الفيديو. بعد الانتهاء ارجع للتطبيق للانتقال للفيديو التالي.");root.addView(note);
        ScrollView sv=new ScrollView(this);sv.addView(root);setContentView(sv);
    }
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r!=61||c!=RESULT_OK||d==null)return;try{JSONArray got=new JSONArray(d.getStringExtra("pages")),old=saved();for(int i=0;i<got.length();i++){JSONObject p=got.getJSONObject(i);String id=p.optString("id");boolean exists=false;for(int j=0;j<old.length();j++)if(old.getJSONObject(j).optString("id").equals(id))exists=true;if(!exists)old.put(new JSONObject().put("id",id).put("n",p.optString("name")).put("k",TikCrypto.encBlob(new JSONObject().put("token",p.optString("token")).toString())));}sp.edit().putString(PREF,old.toString()).apply();refreshPages();toast("تم حفظ الصفحات المتاحة ✓");}catch(Exception e){toast("فشل حفظ الصفحات");}}
    void refreshPages(){pages.removeAllViews();JSONArray a=saved();if(a.length()==0){TextView t=new TextView(this);t.setText("مفيش صفحات محفوظة.");t.setTextColor(MainActivity.DIM);pages.addView(t);}for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p==null)continue;String id=p.optString("id");LinearLayout row=new LinearLayout(this);TextView n=new TextView(this);n.setText("📄 "+p.optString("n"));n.setTextColor(MainActivity.TXT);Button del=new Button(this);del.setText("حذف");del.setOnClickListener(v->{JSONArray cur=saved(),out=new JSONArray();for(int j=0;j<cur.length();j++)if(!cur.optJSONObject(j).optString("id").equals(id))out.put(cur.optJSONObject(j));sp.edit().putString(PREF,out.toString()).apply();refreshPages();});row.addView(n,new LinearLayout.LayoutParams(0,-2,1));row.addView(del);pages.addView(row);}}
    void refresh(){List<File> q=FacebookQ.list(this);queueInfo.setText(q.isEmpty()?"مفيش فيديوهات في الطابور.":"فيديوهات جاهزة للنشر: "+q.size());next.setEnabled(!q.isEmpty());next.setText(q.isEmpty()?"لا يوجد فيديو":"انشر الفيديو التالي ("+q.size()+" متبقي)");}
    @Override protected void onResume(){super.onResume();if(pending!=null){pending.delete();pending=null;if(alsoTikTok){alsoTikTok=false;Intent t=new Intent(this,TikTokActivity.class);t.putExtra("share_next_now",true);startActivity(t);return;}}refresh();}
    void shareBoth(){if(TikTokQ.list(this).isEmpty()){toast("مفيش فيديوهات في طابور تيك توك. فعّل تجهيز النشر اليدوي من قسم تيك توك.");return;}alsoTikTok=true;shareNext();}
    void shareNext(){List<File> q=FacebookQ.list(this);if(q.isEmpty())return;File f=q.get(0);String cap=sp.getString("fb_caption","");Intent i=new Intent(Intent.ACTION_SEND);i.setType("video/mp4");i.putExtra(Intent.EXTRA_STREAM,VideoProvider.uriFor(f));if(!cap.isEmpty())i.putExtra(Intent.EXTRA_TEXT,cap);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("video",VideoProvider.uriFor(f)));try{getPackageManager().getPackageInfo("com.facebook.katana",0);i.setPackage("com.facebook.katana");}catch(Exception ignored){}pending=f;try{startActivity(i.getPackage()!=null?i:Intent.createChooser(i,"مشاركة على فيسبوك"));}catch(Exception e){pending=null;toast("تعذر فتح فيسبوك. تأكد من تثبيته.");}}
}
