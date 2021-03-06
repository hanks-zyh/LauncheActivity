package xyz.hanks.launchactivity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import net.dongliu.apk.parser.ApkParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import xyz.hanks.launchactivity.util.ToastUtils;

/**
 * Simple example that parses the AndroidManifest.xml and displays the source in a WebView
 */
public class XmlSourceViewerActivity extends BaseActivity {


    public static final String EXTRA_APPLICATION = "extra_application";

    private final WebViewClient webViewClient = new WebViewClient() {

        @Override
        public void onPageFinished(WebView view, String url) {
            ProgressBar progress = (ProgressBar) findViewById(R.id.progress);
            progress.setVisibility(View.GONE);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            InputStream stream = inputStreamForAndroidResource(url);
            if (stream != null) {
                return new WebResourceResponse("text/javascript", "UTF-8", stream);
            }
            return super.shouldInterceptRequest(view, url);
        }

        private InputStream inputStreamForAndroidResource(String url) {
            final String ANDROID_ASSET = "file:///android_asset/";

            if (url.contains(ANDROID_ASSET)) {
                url = url.replaceFirst(ANDROID_ASSET, "");
                try {
                    AssetManager assets = getAssets();
                    Uri uri = Uri.parse(url);
                    return assets.open(uri.getPath(), AssetManager.ACCESS_STREAMING);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

    };
    private WebView webView;
    private String sourceCodeText;
    private ApplicationInfo apkInfo;

    public static void start(Context context, ApplicationInfo apkInfo) {
        Intent starter = new Intent(context, XmlSourceViewerActivity.class);
        starter.putExtra(EXTRA_APPLICATION, apkInfo);
        context.startActivity(starter);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        apkInfo = getIntent().getParcelableExtra(EXTRA_APPLICATION);
        File apkFile = new File(apkInfo.sourceDir);
        if (!apkFile.exists()) {
            ToastUtils.show("file don't exists: " + apkFile.getAbsolutePath());
            finish();
        }

        if (mToolbar != null) {
            mToolbar.setTitle(apkInfo.loadLabel(getPackageManager()));
            mToolbar.setSubtitle(getString(R.string.manifest));
        }

        webView = (WebView) findViewById(R.id.source_view);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDefaultTextEncodingName("UTF-8");
        webView.setWebViewClient(webViewClient);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        if (savedInstanceState != null && savedInstanceState.containsKey("source")) {
            sourceCodeText = savedInstanceState.getString("source");
        }

        if (sourceCodeText == null) {
            new AndroidXmlLoader().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            loadSourceCode(sourceCodeText);
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.source_viewer;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("source", sourceCodeText);
    }

    private void loadSourceCode(String html) {
        String data = String.format(
                "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML a.0 Transitional//EN\" \"http://www.w3" +
                        ".org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http://www.w3" +
                        ".org/1999/xhtml\"><head><meta http-equiv=\"Content-Type\" content=\"text/html; " +
                        "charset=utf-8\" /><script src=\"run_prettify.js?skin=github\"></script></head><body " +
                        "bgcolor=\"white\"><pre class=\"prettyprint linenums\">%s</pre></body></html>",
                html);
        webView.loadDataWithBaseURL("file:///android_asset/", data, "text/html", "UTF-8", null);
    }

    private final class AndroidXmlLoader extends AsyncTask<PackageInfo, Void, String> {

        @Override
        protected String doInBackground(PackageInfo... params) {
            try {
                ApkParser parser = new ApkParser(apkInfo.sourceDir);
                final String source = parser.getManifestXml();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    return Html.escapeHtml(source);
                } else {
                    return source;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String escapedHtml) {
            sourceCodeText = escapedHtml;
            loadSourceCode(escapedHtml);
        }
    }

}