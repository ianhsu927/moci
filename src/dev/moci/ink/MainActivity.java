package dev.moci.ink;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final ExecutorService worker=Executors.newFixedThreadPool(2);
    private EntryStore store;
    private LinearLayout root,content,tabs;
    private ScrollView scroll;
    private LinearLayout pageBar;
    private Button previousPage,nextPage;
    private TextView pageIndicator;
    private EditText input;
    private TextView sourceLabel;
    private Button lookupButton;
    private JSONObject entry,aiEntry;
    private String aiQuery="",aiError="";
    private boolean aiCached=false,aiLoading=false;
    private int aiGeneration=0,aiConfigRevision=0;
    private Future<?> aiRequest;
    private String entryQuery="", tab="lookup", error="";
    private boolean cached=false,loading=false;
    private int bodySize=21, generation=0;
    private int capturedVolumeKeys=0;
    private long recommendationDay=Long.MIN_VALUE;
    private Future<?> request;
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
        if(stroke>0)d.setStroke(dp(1),stroke);return d;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new EntryStore(this);
        bodySize=store.preferences.getInt("fontSize",21);
        aiConfigRevision=LlmConfig.revision(this);
        if(state!=null){
            tab=state.getString("tab","lookup");entryQuery=state.getString("entryQuery","");
            cached=state.getBoolean("cached");error=state.getString("error","");
            aiQuery=state.getString("aiQuery","");aiError=state.getString("aiError","");aiCached=state.getBoolean("aiCached");
            try{String e=state.getString("aiEntry");if(e!=null)aiEntry=new JSONObject(e);}catch(Exception ignored){}
            try{String e=state.getString("entry");if(e!=null)entry=new JSONObject(e);}catch(Exception ignored){}
        }
        build();
        if(state!=null)input.setText(state.getString("input",""));
        render();
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, ()->{
                    if(!"lookup".equals(tab)){tab="lookup";render();}else finish();
                });
        if(state==null)handleIntent(getIntent());
        else if(state.getBoolean("loading")&&entry==null)lookup(input.getText().toString(),false);
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleIntent(intent);}
    private void handleIntent(Intent intent){
        if(intent==null)return;
        String text=null;
        if(Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())){
            CharSequence s=intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);if(s!=null)text=s.toString();
        }else if(Intent.ACTION_SEND.equals(intent.getAction())){
            text=intent.getStringExtra(Intent.EXTRA_TEXT);
        }else text=intent.getStringExtra("query");
        if(text!=null){input.setText(text.trim());lookup(text,false);}
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putString("tab",tab);state.putString("input",input.getText().toString());
        state.putString("entryQuery",entryQuery);state.putBoolean("cached",cached);
        state.putBoolean("loading",loading);state.putString("error",error);
        state.putString("aiQuery",aiQuery);state.putString("aiError",aiError);state.putBoolean("aiCached",aiCached);
        if(aiEntry!=null)state.putString("aiEntry",aiEntry.toString());
        if(entry!=null)state.putString("entry",entry.toString());super.onSaveInstanceState(state);
    }
    @Override protected void onResume(){
        super.onResume();if(store==null)return;int revision=LlmConfig.revision(this);
        if(revision!=aiConfigRevision){aiConfigRevision=revision;aiGeneration++;if(aiRequest!=null)aiRequest.cancel(true);
            aiEntry=null;aiError="";aiLoading=false;aiCached=false;render();}
        if("lookup".equals(tab)&&entry==null&&!loading&&error.isEmpty()&&recommendationDay!=DailyWords.today())render();
    }
    @Override protected void onDestroy(){generation++;aiGeneration++;if(request!=null)request.cancel(true);if(aiRequest!=null)aiRequest.cancel(true);worker.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){
        if(!"lookup".equals(tab)){tab="lookup";render();}else super.onBackPressed();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int code=event.getKeyCode();
        int key=code==KeyEvent.KEYCODE_VOLUME_UP?1:code==KeyEvent.KEYCODE_VOLUME_DOWN?2:0;
        if(key!=0){
            if(event.getAction()==KeyEvent.ACTION_UP && (capturedVolumeKeys&key)!=0){
                capturedVolumeKeys&=~key;return true;
            }
            if(event.getAction()==KeyEvent.ACTION_DOWN &&
                    ((capturedVolumeKeys&key)!=0 || (pageBar!=null && pageBar.getVisibility()==View.VISIBLE))){
                capturedVolumeKeys|=key;
                if(event.getRepeatCount()==0)turnPage(code==KeyEvent.KEYCODE_VOLUME_UP?-1:1);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    private void build(){
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.WHITE);root.setClipToPadding(false);
        root.setPadding(dp(20),dp(6),dp(20),dp(6));setContentView(root);
        if(Build.VERSION.SDK_INT>=30){
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
                root.setPadding(dp(20)+bars.left,bars.top+dp(6),dp(20)+bars.right,bars.bottom+dp(6));return insets;
            });root.requestApplyInsets();
        }
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("墨词",28,true);header.addView(brand,new LinearLayout.LayoutParams(0,dp(50),1));brand.setGravity(Gravity.CENTER_VERTICAL);
        Button font=button("设置",false,v->fontDialog());font.setContentDescription("阅读与AI服务设置");header.addView(font,new LinearLayout.LayoutParams(dp(66),dp(44)));root.addView(header);
        space(root,14);
        LinearLayout search=row();search.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout field=row();field.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable fieldBorder=background(Color.WHITE,0,4);fieldBorder.setStroke(dp(2),Color.BLACK);
        field.setBackground(fieldBorder);field.setPadding(dp(2),dp(2),dp(2),dp(2));
        input=new EditText(this);input.setSingleLine(true);input.setTextSize(20);input.setTextColor(Color.BLACK);input.setHintTextColor(0xff666666);input.setHint("输入英文单词");
        input.setBackgroundColor(Color.TRANSPARENT);input.setPadding(dp(12),dp(6),0,dp(6));
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH|EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setContentDescription("英文单词输入框");
        input.setOnEditorActionListener((v,id,event)->{
            if(id==EditorInfo.IME_ACTION_SEARCH || (event!=null&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){
                submitLookup();return true;
            }return false;
        });
        field.addView(input,new LinearLayout.LayoutParams(0,dp(52),1));
        Button clear=button("×",false,v->{input.setText("");input.requestFocus();});clear.setContentDescription("清空输入");
        clear.setBackgroundColor(Color.WHITE);field.addView(clear,new LinearLayout.LayoutParams(dp(44),dp(48)));
        search.addView(field,new LinearLayout.LayoutParams(0,dp(56),1));
        lookupButton=button("查询",true,v->submitLookup());
        LinearLayout.LayoutParams submit=new LinearLayout.LayoutParams(dp(74),dp(56));submit.leftMargin=dp(8);search.addView(lookupButton,submit);root.addView(search);
        space(root,14);
        line(root,Color.BLACK);scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setSmoothScrollingEnabled(false);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);scroll.setVerticalScrollBarEnabled(false);
        content=new LinearLayout(this);content.setOrientation(1);content.setPadding(0,dp(22),0,dp(24));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        pageBar=new LinearLayout(this);pageBar.setOrientation(1);line(pageBar,0xffbbbbbb);
        LinearLayout pageControls=row();pageControls.setGravity(Gravity.CENTER_VERTICAL);pageControls.setPadding(dp(8),0,dp(8),0);
        tabs=row();pageControls.addView(tabs,new LinearLayout.LayoutParams(-2,dp(52)));
        pageControls.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        pageIndicator=text("1/1",12,false);pageIndicator.setGravity(Gravity.CENTER);
        pageControls.addView(pageIndicator,new LinearLayout.LayoutParams(dp(38),dp(40)));
        LinearLayout capsule=row();capsule.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable capsuleBorder=background(Color.WHITE,0,24);capsuleBorder.setStroke(dp(1),0xffb5b5b5);
        capsule.setBackground(capsuleBorder);capsule.setPadding(dp(1),dp(1),dp(1),dp(1));
        previousPage=new BarButton(this,"up","上一页");previousPage.setOnClickListener(v->turnPage(-1));
        nextPage=new BarButton(this,"down","下一页");nextPage.setOnClickListener(v->turnPage(1));
        capsule.addView(previousPage,new LinearLayout.LayoutParams(0,-1,1));
        View separator=new View(this);separator.setBackgroundColor(0xffb5b5b5);capsule.addView(separator,new LinearLayout.LayoutParams(dp(1),-1));
        capsule.addView(nextPage,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout.LayoutParams capsuleLayout=new LinearLayout.LayoutParams(dp(112),dp(40));capsuleLayout.leftMargin=dp(6);
        pageControls.addView(capsule,capsuleLayout);
        pageBar.addView(pageControls);
        LinearLayout.LayoutParams barLayout=new LinearLayout.LayoutParams(-1,-2);barLayout.leftMargin=-dp(20);barLayout.rightMargin=-dp(20);barLayout.bottomMargin=-dp(6);
        root.addView(pageBar,barLayout);
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->updatePaging());
        scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updatePaging());
        content.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updatePaging());
        root.setFocusableInTouchMode(true);root.requestFocus();
    }
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);return l;}
    private TextView text(String value,int size,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.BLACK);
        t.setIncludeFontPadding(false);t.setLineSpacing(dp(3),1.13f);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private Button button(String value,boolean solid,View.OnClickListener listener){
        Button b=new Button(this);b.setText(value);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(solid?Color.WHITE:Color.BLACK);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(background(solid?0xff111111:Color.WHITE,solid?0:0xff222222,4));
        b.setStateListAnimator(null);b.setMinimumWidth(0);b.setMinWidth(0);b.setMinHeight(0);b.setMinimumHeight(dp(44));b.setPadding(dp(8),0,dp(8),0);b.setOnClickListener(listener);return b;
    }
    private void line(LinearLayout layout,int color){View v=new View(this);v.setBackgroundColor(color);layout.addView(v,new LinearLayout.LayoutParams(-1,dp(1)));}
    private void space(LinearLayout layout,int value){layout.addView(new View(this),new LinearLayout.LayoutParams(1,dp(value)));}
    private void body(String value,int size,boolean bold){TextView t=text(value,size,bold);t.setTextIsSelectable(true);content.addView(t);}
    private void section(String value){space(content,24);body(value,16,true);space(content,12);}
    private void renderTabs(){
        tabs.removeAllViews();String[] ids={"lookup","ai","saved","history"};String[] labels={"词典","AI查词","生词本","历史"};
        for(int i=0;i<ids.length;i++){
            final String id=ids[i];LinearLayout cell=new LinearLayout(this);cell.setOrientation(1);
            View mark=new View(this);mark.setBackgroundColor(id.equals(tab)?Color.BLACK:Color.WHITE);cell.addView(mark,new LinearLayout.LayoutParams(-1,dp(2)));
            Button icon=new BarButton(this,id,labels[i]);icon.setSelected(id.equals(tab));
            icon.setOnClickListener(v->{hideKeyboard();tab=id;render();});cell.addView(icon,new LinearLayout.LayoutParams(-1,0,1));
            tabs.addView(cell,new LinearLayout.LayoutParams(dp(44),-1));
        }
    }
    private void render(){
        pageBar.setVisibility(View.VISIBLE);
        scroll.post(this::updatePaging);
        renderTabs();content.removeAllViews();lookupButton.setText("ai".equals(tab)?(aiLoading?"生成中":"查询"):(loading?"查询中":"查询"));
        if("ai".equals(tab)){renderAi();scroll.scrollTo(0,0);return;}
        if(!"lookup".equals(tab)){renderList(tab);scroll.scrollTo(0,0);return;}
        if(loading&&entry==null){body("正在查询…",24,true);return;}
        if(!error.isEmpty()&&entry==null){
            body("暂时没有查到",24,true);space(content,14);body(error,bodySize,false);space(content,22);
            Button retry=button("重新查询",true,v->lookup(input.getText().toString(),true));content.addView(retry,new LinearLayout.LayoutParams(-1,dp(50)));return;
        }
        if(entry==null){renderWelcome();return;}
        if(!error.isEmpty()){body(error,16,false);space(content,18);}
        TextView head=text(entry.optString("word",entryQuery),34,true);head.setTypeface(Typeface.create("serif",Typeface.BOLD));head.setTextIsSelectable(true);content.addView(head);space(content,12);
        String uk=entry.optString("uk"),us=entry.optString("us");
        if(!uk.isEmpty()||!us.isEmpty()){
            String phonetic=(!uk.isEmpty()?"英 /"+uk+"/":"")+(!uk.isEmpty()&&!us.isEmpty()?"\n":"")+(!us.isEmpty()?"美 /"+us+"/":"");
            body(phonetic,17,false);space(content,14);
        }
        LinearLayout actions=row();Button favorite=button(store.contains("saved",entryQuery)?"已加入生词本":"加入生词本",false,v->{
            if(store.contains("saved",entryQuery))store.remove("saved",entryQuery);else store.add("saved",entryQuery,Integer.MAX_VALUE);
            ((Button)v).setText(store.contains("saved",entryQuery)?"已加入生词本":"加入生词本");renderTabs();
        });actions.addView(favorite,new LinearLayout.LayoutParams(0,dp(46),1));
        Button update=button("联网更新",false,v->lookup(entryQuery,true));LinearLayout.LayoutParams refresh=new LinearLayout.LayoutParams(dp(100),dp(46));refresh.leftMargin=dp(8);actions.addView(update,refresh);content.addView(actions);
        space(content,14);sourceLabel=text((loading?"正在联网更新":cached?"已缓存 · "+date(entry.optLong("savedAt")):"在线查询 · 刚刚更新"),12,false);sourceLabel.setTextColor(0xff555555);content.addView(sourceLabel);
        JSONArray tags=entry.optJSONArray("tags");if(tags!=null&&tags.length()>0){space(content,10);body(join(tags,"  ·  "),13,true);}
        space(content,20);line(content,0xffbbbbbb);space(content,20);
        JSONArray defs=entry.optJSONArray("definitions");
        if(defs!=null)for(int i=0;i<defs.length();i++){body(defs.optString(i),bodySize,false);if(i<defs.length()-1)space(content,14);}
        JSONArray forms=entry.optJSONArray("forms");if(forms!=null&&forms.length()>0){section("词形变化");body(join(forms,"\n"),Math.max(18,bodySize-2),false);}
        JSONArray examples=entry.optJSONArray("examples");if(examples!=null&&examples.length()>0){
            section("双语例句");for(int i=0;i<examples.length();i++){
                JSONObject ex=examples.optJSONObject(i);if(ex==null)continue;
                body(ex.optString("en"),bodySize,false);space(content,8);body(ex.optString("zh"),Math.max(18,bodySize-2),false);
                if(i<examples.length()-1){space(content,18);line(content,0xffdddddd);space(content,18);}
            }
        }
        space(content,24);line(content,0xffbbbbbb);space(content,14);body("释义与例句来源：有道词典",12,false);
        scroll.scrollTo(0,0);
    }
    private int pageStep(){return Math.max(1,scroll.getHeight()-dp(24));}
    private int maxScroll(){return Math.max(0,content.getHeight()-scroll.getHeight());}
    private void turnPage(int direction){
        int target=Math.max(0,Math.min(maxScroll(),scroll.getScrollY()+direction*pageStep()));
        scroll.scrollTo(0,target);updatePaging();
    }
    private void updatePaging(){
        if(pageBar==null)return;
        int max=maxScroll(),position=scroll.getScrollY(),step=pageStep();
        int total=(int)Math.ceil(max/(double)step)+1;
        int current=Math.min(total,(int)Math.ceil(position/(double)step)+1);
        pageIndicator.setText(current+"/"+total);
        previousPage.setEnabled(position>0);nextPage.setEnabled(position<max);
        previousPage.setTextColor(position>0?Color.BLACK:0xff999999);
        nextPage.setTextColor(position<max?Color.WHITE:0xffaaaaaa);
    }
    private String date(long millis){return new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(millis));}
    private String join(JSONArray a,String delimiter){StringBuilder s=new StringBuilder();for(int i=0;i<a.length();i++){if(i>0)s.append(delimiter);s.append(a.optString(i));}return s.toString();}
    private void renderWelcome(){
        recommendationDay=DailyWords.today();
        body("今日推荐词汇",22,true);space(content,14);
        for(String[] word:DailyWords.forDay(recommendationDay)){
            LinearLayout item=new LinearLayout(this);item.setOrientation(1);item.setPadding(0,dp(14),0,dp(14));
            TextView title=text(word[0],25,true);title.setTypeface(Typeface.create("serif",Typeface.BOLD));item.addView(title);
            TextView meaning=text(word[1],17,false);meaning.setPadding(0,dp(6),0,0);item.addView(meaning);
            item.setContentDescription("查询 "+word[0]);item.setFocusable(true);
            item.setOnClickListener(v->{input.setText(word[0]);lookup(word[0],false);});
            content.addView(item,new LinearLayout.LayoutParams(-1,-2));line(content,0xffbbbbbb);
        }
        scroll.scrollTo(0,0);
    }
    private void renderList(String name){
        JSONArray words=store.list(name);String title="saved".equals(name)?"我的生词本":"最近查询";
        LinearLayout titleRow=row();TextView heading=text(title,25,true);titleRow.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView count=text(words.length()+" 词",14,false);count.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);titleRow.addView(count);content.addView(titleRow);space(content,18);
        if(words.length()==0){space(content,18);body("saved".equals(name)?"还没有收藏的单词。":"还没有查询记录。",bodySize,false);return;}
        for(int i=0;i<words.length();i++){
            String word=words.optString(i);JSONObject data=store.read(word);LinearLayout item=new LinearLayout(this);item.setOrientation(1);item.setPadding(0,dp(14),0,dp(14));
            TextView label=text(data==null?word:data.optString("word",word),23,true);label.setTypeface(Typeface.create("serif",Typeface.BOLD));item.addView(label);
            JSONArray defs=data==null?null:data.optJSONArray("definitions");if(defs!=null&&defs.length()>0){TextView preview=text(defs.optString(0),16,false);preview.setMaxLines(2);preview.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=dp(6);item.addView(preview,pp);}
            if(data!=null&&"llm".equals(data.optString("type"))){TextView origin=text("AI · "+data.optString("model"),12,false);origin.setPadding(0,dp(6),0,0);item.addView(origin);}
            item.setContentDescription("查看单词 "+(data==null?word:data.optString("word",word)));item.setOnClickListener(v->{
                if(data!=null&&"llm".equals(data.optString("type"))){
                    aiGeneration++;if(aiRequest!=null)aiRequest.cancel(true);aiEntry=data;aiQuery=data.optString("query",data.optString("word"));aiCached=true;aiLoading=false;aiError="";tab="ai";input.setText(aiQuery);render();
                }else{input.setText(word);lookup(word,false);}
            });content.addView(item);line(content,0xffcccccc);
        }
    }
    private void lookup(String raw,boolean force){
        String query=EntryStore.key(raw);
        if(query.isEmpty()){input.requestFocus();input.setError("请输入英文单词");return;}
        if(query.length()>80 || !query.matches("[a-z0-9][a-z0-9' .-]*") || !query.matches(".*[a-z].*")){
            input.setError("请输入不超过80个字符的英文单词或短语");return;
        }
        hideKeyboard();generation++;final int token=generation;if(request!=null)request.cancel(true);
        tab="lookup";error="";input.setError(null);JSONObject local=store.read(query);
        if(!force&&local!=null){entry=local;entryQuery=query;cached=true;loading=false;store.add("history",query,80);render();return;}
        entry=local;entryQuery=query;cached=local!=null;loading=true;render();
        request=worker.submit(()->{
            try{
                JSONObject result=DictionaryClient.lookup(query);
                try{store.write(query,result);}catch(Exception ignored){}
                runOnUiThread(()->{if(token!=generation||isFinishing()||isDestroyed())return;
                    entry=result;entryQuery=query;cached=false;loading=false;error="";store.add("history",query,80);render();});
            }catch(Exception e){
                String message=e instanceof DictionaryClient.NotFound?e.getMessage():e instanceof SocketTimeoutException?"连接超时，请稍后重试。":e instanceof UnknownHostException?"无法连接词典，请检查网络后重试。":"在线查询失败，请检查网络或稍后重试。";
                runOnUiThread(()->{if(token!=generation||isFinishing()||isDestroyed())return;loading=false;
                    error=(entry!=null?"更新失败，以下保留本机缓存。\n":"")+message;render();});
            }
        });
    }
    private void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);input.clearFocus();root.requestFocus();}
    private void submitLookup(){if("ai".equals(tab))lookupAi(input.getText().toString(),false);else lookup(input.getText().toString(),false);}
    private void openAiSettings(){startActivity(new Intent(this,LlmSettingsActivity.class));}
    private void lookupAi(String raw,boolean force){
        String query=EntryStore.key(raw);
        if(query.isEmpty()||query.length()>80||!query.matches("[a-z0-9][a-z0-9' .-]*")||!query.matches(".*[a-z].*")){
            input.setError("请输入不超过80个字符的英文单词或短语");return;
        }
        tab="ai";hideKeyboard();input.setError(null);
        if(!LlmConfig.configured(this)){render();return;}
        final LlmConfig config;
        try{config=LlmConfig.read(this);}catch(Exception e){aiError="无法读取已保存的密钥，请进入AI服务设置重新保存。";render();return;}
        aiGeneration++;final int token=aiGeneration;if(aiRequest!=null)aiRequest.cancel(true);
        String cacheKey=config.cacheKey(query);JSONObject local=store.read(cacheKey);
        aiError="";aiQuery=query;
        if(!force&&local!=null){aiEntry=local;aiCached=true;aiLoading=false;store.add("history",cacheKey,80);render();return;}
        aiEntry=local;aiCached=local!=null;aiLoading=true;render();
        aiRequest=worker.submit(()->{
            try{
                JSONObject result=LlmClient.lookup(query,config);
                try{store.write(cacheKey,result);}catch(Exception ignored){}
                runOnUiThread(()->{if(token!=aiGeneration||isFinishing()||isDestroyed())return;
                    aiEntry=result;aiLoading=false;aiCached=false;aiError="";store.add("history",cacheKey,80);render();});
            }catch(Exception e){
                String message=e instanceof SocketTimeoutException?"模型响应超时，请稍后重试或换用更快的模型。":
                    e instanceof UnknownHostException?"无法连接模型服务，请检查网络和服务地址。":
                    e.getMessage()!=null&&e.getMessage().matches(".*[\\u4e00-\\u9fff].*")?e.getMessage():"AI查询失败，请检查网络和服务设置。";
                runOnUiThread(()->{if(token!=aiGeneration||isFinishing()||isDestroyed())return;aiLoading=false;
                    aiError=(aiEntry!=null?"生成失败，保留上次结果。\n":"")+message;render();});
            }
        });
    }
    private void renderAi(){
        if(aiEntry==null&&!LlmConfig.configured(this)){
            Button configure=button("配置 AI 服务",true,v->openAiSettings());content.addView(configure,new LinearLayout.LayoutParams(-1,dp(52)));
            return;
        }
        if(!aiError.isEmpty()){body(aiError,18,false);space(content,16);
            Button settings=button("检查 AI 服务设置",false,v->openAiSettings());content.addView(settings,new LinearLayout.LayoutParams(-1,dp(48)));space(content,16);}
        if(aiEntry==null){
            if(aiLoading){body("正在生成释义…",25,true);}
            else if(aiError.isEmpty()){
                body("当前模型："+LlmConfig.prefs(this).getString("model",""),15,false);}
            else {Button retry=button("重新查询",true,v->lookupAi(input.getText().toString(),true));content.addView(retry,new LinearLayout.LayoutParams(-1,dp(50)));}
            return;
        }
        TextView head=text(aiEntry.optString("word",aiQuery),34,true);head.setTypeface(Typeface.create("serif",Typeface.BOLD));head.setTextIsSelectable(true);content.addView(head);space(content,14);
        body("AI 讲解 · "+aiEntry.optString("model"),14,true);space(content,8);
        body(aiLoading?"正在重新生成…":aiCached?"已缓存 · "+date(aiEntry.optLong("savedAt")):"刚刚生成",12,false);space(content,16);
        String key=aiEntry.optString("cacheKey");LinearLayout actions=row();
        Button save=button(store.contains("saved",key)?"已加入生词本":"加入生词本",false,v->{
            if(store.contains("saved",key))store.remove("saved",key);else store.add("saved",key,Integer.MAX_VALUE);
            ((Button)v).setText(store.contains("saved",key)?"已加入生词本":"加入生词本");
        });actions.addView(save,new LinearLayout.LayoutParams(0,dp(46),1));
        Button regenerate=button("重新生成",false,v->lookupAi(aiQuery,true));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(100),dp(46));p.leftMargin=dp(8);actions.addView(regenerate,p);content.addView(actions);
        space(content,18);line(content,0xffbbbbbb);space(content,20);
        if(aiEntry.optBoolean("incomplete")){body("输出可能未完成，可尝试重新生成或更换模型。",15,false);space(content,14);}
        JSONArray meanings=aiEntry.optJSONArray("definitions");if(meanings!=null)for(int i=0;i<meanings.length();i++){body(meanings.optString(i),bodySize,false);space(content,12);}
        String explanation=aiEntry.optString("explanation");if(!explanation.isEmpty())body(explanation,bodySize,false);
        String usage=aiEntry.optString("usage");if(!usage.isEmpty()){section("用法提示");body(usage,bodySize,false);}
        JSONArray examples=aiEntry.optJSONArray("examples");if(examples!=null&&examples.length()>0){section("模型例句");for(int i=0;i<examples.length();i++){
            JSONObject ex=examples.optJSONObject(i);if(ex==null)continue;body(ex.optString("en"),bodySize,false);space(content,8);body(ex.optString("zh"),Math.max(18,bodySize-2),false);space(content,16);
        }}
        String memory=aiEntry.optString("memoryTip");if(!memory.isEmpty()){section("助记联想");body(memory,bodySize,false);}
        String confusions=aiEntry.optString("confusions");if(!confusions.isEmpty()){section("易混辨析");body(confusions,bodySize,false);}
    }
    private void fontDialog(){
        String[] choices={"标准字号","大号字体","特大字体","AI 服务设置","关于墨词"};int[] sizes={19,23,27};
        new AlertDialog.Builder(this).setTitle("墨词设置").setItems(choices,(dialog,which)->{
            if(which==3){openAiSettings();return;}
            if(which==4){new AlertDialog.Builder(this).setTitle("墨词 1.2")
                .setMessage("为墨水屏设计的英汉查词工具。\n\n词典释义来自有道网页查询接口；AI查词使用你配置的OpenAI兼容服务。服务变化可能影响可用性，AI内容需要核对。\n\n只在提交查询时发送单词。缓存、历史和生词本保存在本机，API密钥由Android Keystore加密保存。\n\n无广告、无分析统计。支持文字选择与分享查词。")
                .setPositiveButton("知道了",null).show();return;}
            bodySize=sizes[which];store.preferences.edit().putInt("fontSize",bodySize).apply();render();
        }).setNegativeButton("取消",null).show();
    }
}
