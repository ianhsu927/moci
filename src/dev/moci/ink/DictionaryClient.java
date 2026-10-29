package dev.moci.ink;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URL;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.io.*;
import java.util.ArrayList;

final class DictionaryClient {
    static final class NotFound extends IOException {
        NotFound() { super("未找到英文词条，请检查拼写或试试单词原形。"); }
    }
    static JSONObject lookup(String query) throws Exception {
        String dicts = "{\"count\":3,\"dicts\":[[\"ec\"],[\"blng_sents_part\"],[\"meta\"]]}";
        String address = "https://dict.youdao.com/jsonapi?doctype=json&jsonversion=2&q="
                + URLEncoder.encode(query, "UTF-8") + "&dicts=" + URLEncoder.encode(dicts, "UTF-8");
        HttpURLConnection connection = (HttpURLConnection)new URL(address).openConnection();
        connection.setConnectTimeout(10000); connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent", "MoCi/1.0 (Android; English-Chinese dictionary)");
        connection.setRequestProperty("Accept", "application/json");
        try {
            int code = connection.getResponseCode();
            if (code != 200) throw new IOException("词典服务暂时不可用（" + code + "），请稍后重试。");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                    if (bytes.size() + n > 2 * 1024 * 1024) throw new IOException("词条数据过大，请稍后重试。");
                    bytes.write(buffer, 0, n);
                }
            }
            return parse(new JSONObject(bytes.toString("UTF-8")), query);
        } finally { connection.disconnect(); }
    }
    static JSONObject parse(JSONObject response, String query) throws Exception {
        JSONObject ec = response.optJSONObject("ec");
        JSONArray words = ec == null ? null : ec.optJSONArray("word");
        if (words == null || words.length() == 0) throw new NotFound();
        JSONObject word = words.getJSONObject(0);
        JSONArray definitions = new JSONArray();
        JSONArray trs = word.optJSONArray("trs");
        if (trs != null) for (int i=0; i<trs.length() && definitions.length()<12; i++) {
            JSONArray tr = trs.optJSONObject(i) == null ? null : trs.optJSONObject(i).optJSONArray("tr");
            if (tr == null) continue;
            for (int k=0; k<tr.length() && definitions.length()<12; k++) {
                JSONObject item = tr.optJSONObject(k);
                JSONObject l = item == null ? null : item.optJSONObject("l");
                Object lines = l == null ? null : l.opt("i");
                if (lines instanceof JSONArray) {
                    JSONArray a = (JSONArray)lines;
                    for (int j=0; j<a.length() && definitions.length()<12; j++) addUnique(definitions, clean(a.optString(j)));
                } else if (lines instanceof String) addUnique(definitions, clean((String)lines));
            }
        }
        if (definitions.length() == 0) throw new NotFound();
        JSONObject result = new JSONObject();
        result.put("query", query);
        JSONObject phrase = word.optJSONObject("return-phrase");
        JSONObject phraseL = phrase == null ? null : phrase.optJSONObject("l");
        Object spelling = phraseL == null ? null : phraseL.opt("i");
        String head = spelling instanceof JSONArray ? ((JSONArray)spelling).optString(0,query)
                : spelling instanceof String ? (String)spelling : query;
        result.put("word", clean(head));
        result.put("uk", clean(word.optString("ukphone")));
        result.put("us", clean(word.optString("usphone")));
        result.put("definitions", definitions);
        result.put("savedAt", System.currentTimeMillis());
        JSONArray tags = ec.optJSONArray("exam_type");
        result.put("tags", tags == null ? new JSONArray() : tags);
        JSONArray forms = new JSONArray();
        JSONArray wfs = word.optJSONArray("wfs");
        if (wfs != null) for (int i=0; i<wfs.length() && forms.length()<8; i++) {
            JSONObject item=wfs.optJSONObject(i); JSONObject wf=item == null ? null : item.optJSONObject("wf");
            if (wf != null) forms.put(clean(wf.optString("name")) + "  " + clean(wf.optString("value")));
        }
        result.put("forms",forms);
        JSONArray examples = new JSONArray();
        JSONObject block=response.optJSONObject("blng_sents_part");
        JSONArray pairs=block == null ? null : block.optJSONArray("sentence-pair");
        if (pairs != null) for (int i=0; i<pairs.length() && examples.length()<2; i++) {
            JSONObject pair=pairs.optJSONObject(i); if (pair == null) continue;
            String en=clean(pair.optString("sentence",pair.optString("sentence-eng")));
            String zh=clean(pair.optString("sentence-translation"));
            if (!en.isEmpty() && !zh.isEmpty() && en.length()<400 && zh.length()<300) {
                JSONObject ex=new JSONObject();ex.put("en",en);ex.put("zh",zh);examples.put(ex);
            }
        }
        result.put("examples", examples);
        return result;
    }
    private static void addUnique(JSONArray a, String value) {
        if (value.isEmpty()) return;
        for (int i=0;i<a.length();i++) if (value.equals(a.optString(i))) return;
        a.put(value);
    }
    private static String clean(String text) {
        return android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }
}
