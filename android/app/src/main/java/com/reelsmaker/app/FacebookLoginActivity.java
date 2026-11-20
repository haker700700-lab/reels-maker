package com.reelsmaker.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/** Opens Meta OAuth in the device browser and receives the official fb{app-id}://authorize callback. */
public class FacebookLoginActivity extends Activity {
    boolean launched=false;
    @Override public void onCreate(Bundle b){super.onCreate(b);TextView t=new TextView(this);t.setText("افتح صفحة تسجيل الدخول الرسمية لفيسبوك، وبعد الموافقة ارجع هنا لاختيار الصفحات.");t.setPadding(32,48,32,48);setContentView(t);if(BuildConfig.FB_APP_ID==null||BuildConfig.FB_APP_ID.trim().isEmpty()){Toast.makeText(this,"FB_APP_ID غير مضاف في إعدادات بناء التطبيق",Toast.LENGTH_LONG).show();finish();return;}if(getIntent()!=null&&getIntent().getData()!=null)handle(getIntent().getData());else launchLogin();}
    void launchLogin(){if(launched)return;launched=true;try{String id=BuildConfig.FB_APP_ID;String redirect="fb"+id+"://authorize";String u="https://www.facebook.com/v24.0/dialog/oauth?client_id="+URLEncoder.encode(id,"UTF-8")+"&redirect_uri="+URLEncoder.encode(redirect,"UTF-8")+"&response_type=token&scope=pages_show_list%2Cpages_manage_posts%2Cpages_read_engagement";startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception e){Toast.makeText(this,"تعذر فتح تسجيل الدخول الرسمي",Toast.LENGTH_LONG).show();finish();}}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);if(i!=null&&i.getData()!=null)handle(i.getData());}
    void handle(Uri uri){if(uri==null||!uri.getScheme().equals("fb"+BuildConfig.FB_APP_ID)||!"authorize".equals(uri.getHost()))return;String token=uri.getFragment();if(token==null)token=uri.getEncodedQuery();if(token==null){Toast.makeText(this,"فيسبوك لم يرجع رمز الدخول",Toast.LENGTH_LONG).show();finish();return;}String access="";for(String part:token.split("&")){if(part.startsWith("access_token=")){access=Uri.decode(part.substring(13));break;}}if(access.isEmpty()){Toast.makeText(this,"تسجيل الدخول لم يكتمل أو لم تتم الموافقة على الصلاحيات",Toast.LENGTH_LONG).show();finish();return;}final String tok=access;new Thread(()->loadPages(tok)).start();}
    void loadPages(String token){try{String u="https://graph.facebook.com/v24.0/me/accounts?fields=id,name,access_token&limit=100&access_token="+URLEncoder.encode(token,"UTF-8");HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(20000);c.setReadTimeout(20000);BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));StringBuilder s=new StringBuilder();String line;while((line=r.readLine())!=null)s.append(line);c.disconnect();JSONArray a=new JSONObject(s.toString()).optJSONArray("data");if(a==null||a.length()==0)throw new Exception("الحساب مفيهوش صفحات متاحة أو صلاحيات الصفحات غير مفعلة");JSONArray out=new JSONArray();for(int i=0;i<a.length();i++){JSONObject p=a.getJSONObject(i);out.put(new JSONObject().put("id",p.optString("id")).put("name",p.optString("name")).put("token",p.optString("access_token")));}Intent result=new Intent();result.putExtra("pages",out.toString());setResult(RESULT_OK,result);runOnUiThread(this::finish);}catch(Exception e){runOnUiThread(()->{Toast.makeText(this,"تعذر جلب الصفحات: "+e.getMessage(),Toast.LENGTH_LONG).show();finish();});}}
}
