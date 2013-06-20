package com.weibo.sdk.android;

import java.io.IOException;
import java.io.InputStream;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.NinePatchDrawable;
import android.net.http.SslError;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Xml;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.weibo.sdk.android.demo.R;
import com.weibo.sdk.android.util.Utility;
/**
 * 用来显示用户认证界面的dialog，封装了一个webview，通过redirect地址中的参数来获取accesstoken
 * @author xiaowei6@staff.sina.com.cn
 *
 */
public class WeiboDialog extends Dialog {

	static  FrameLayout.LayoutParams FILL = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT);
	private String mUrl;
	private WeiboAuthListener mListener;
	private WebView mWebView;
	private RelativeLayout mWebViewContainer;
	private LinearLayout mContent;
	private View mLoadingView;

	private final static String TAG = "Weibo-WebView";

	private static int theme=android.R.style.Theme_Translucent_NoTitleBar;
	public WeiboDialog(Context context, String url, WeiboAuthListener listener) {
		super(context,theme);
		mUrl = url;
		mListener = listener;

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFeatureDrawableAlpha(Window.FEATURE_OPTIONS_PANEL, 0);
		mContent = new LinearLayout(getContext());
		mContent.setOrientation(LinearLayout.VERTICAL);
		setUpTitleBar();
		setUpWebView();

		addContentView(mContent, new LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.FILL_PARENT));
	}

	private void setUpTitleBar() {
	    final View titleBar = View.inflate(getContext(), R.layout.login_title_bar, null);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT,
                LayoutParams.WRAP_CONTENT);

        mLoadingView = titleBar.findViewById(R.id.login_title_progressbar);
        mContent.addView(titleBar, lp);
	}

	protected void onBack() {
		try {
			if (null != mWebView) {
				mWebView.stopLoading();
				mWebView.destroy();
			}
		} catch (Exception e) {
		}
		dismiss();
	}

	private void setUpWebView() {
		mWebViewContainer = new RelativeLayout(getContext());
		mWebView = new WebView(getContext());
		mWebView.setVerticalScrollBarEnabled(false);
		mWebView.setHorizontalScrollBarEnabled(false);
		mWebView.getSettings().setJavaScriptEnabled(true);
		mWebView.setWebViewClient(new WeiboDialog.WeiboWebViewClient());
		mWebView.loadUrl(mUrl);
		mWebView.setLayoutParams(FILL);
		mWebView.setVisibility(View.INVISIBLE);

		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.FILL_PARENT);

        mContent.setBackgroundColor(Color.WHITE);

        mWebViewContainer.addView(mWebView);
		mWebViewContainer.setGravity(Gravity.CENTER);

	    lp.leftMargin = 0;
        lp.topMargin = 0;
        lp.rightMargin =0;
        lp.bottomMargin = 0;
        mContent.addView(mWebViewContainer, lp);
	}

	private class WeiboWebViewClient extends WebViewClient {

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			Log.d(TAG, "Redirect URL: " + url);
			 if (url.startsWith("sms:")) {  //针对webview里的短信注册流程，需要在此单独处理sms协议
	                Intent sendIntent = new Intent(Intent.ACTION_VIEW);
	                sendIntent.putExtra("address", url.replace("sms:", ""));
	                sendIntent.setType("vnd.android-dir/mms-sms");
	                WeiboDialog.this.getContext().startActivity(sendIntent);
	                return true;
	            }
			return super.shouldOverrideUrlLoading(view, url);
		}

		@Override
		public void onReceivedError(WebView view, int errorCode, String description,
				String failingUrl) {
			super.onReceivedError(view, errorCode, description, failingUrl);
			mListener.onError(new WeiboDialogError(description, errorCode, failingUrl));
			WeiboDialog.this.dismiss();
		}

		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			Log.d(TAG, "onPageStarted URL: " + url);
			if (url.startsWith(Weibo.redirecturl)) {
				handleRedirectUrl(view, url);
				view.stopLoading();
				WeiboDialog.this.dismiss();
				return;
			}
			super.onPageStarted(view, url, favicon);
			mLoadingView.setVisibility(View.VISIBLE);
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			Log.d(TAG, "onPageFinished URL: " + url);
			super.onPageFinished(view, url);
            mLoadingView.setVisibility(View.GONE);
			mWebView.setVisibility(View.VISIBLE);
		}

        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
			handler.proceed();
		}

	}

	private void handleRedirectUrl(WebView view, String url) {
		Bundle values = Utility.parseUrl(url);

		String error = values.getString("error");
		String error_code = values.getString("error_code");

		if (error == null && error_code == null) {
			mListener.onComplete(values);
		} else if (error.equals("access_denied")) {
			// 用户或授权服务器拒绝授予数据访问权限
			mListener.onCancel();
		} else {
			if(error_code==null){
				mListener.onWeiboException(new WeiboException(error, 0));
			}
			else{
				mListener.onWeiboException(new WeiboException(error, Integer.parseInt(error_code)));
			}

		}
	}

    @Override
    public void onBackPressed() {
        try {
            if (null != mWebView) {
                mWebView.stopLoading();
                mWebView.destroy();
            }
        } catch (Exception e) {
        }
        mListener.onCancel();
        super.onBackPressed();
    }
}
