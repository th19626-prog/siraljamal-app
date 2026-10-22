package com.siraljamal.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    static final int BRAND = Color.parseColor("#1F3A5F");
    static final String PREFS = "siraljamal";
    static final String DEFAULT_PAGES = "["
        + "{\"name\":\"أعمار الموردين\",\"url\":\"https://claude.ai/artifact/9u2tW218N3yiYZ8WWhKGmM\"},"
        + "{\"name\":\"مشتريات المارد\",\"url\":\"https://claude.ai/artifact/8kak2EH8VNT4o2VP9jpmCa\"}"
        + "]";

    final List<String[]> pages = new ArrayList<>();
    int current = 0;
    WebView web;
    SwipeRefreshLayout swipe;
    ProgressBar progress;
    TextView banner;
    LinearLayout tabs;
    ConnectivityManager cm;
    ConnectivityManager.NetworkCallback netCb;

    int dp(int v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        loadPages();
        current = Math.min(getSharedPreferences(PREFS, MODE_PRIVATE).getInt("current", 0), pages.size() - 1);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // header
        LinearLayout header = new LinearLayout(this);
        header.setBackgroundColor(BRAND);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(10), dp(8), dp(10));
        TextView title = new TextView(this);
        title.setText("سر الجمال");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(headerButton("⟳", v -> web.reload()));
        header.addView(headerButton("⚙", v -> managePages()));
        root.addView(header);

        // tabs
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setBackgroundColor(Color.parseColor("#F2F5F9"));
        tabs = new LinearLayout(this);
        tabs.setPadding(dp(8), dp(6), dp(8), dp(6));
        hs.addView(tabs);
        root.addView(hs);

        banner = new TextView(this);
        banner.setText("لا يوجد اتصال بالإنترنت – ستتحدث الصفحة تلقائياً عند عودة الاتصال");
        banner.setBackgroundColor(Color.parseColor("#B3261E"));
        banner.setTextColor(Color.WHITE);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(dp(8), dp(6), dp(8), dp(6));
        banner.setVisibility(View.GONE);
        root.addView(banner);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(false);
        progress.setMax(100);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        swipe = new SwipeRefreshLayout(this);
        swipe.setColorSchemeColors(BRAND);
        web = new WebView(this);
        setupWeb();
        swipe.addView(web, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        swipe.setOnRefreshListener(() -> web.reload());
        swipe.setOnChildScrollUpCallback((parent, child) -> web.getScrollY() > 0);
        root.addView(swipe, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        buildTabs();
        watchNetwork();
        if (b != null) web.restoreState(b); else openCurrent();
    }

    Button headerButton(String t, View.OnClickListener l) {
        Button btn = new Button(this, null, android.R.attr.borderlessButtonStyle);
        btn.setText(t);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(20);
        btn.setMinWidth(dp(44));
        btn.setOnClickListener(l);
        return btn;
    }

    void setupWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.INVISIBLE : View.VISIBLE);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String host = u.getHost() == null ? "" : u.getHost();
                if (host.endsWith("claude.ai") || host.endsWith("anthropic.com") || host.endsWith("claudeusercontent.com")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
            @Override public void onPageFinished(WebView v, String url) {
                swipe.setRefreshing(false);
                CookieManager.getInstance().flush();
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) { swipe.setRefreshing(false); banner.setVisibility(View.VISIBLE); }
            }
        });
    }

    void buildTabs() {
        tabs.removeAllViews();
        for (int i = 0; i < pages.size(); i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(pages.get(i)[0]);
            t.setTextSize(15);
            t.setPadding(dp(16), dp(8), dp(16), dp(8));
            boolean on = i == current;
            t.setTextColor(on ? Color.WHITE : BRAND);
            t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(dp(18));
            bg.setColor(on ? BRAND : Color.WHITE);
            bg.setStroke(dp(1), BRAND);
            t.setBackground(bg);
            t.setOnClickListener(v -> { current = idx; save(); buildTabs(); openCurrent(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            tabs.addView(t, lp);
        }
    }

    void openCurrent() {
        if (pages.isEmpty()) return;
        web.loadUrl(pages.get(current)[1]);
    }

    void watchNetwork() {
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        netCb = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) {
                runOnUiThread(() -> { if (banner.getVisibility() == View.VISIBLE) { banner.setVisibility(View.GONE); web.reload(); } });
            }
            @Override public void onLost(Network n) { runOnUiThread(() -> banner.setVisibility(online() ? View.GONE : View.VISIBLE)); }
        };
        cm.registerDefaultNetworkCallback(netCb);
        banner.setVisibility(online() ? View.GONE : View.VISIBLE);
    }

    boolean online() {
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities c = cm.getNetworkCapabilities(n);
        return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @Override protected void onResume() {
        super.onResume();
        // refresh when coming back to the app so numbers are always current
        if (web != null && web.getUrl() != null && online()) web.reload();
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }

    @Override protected void onDestroy() { if (cm != null && netCb != null) cm.unregisterNetworkCallback(netCb); super.onDestroy(); }

    /* ---------- pages ---------- */
    void loadPages() {
        pages.clear();
        String json = getSharedPreferences(PREFS, MODE_PRIVATE).getString("pages", DEFAULT_PAGES);
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                pages.add(new String[]{o.getString("name"), o.getString("url")});
            }
        } catch (Exception e) { /* fall through */ }
        if (pages.isEmpty()) { getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove("pages").apply(); loadPages(); }
    }

    void save() {
        JSONArray a = new JSONArray();
        try { for (String[] p : pages) a.put(new JSONObject().put("name", p[0]).put("url", p[1])); } catch (Exception ignored) {}
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("pages", a.toString()).putInt("current", current).apply();
    }

    void managePages() {
        String[] items = new String[pages.size() + 1];
        for (int i = 0; i < pages.size(); i++) items[i] = "حذف: " + pages.get(i)[0];
        items[pages.size()] = "➕ إضافة صفحة جديدة";
        new AlertDialog.Builder(this)
            .setTitle("الصفحات")
            .setItems(items, (d, which) -> {
                if (which == pages.size()) addPage();
                else confirmDelete(which);
            })
            .setNegativeButton("إغلاق", null)
            .show();
    }

    void confirmDelete(int i) {
        if (pages.size() <= 1) return;
        new AlertDialog.Builder(this)
            .setMessage("حذف «" + pages.get(i)[0] + "» من التطبيق؟")
            .setPositiveButton("حذف", (d, w) -> { pages.remove(i); current = 0; save(); buildTabs(); openCurrent(); })
            .setNegativeButton("إلغاء", null).show();
    }

    void addPage() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(20), dp(8), dp(20), 0);
        EditText name = new EditText(this); name.setHint("اسم الصفحة");
        EditText url = new EditText(this); url.setHint("الرابط https://claude.ai/artifact/…");
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        f.addView(name); f.addView(url);
        new AlertDialog.Builder(this)
            .setTitle("إضافة صفحة")
            .setView(f)
            .setPositiveButton("إضافة", (d, w) -> {
                String n = name.getText().toString().trim(), u = url.getText().toString().trim();
                if (n.isEmpty() || !u.startsWith("https://")) return;
                pages.add(new String[]{n, u});
                current = pages.size() - 1; save(); buildTabs(); openCurrent();
            })
            .setNegativeButton("إلغاء", null).show();
    }
}
