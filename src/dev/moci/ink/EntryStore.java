package dev.moci.ink;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

final class EntryStore {
    final SharedPreferences preferences;
    private final File directory;
    EntryStore(Context context) {
        preferences=context.getSharedPreferences("moci",Context.MODE_PRIVATE);
        directory=new File(context.getFilesDir(),"entries");directory.mkdirs();
    }
    static String key(String word) { return word.trim().toLowerCase(Locale.ROOT); }
    private File file(String word) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(key(word).getBytes(StandardCharsets.UTF_8));
        StringBuilder name=new StringBuilder();for(byte b:hash)name.append(String.format(Locale.ROOT,"%02x",b & 255));
        return new File(directory,name+".json");
    }
    JSONObject read(String word) {
        try (InputStream in=new FileInputStream(file(word))) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1) {if(out.size()+n>200000)return null;out.write(b,0,n);}
            return new JSONObject(out.toString("UTF-8"));
        } catch(Exception ignored) {return null;}
    }
    synchronized void write(String word,JSONObject entry) throws Exception {
        File target=file(word),temp=new File(target.getPath()+".tmp");
        try(OutputStream out=new FileOutputStream(temp)){out.write(entry.toString().getBytes(StandardCharsets.UTF_8));}
        if(!temp.renameTo(target))throw new IOException("无法保存查询缓存");
    }
    JSONArray list(String name) {
        try{return new JSONArray(preferences.getString(name,"[]"));}catch(Exception e){return new JSONArray();}
    }
    void add(String name,String word,int limit) {
        String value=key(word);JSONArray old=list(name),next=new JSONArray();next.put(value);
        for(int i=0;i<old.length() && next.length()<limit;i++) if(!value.equals(old.optString(i)))next.put(old.optString(i));
        preferences.edit().putString(name,next.toString()).apply();
    }
    boolean contains(String name,String word) {
        JSONArray a=list(name);String value=key(word);
        for(int i=0;i<a.length();i++)if(value.equals(a.optString(i)))return true;
        return false;
    }
    void remove(String name,String word) {
        JSONArray a=list(name),next=new JSONArray();String value=key(word);
        for(int i=0;i<a.length();i++)if(!value.equals(a.optString(i)))next.put(a.optString(i));
        preferences.edit().putString(name,next.toString()).apply();
    }
}
