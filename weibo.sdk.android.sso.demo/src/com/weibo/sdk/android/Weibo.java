package com.weibo.sdk.android;

import java.util.List;

import com.weibo.sdk.android.util.Utility;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;

/**
 *
 * @author luopeng (luopeng@staff.sina.com.cn)
 */
public class Weibo {
	public static String URL_OAUTH2_ACCESS_AUTHORIZE = "https://api.weibo.com/oauth2/authorize";

	private static Weibo mWeiboInstance = null;

	public static String app_key = "";//第三方应用的appkey
	public static String redirecturl = "";// 重定向url

	public Oauth2AccessToken accessToken = null;//AccessToken实例

	public static final String KEY_TOKEN = "access_token";
	public static final String KEY_EXPIRES = "expires_in";
	public static final String KEY_REFRESHTOKEN = "refresh_token";
	public static final String KEY_CODE = "code";
	public static boolean isWifi=false;//当前是否为wifi
	/**
	 *
	 * @param appKey 第三方应用的appkey
	 * @param redirectUrl 第三方应用的回调页
	 * @return Weibo的实例
	 */
	public synchronized static Weibo getInstance(String appKey, String redirectUrl) {
		if (mWeiboInstance == null) {
			mWeiboInstance = new Weibo();
		}
		app_key = appKey;
		Weibo.redirecturl = redirectUrl;
		return mWeiboInstance;
	}
	/**
	 * 设定第三方使用者的appkey和重定向url
	 * @param appKey 第三方应用的appkey
	 * @param redirectUrl 第三方应用的回调页
	 */
	public void setupConsumerConfig(String appKey,String redirectUrl) {
		app_key = appKey;
		redirecturl = redirectUrl;
	}
	/**
	 *
	 * 进行微博认证
	 * @param activity 调用认证功能的Context实例
	 * @param listener WeiboAuthListener 微博认证的回调接口
	 */
	public void authorize(Context context, WeiboAuthListener listener) {
		isWifi=Utility.isWifi(context);
		startAuthDialog(context, listener);
	}

	public void startAuthDialog(Context context, final WeiboAuthListener listener) {
		WeiboParameters params = new WeiboParameters();
		CookieSyncManager.createInstance(context);
		startDialog(context, params, new WeiboAuthListener() {
			@Override
			public void onComplete(Bundle values) {
				// ensure any cookies set by the dialog are saved
				CookieSyncManager.getInstance().sync();
				final String code = values.getString(KEY_CODE);
				if (!TextUtils.isEmpty(code)) {
					Log.d("Weibo-authorize", "Login Success! code=" + code);
					listener.onComplete(values);
				} else {
					Log.d("Weibo-authorize", "Failed to receive code");
					listener.onWeiboException(new WeiboException("Failed to receive code."));
				}
			}

			@Override
			public void onError(WeiboDialogError error) {
				Log.d("Weibo-authorize", "Login failed: " + error);
				listener.onError(error);
			}

			@Override
			public void onWeiboException(WeiboException error) {
				Log.d("Weibo-authorize", "Login failed: " + error);
				listener.onWeiboException(error);
			}

			@Override
			public void onCancel() {
				Log.d("Weibo-authorize", "Login canceled");
				listener.onCancel();
			}
		});
	}

	public void startDialog(Context context, WeiboParameters parameters,
			final WeiboAuthListener listener) {
		parameters.add("client_id", app_key);
		parameters.add("response_type", "code");
		parameters.add("redirect_uri", redirecturl);
		parameters.add("display", "mobile");
        parameters.add("forcelogin", "true");
        parameters.add("scope", "friendships_groups_read,friendships_groups_write,follow_app_official_microblog");

		if (accessToken != null && accessToken.isSessionValid()) {
			parameters.add(KEY_TOKEN, accessToken.getToken());
		}
		String url = URL_OAUTH2_ACCESS_AUTHORIZE + "?" + Utility.encodeUrl(parameters);
		if (context.checkCallingOrSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
			Utility.showAlert(context, "Error",
					"Application requires permission to access the Internet");
		} else {
			new WeiboDialog(context, url, listener).show();
		}
	}

}
