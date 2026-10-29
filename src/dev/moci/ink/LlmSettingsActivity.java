package dev.moci.ink;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import android.content.*;

public class LlmSettingsActivity extends Activity {
    private EditText endpoint,model,key;
    private LinearLayout layout;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.WHITE);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(dp(20),dp(18),dp(20),dp(20));scroll.addView(layout);setContentView(scroll);
        if(Build.VERSION.SDK_INT>=30)scroll.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());
            scroll.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        label("AI 服务设置",26);
        endpoint=field("服务地址","https://你的服务地址/v1",false);
        model=field("模型名称","填写服务商提供的模型 ID",false);
        key=field("API Key","无鉴权服务可留空",true);
        endpoint.setText(LlmConfig.prefs(this).getString("endpoint",""));model.setText(LlmConfig.prefs(this).getString("model",""));
        try{key.setText(LlmConfig.read(this).key);}catch(Exception e){label("原密钥无法读取，请重新填写后保存。",15);}
        Button save=new Button(this);save.setText("保存并返回");save.setTextSize(18);save.setOnClickListener(v->{
            try{LlmConfig.save(this,endpoint.getText().toString(),model.getText().toString(),key.getText().toString());setResult(RESULT_OK);finish();}
            catch(IllegalArgumentException e){new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("确定",null).show();}
            catch(Exception e){new AlertDialog.Builder(this).setMessage("无法安全保存密钥或配置，请重新尝试。").setPositiveButton("确定",null).show();}
        });layout.addView(save,new LinearLayout.LayoutParams(-1,dp(54)));
        Button cancel=new Button(this);cancel.setText("取消");cancel.setOnClickListener(v->finish());layout.addView(cancel,new LinearLayout.LayoutParams(-1,dp(48)));
        layout.setFocusableInTouchMode(true);layout.requestFocus();
    }
    private void label(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.BLACK);t.setPadding(0,dp(12),0,dp(10));layout.addView(t);}
    private EditText field(String title,String hint,boolean secret){
        label(title,18);EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(Color.BLACK);e.setTextSize(17);e.setHint(hint);e.setContentDescription(title);
        e.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_URI)|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if(secret){e.setSaveEnabled(false);e.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);}
        layout.addView(e,new LinearLayout.LayoutParams(-1,dp(56)));return e;
    }
}
