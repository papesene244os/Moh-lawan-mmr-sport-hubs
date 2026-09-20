package com.mohlawanmmr.sportshub;

import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new NewsBridge(), "AndroidNews");
        webView.loadUrl("file:///android_asset/index.html");
    }

    public class NewsBridge {
        @JavascriptInterface
        public void refreshNews() {
            new Thread(() -> {
                try {
                    JSONArray all = new JSONArray();
                    String[] queries = {
                        "Nigeria football Hausa",
                        "Africa football Hausa",
                        "Nigeria Super Eagles football",
                        "CAF football Africa"
                    };

                    for (String q : queries) {
                        String url = "https://news.google.com/rss/search?q="
                                + URLEncoder.encode(q, "UTF-8")
                                + "&hl=ha&gl=NG&ceid=NG:ha";
                        all = merge(all, fetchRss(url, q));
                    }

                    final String result = all.toString();
                    runOnUiThread(() ->
                        webView.evaluateJavascript(
                            "window.receiveLiveNews(" + JSONObject.quote(result) + ")", null
                        )
                    );
                } catch (Exception e) {
                    runOnUiThread(() ->
                        webView.evaluateJavascript(
                            "window.receiveLiveNews('[]')", null
                        )
                    );
                }
            }).start();
        }

        private JSONArray merge(JSONArray a, JSONArray b) {
            JSONArray out = new JSONArray();
            try {
                for (int i = 0; i < a.length(); i++) out.put(a.get(i));
                for (int i = 0; i < b.length(); i++) out.put(b.get(i));
            } catch (Exception ignored) {}
            return out;
        }

        private JSONArray fetchRss(String urlString, String category) {
            JSONArray result = new JSONArray();
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "MOH-LAWAN-MMR-SPORTS-HUB/1.0");

                InputStream in = conn.getInputStream();
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));

                String title = null, link = null, pubDate = null, description = null;
                boolean inItem = false;

                int event;
                while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        String name = parser.getName();
                        if ("item".equalsIgnoreCase(name)) {
                            inItem = true;
                            title = link = pubDate = description = null;
                        } else if (inItem && ("title".equalsIgnoreCase(name) ||
                                "link".equalsIgnoreCase(name) ||
                                "pubDate".equalsIgnoreCase(name) ||
                                "description".equalsIgnoreCase(name))) {
                            String text = parser.nextText();
                            if ("title".equalsIgnoreCase(name)) title = text;
                            else if ("link".equalsIgnoreCase(name)) link = text;
                            else if ("pubDate".equalsIgnoreCase(name)) pubDate = text;
                            else description = text;
                        }
                    } else if (event == XmlPullParser.END_TAG && "item".equalsIgnoreCase(parser.getName())) {
                        if (inItem && title != null && link != null) {
                            // Keep the app focused on sports and skip obvious betting/promotional items.
                            String low = (title + " " + (description == null ? "" : description)).toLowerCase();
                            if (!low.contains("betting") && !low.contains("odds") &&
                                !low.contains("bet") && !low.contains("casino") &&
                                !low.contains("stake")) {
                                JSONObject item = new JSONObject();
                                item.put("title", html.unescapeHtml(title).replaceAll("<[^>]*>", ""));
                                item.put("link", link);
                                item.put("date", pubDate == null ? "" : pubDate);
                                item.put("description", description == null ? "" :
                                        html.unescapeHtml(description).replaceAll("<[^>]*>", ""));
                                item.put("category", category);
                                result.put(item);
                            }
                        }
                        inItem = false;
                    }
                }
                in.close();
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            return result;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
