package com.aojiao.shiying;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(7, 17, 28), CARD = Color.rgb(16, 31, 45);
    private static final int TEXT = Color.rgb(234, 244, 248), MUTED = Color.rgb(143, 165, 183), ACCENT = Color.rgb(101, 227, 181);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService workers = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private EditText input;
    private TextView status, resultTitle, resultDetail;
    private LinearLayout resultCard, historyList;
    private Button parseButton, downloadButton, cancelButton;
    private ProgressBar progress;
    private VideoInfo video;
    private boolean parsing, receiverRegistered, destroyed;
    private String cloudKey = "";
    private String browserTargetId = "";
    private WebView browser;
    private AlertDialog browserDialog;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra("state");
            status.setText(intent.getStringExtra("message"));
            int value = intent.getIntExtra("progress", -1);
            progress.setIndeterminate(value < 0); progress.setProgress(Math.max(0, value));
            boolean finished = "done".equals(state) || "error".equals(state);
            if (finished) {
                cancelButton.setVisibility(View.GONE); progress.setVisibility(View.GONE);
                parseButton.setEnabled(true); downloadButton.setEnabled(video != null);
                refreshHistory();
            }
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        buildUi();
        if (state != null) input.setText(state.getString("input", ""));
        handleIntent(getIntent());
    }
    private int dp(float value) { return (int) (getResources().getDisplayMetrics().density * value + 0.5f); }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLineSpacing(dp(4), 1); return view;
    }
    private Button button(String value, boolean primary) {
        Button view = new Button(this); view.setText(value); view.setAllCaps(false); view.setTextSize(15);
        view.setTextColor(primary ? BG : TEXT); view.setBackgroundTintList(ColorStateList.valueOf(primary ? ACCENT : Color.rgb(31, 51, 67)));
        view.setMinHeight(dp(50)); return view;
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout view = column(); view.setPadding(dp(18), dp(18), dp(18), dp(18)); view.setBackground(background(CARD, 20));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(18); parent.addView(view, params); return view;
    }
    private void space(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root = column(); root.setPadding(dp(22), dp(24), dp(22), dp(24)); scroll.addView(root);
        // Android 15 edge-to-edge: keep controls outside system bars and the keyboard.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        TextView tag = text("SHIYING  /  VIDEO SAVER", 11, ACCENT, true); tag.setLetterSpacing(.12f); root.addView(tag);
        space(root, 14); root.addView(text("拾影", 38, TEXT, true));
        root.addView(text("把喜欢的视频，好好留下。", 15, MUTED, false));
        LinearLayout form = card(root);
        form.addView(text("01  粘贴分享链接", 17, TEXT, true)); space(form, 10);
        input = new EditText(this); input.setTextColor(TEXT); input.setHintTextColor(MUTED); input.setTextSize(14);
        input.setHint("粘贴抖音分享文本或 https://v.douyin.com/…"); input.setMinLines(3); input.setMaxLines(6);
        input.setGravity(Gravity.TOP); input.setPadding(dp(12), dp(14), dp(12), dp(14)); input.setBackground(background(BG, 12));
        form.addView(input, new LinearLayout.LayoutParams(-1, -2)); space(form, 12);
        LinearLayout actions = new LinearLayout(this);
        Button paste = button("粘贴", false); parseButton = button("解析视频", true);
        actions.addView(paste, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams parseParams = new LinearLayout.LayoutParams(0, -2, 2); parseParams.leftMargin = dp(8);
        actions.addView(parseButton, parseParams); form.addView(actions);
        paste.setOnClickListener(v -> paste()); parseButton.setOnClickListener(v -> parse(false));
        resultCard = card(root); resultCard.setVisibility(View.GONE);
        resultCard.addView(text("02  无水印播放资源", 17, ACCENT, true)); space(resultCard, 10);
        resultTitle = text("", 16, TEXT, true); resultCard.addView(resultTitle);
        resultDetail = text("", 13, MUTED, false); resultCard.addView(resultDetail); space(resultCard, 12);
        downloadButton = button("保存视频到相册", true); resultCard.addView(downloadButton);
        downloadButton.setOnClickListener(v -> startDownload());
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        progress.setVisibility(View.GONE); LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(4)); progressParams.topMargin = dp(20); root.addView(progress, progressParams);
        status = text("直接解析优先使用本机网络；不需要注册。", 13, MUTED, false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2); statusParams.topMargin = dp(16); root.addView(status, statusParams);
        cancelButton = button("取消下载", false); cancelButton.setVisibility(View.GONE); root.addView(cancelButton);
        cancelButton.setOnClickListener(v -> startService(new Intent(this, DownloadService.class).setAction(DownloadService.CANCEL)));
        LinearLayout tools = new LinearLayout(this); tools.setGravity(Gravity.CENTER);
        Button web = button("内置网页解析", false), cloud = button("云端解析设置", false);
        tools.addView(web, new LinearLayout.LayoutParams(0, -2, 1)); tools.addView(cloud, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams toolParams = new LinearLayout.LayoutParams(-1, -2); toolParams.topMargin = dp(20); root.addView(tools, toolParams);
        web.setOnClickListener(v -> openBrowser()); cloud.setOnClickListener(v -> cloudDialog());
        LinearLayout history = card(root); history.addView(text("最近保存", 17, TEXT, true)); space(history, 8);
        historyList = column(); history.addView(historyList);
        space(root, 24); root.addView(text("公开播放资源 · 原文件保存 · 无额外水印\n作者已烘焙进画面中的文字或水印不会被抹除。\n支持 Android 10 及以上。", 12, MUTED, false));
    }
    private void paste() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) { status.setText("剪贴板为空，先在抖音复制链接"); return; }
        input.setText(clip.getItemAt(0).coerceToText(this));
    }
    private void parse(boolean cloud) {
        if (parsing || DownloadService.running) { status.setText("当前任务完成后再解析新链接"); return; }
        final String share;
        try { share = LinkTools.extract(input.getText().toString()); } catch (Exception e) { status.setText(e.getMessage()); return; }
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
        parsing = true; video = null; resultCard.setVisibility(View.GONE); parseButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE); progress.setIndeterminate(true);
        status.setText(cloud ? "正在发送公开链接到 Firecrawl 解析…" : "正在解析分享链接和播放资源…");
        int request = generation.incrementAndGet();
        workers.execute(() -> {
            try {
                DouyinResolver resolver = new DouyinResolver(url -> CookieManager.getInstance().getCookie(url));
                VideoInfo info = cloud ? resolver.cloudResolve(share, cloudKey) : resolver.resolve(share);
                main.post(() -> { if (!destroyed && request == generation.get()) { finishParse(); showVideo(info); } });
            } catch (Exception e) {
                main.post(() -> {
                    if (destroyed || request != generation.get()) return;
                    finishParse(); status.setText(e.getMessage() == null ? "解析失败，请使用内置网页" : e.getMessage());
                    if (!cloud) openBrowser();
                });
            }
        });
    }
    private void finishParse() { parsing = false; parseButton.setEnabled(true); progress.setVisibility(View.GONE); }
    private void showVideo(VideoInfo info) {
        video = info; resultTitle.setText(info.title.isEmpty() ? "抖音视频" : info.title);
        resultDetail.setText((info.author.isEmpty() ? "" : "作者：" + info.author + "\n") + "视频 ID：" + info.id + "\n可用播放地址：" + info.urls.size());
        resultCard.setVisibility(View.VISIBLE); downloadButton.setEnabled(!DownloadService.running);
        status.setText("已解析播放资源，点击保存。地址可能过期，失效时重新解析。");
    }
    private void startDownload() {
        if (video == null || DownloadService.running) return;
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        try {
            Intent intent = new Intent(this, DownloadService.class).putExtra("video", video.toJson().toString());
            startForegroundService(intent); downloadButton.setEnabled(false); parseButton.setEnabled(false);
            progress.setVisibility(View.VISIBLE); progress.setIndeterminate(true); cancelButton.setVisibility(View.VISIBLE);
            status.setText("开始下载；切到后台可从通知栏查看进度。");
        } catch (Exception e) { status.setText("无法启动下载：" + e.getMessage()); }
    }
    private void openBrowser() {
        final String share;
        try { share = LinkTools.extract(input.getText().toString()); } catch (Exception e) { status.setText(e.getMessage()); return; }
        if (browserDialog != null && browserDialog.isShowing()) return;
        browserTargetId = LinkTools.videoId(share);
        LinearLayout panel = column();
        TextView note = text("完成验证或播放视频后，点底部“读取当前页面”。", 13, MUTED, false); note.setPadding(dp(14), dp(12), dp(14), dp(12)); panel.addView(note);
        browser = new WebView(this); browser.setBackgroundColor(BG);
        WebSettings settings = browser.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false); settings.setUserAgentString(HttpTransport.DESKTOP_UA);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false);
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !LinkTools.isShareUrl(request.getUrl().toString());
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!LinkTools.isShareUrl(url)) { view.stopLoading(); return; }
                if (browserTargetId.isEmpty()) browserTargetId = LinkTools.videoId(url);
                note.setText("网页已加载，正在自动读取资源。若出现验证，请完成验证后继续。");
                pollBrowser(share, note, view, 0);
            }
        });
        panel.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        browserDialog = new AlertDialog.Builder(this).setTitle("抖音网页 · 本机会话").setView(panel)
            .setPositiveButton("读取当前页面", null).setNeutralButton("清除网页会话", null).setNegativeButton("关闭", null).create();
        browserDialog.setOnDismissListener(d -> {
            if (browser != null) { browser.stopLoading(); browser.destroy(); browser = null; }
        });
        browserDialog.show(); browserDialog.getWindow().setLayout(-1, (int) (getResources().getDisplayMetrics().heightPixels * .88));
        browserDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> inspectBrowser(share, note));
        browserDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            CookieManager.getInstance().removeAllCookies(success -> { CookieManager.getInstance().flush(); if (browser != null) browser.reload(); });
        });
        String id = LinkTools.videoId(share);
        browser.loadUrl(id.isEmpty() ? share : "https://www.douyin.com/video/" + id);
    }
    private void pollBrowser(String share, TextView note, WebView expected, int attempt) {
        if (destroyed || browser != expected || browserDialog == null || !browserDialog.isShowing() || attempt >= 20) return;
        main.postDelayed(() -> {
            if (destroyed || browser != expected || browserDialog == null || !browserDialog.isShowing()) return;
            inspectBrowser(share, note);
            pollBrowser(share, note, expected, attempt + 1);
        }, 2000);
    }
    private void inspectBrowser(String share, TextView note) {
        WebView current = browser;
        if (current == null || !LinkTools.isShareUrl(current.getUrl())) { note.setText("尚未加载有效的抖音页面"); return; }
        try {
            String script;
            try (InputStream in = getAssets().open("inspect-page.js")) { script = HttpTransport.readText(in, 1024 * 1024); }
            current.evaluateJavascript(script, encoded -> {
                try {
                    Object raw = new JSONTokener(encoded).nextValue();
                    if (!(raw instanceof String)) throw new Exception("页面尚未就绪");
                    VideoInfo found = PageParser.fromBrowserSnapshot((String) raw, browserTargetId);
                    if (found == null) { note.setText("暂未发现资源。请播放原视频几秒后重试；如果网页要求登录或验证，先完成它。"); return; }
                    found.source = share; finishParse(); showVideo(found); browserDialog.dismiss();
                } catch (Exception e) { note.setText("读取失败：" + e.getMessage()); }
            });
        } catch (Exception e) { note.setText("无法读取网页：" + e.getMessage()); }
    }
    private void cloudDialog() {
        EditText key = new EditText(this); key.setText(cloudKey); key.setHint("可选：Firecrawl API Key");
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout panel = column(); panel.setPadding(dp(20), dp(12), dp(20), dp(12));
        panel.addView(text("这是可选备用方案。点击云端解析会把公开分享链接发送给 Firecrawl；网页登录 Cookie 不会发送。Key 只保留在当前应用会话，不写进 APK 或文件。", 13, MUTED, false)); panel.addView(key);
        new AlertDialog.Builder(this).setTitle("云端解析 · 可选").setView(panel)
            .setPositiveButton("保存并解析", (d, w) -> { cloudKey = key.getText().toString().trim(); parse(true); })
            .setNeutralButton("仅保存本次 Key", (d, w) -> { cloudKey = key.getText().toString().trim(); })
            .setNegativeButton("取消", null).show();
    }
    private void refreshHistory() {
        historyList.removeAllViews();
        try {
            JSONArray array = new JSONArray(getSharedPreferences("downloads", MODE_PRIVATE).getString("history", "[]"));
            if (array.length() == 0) { historyList.addView(text("保存成功的视频会显示在这里。", 13, MUTED, false)); return; }
            for (int i = 0; i < Math.min(10, array.length()); i++) {
                JSONObject item = array.getJSONObject(i); Uri uri = Uri.parse(item.getString("uri"));
                TextView title = text(item.optString("title", "抖音视频"), 14, TEXT, true); title.setMaxLines(2); historyList.addView(title);
                LinearLayout row = new LinearLayout(this);
                Button play = button("播放", false), share = button("分享文件", false);
                row.addView(play, new LinearLayout.LayoutParams(0, -2, 1)); row.addView(share, new LinearLayout.LayoutParams(0, -2, 1)); historyList.addView(row); space(historyList, 12);
                play.setOnClickListener(v -> mediaIntent(uri, false)); share.setOnClickListener(v -> mediaIntent(uri, true));
            }
        } catch (Exception e) { historyList.addView(text("暂无下载记录", 13, MUTED, false)); }
    }
    private void mediaIntent(Uri uri, boolean share) {
        try {
            Intent intent = new Intent(share ? Intent.ACTION_SEND : Intent.ACTION_VIEW);
            if (share) { intent.setType("video/mp4"); intent.putExtra(Intent.EXTRA_STREAM, uri); }
            else intent.setDataAndType(uri, "video/mp4");
            intent.setClipData(ClipData.newRawUri("视频", uri)); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, share ? "分享视频" : "播放视频"));
        } catch (Exception e) { status.setText("文件已移除或没有可用播放器：" + e.getMessage()); }
    }
    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            String value = intent.getStringExtra(Intent.EXTRA_TEXT); if (value != null) input.setText(value);
        }
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent); }
    @Override protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, new IntentFilter(DownloadService.EVENT), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, new IntentFilter(DownloadService.EVENT));
        receiverRegistered = true; refreshHistory();
        if (DownloadService.running) {
            parseButton.setEnabled(false); downloadButton.setEnabled(false); cancelButton.setVisibility(View.VISIBLE);
            progress.setVisibility(View.VISIBLE); progress.setIndeterminate(true); status.setText("视频正在后台下载…");
        } else {
            parseButton.setEnabled(!parsing); downloadButton.setEnabled(video != null); cancelButton.setVisibility(View.GONE);
            if (!parsing) progress.setVisibility(View.GONE);
        }
    }
    @Override protected void onStop() { if (receiverRegistered) { unregisterReceiver(receiver); receiverRegistered = false; } super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("input", input.getText().toString()); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() {
        destroyed = true; generation.incrementAndGet(); workers.shutdownNow(); main.removeCallbacksAndMessages(null);
        if (browserDialog != null && browserDialog.isShowing()) browserDialog.dismiss();
        super.onDestroy();
    }
}
