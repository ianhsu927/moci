package dev.moci.ink;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.net.URI;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;

final class LlmConfig {
    final String endpoint,model,key;
    LlmConfig(String endpoint,String model,String key){this.endpoint=endpoint;this.model=model;this.key=key;}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("llm",Context.MODE_PRIVATE);}
    static int revision(Context c){return prefs(c).getInt("revision",0);}
    static boolean configured(Context c){return !prefs(c).getString("endpoint","").isEmpty()&&!prefs(c).getString("model","").isEmpty();}
    static String endpoint(String raw) throws Exception {
        String value=raw.trim();while(value.endsWith("/"))value=value.substring(0,value.length()-1);
        URI uri=new URI(value);
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getFragment()!=null)
            throw new IllegalArgumentException("请填写 HTTPS 地址，不包含用户名、查询参数或片段。");
        if(!value.endsWith("/chat/completions"))value+="/chat/completions";
        return value;
    }
    static LlmConfig read(Context c) throws Exception {
        SharedPreferences p=prefs(c);
        return new LlmConfig(p.getString("endpoint",""),p.getString("model",""),decrypt(p.getString("key","")));
    }
    static void save(Context c,String base,String model,String key) throws Exception {
        String address=endpoint(base),name=model.trim();
        if(name.isEmpty()||name.length()>160||name.contains("\n")||name.contains("\r"))throw new IllegalArgumentException("请填写有效的模型名称。");
        if(key.contains("\n")||key.contains("\r"))throw new IllegalArgumentException("API Key 中不能包含换行符。");
        String encrypted=encrypt(key.trim());SharedPreferences p=prefs(c);
        if(!p.edit().putString("endpoint",address).putString("model",name).putString("key",encrypted)
                .putInt("revision",p.getInt("revision",0)+1).commit())throw new Exception("配置未能保存，请重试。");
    }
    String cacheKey(String word){
        try{
            byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest((endpoint+"\n"+model).getBytes(StandardCharsets.UTF_8));
            StringBuilder id=new StringBuilder();for(byte b:hash)id.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            return "ai-v1:"+id+":"+EntryStore.key(word);
        }catch(Exception e){throw new IllegalStateException("无法生成缓存标识");}
    }
    private static javax.crypto.SecretKey secret() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        String alias="moci.llm.config";
        if(ks.containsAlias(alias))return ((KeyStore.SecretKeyEntry)ks.getEntry(alias,null)).getSecretKey();
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    static String encrypt(String plain) throws Exception {
        if(plain.isEmpty())return "";
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,secret());
        return Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
    }
    static String decrypt(String value) throws Exception {
        if(value.isEmpty())return "";
        String[] parts=value.split(":",2);if(parts.length!=2)throw new Exception("密钥保存格式无效");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,secret(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
}
