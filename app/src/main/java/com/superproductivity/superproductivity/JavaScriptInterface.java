package com.superproductivity.superproductivity;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.lang.ref.WeakReference;

import static android.content.Context.NOTIFICATION_SERVICE;
import static com.superproductivity.superproductivity.Google.RC_SIGN_IN;

public class JavaScriptInterface {
    private FullscreenActivity mContext;
    private WebView webView;
    private Google g;

    /**
     * Instantiate the interface and set the context
     */
    JavaScriptInterface(FullscreenActivity c, WebView wv) {
        mContext = c;
        webView = wv;
    }

    void onActivityResult(int requestCode, int resultCode, Intent data) {
//        Toast.makeText(mContext, "JavaScriptInterface onActivityResult", Toast.LENGTH_SHORT).show();

        if (requestCode == RC_SIGN_IN) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            _handleSignInResult(task);
        }
    }


    @SuppressWarnings("unused")
    @JavascriptInterface
    public void showToast(String toast) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void updateTaskData(String str) {
        Log.w("TW", "JavascriptInterface: updateTaskData");
        Intent intent = new Intent(mContext.getApplicationContext(), TaskListWidget.class);
        intent.setAction(TaskListWidget.LIST_CHANGED);
        intent.putExtra("taskJson", str);

        TaskListDataService.getInstance().setData(str);
        mContext.sendBroadcast(intent);
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void showNotification(String title, String body) {
        Log.d("TW", "title " + title);
        Log.d("TW", "body " + body);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mContext.getApplicationContext(), "SUP_CHANNEL_ID");
        Intent ii = new Intent(mContext.getApplicationContext(), FullscreenActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, ii, 0);

        NotificationCompat.BigTextStyle bigText = new NotificationCompat.BigTextStyle();
        bigText.setBigContentTitle(title);

        if ((body != null) && !body.isEmpty() && !(body.trim().equals("undefined"))) {
            bigText.bigText(body);
        }

        mBuilder.setContentIntent(pendingIntent);
        mBuilder.setSmallIcon(R.mipmap.ic_launcher);
        mBuilder.setLargeIcon(BitmapFactory.decodeResource(mContext.getResources(),
                R.mipmap.ic_launcher));
        mBuilder.setSmallIcon(R.drawable.ic_stat_name);
        mBuilder.setPriority(Notification.PRIORITY_MAX);
        mBuilder.setStyle(bigText);

        NotificationManager mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

// === Removed some obsoletes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "SUP_CHANNEL_ID";
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Super Productivity",
                    NotificationManager.IMPORTANCE_HIGH);
            mNotificationManager.createNotificationChannel(channel);
            mBuilder.setChannelId(channelId);
        }

        mNotificationManager.notify(0, mBuilder.build());
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void triggerGetGoogleToken() {
        g = new Google();
        g.load(mContext);
        g.signIn(mContext);
    }


    private void _handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            new GetAccessToken(mContext, account).execute();
            // Signed in successfully, show authenticated UI.
        } catch (ApiException e) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w("TW", "signInResult:failed code=" + e.getStatusCode());
            _callJavaScriptFunction("window.googleGetTokenErrorCallback(\'" + e.getStatusCode() + "\')");
        }
    }

    private static class GetAccessToken extends AsyncTask<Void, Void, String> {
        private WeakReference<FullscreenActivity> activityReference;
        private GoogleSignInAccount account;

        // only retain a weak reference to the activity
        GetAccessToken(FullscreenActivity context, GoogleSignInAccount accountIn) {
            activityReference = new WeakReference<>(context);
            account = accountIn;
        }

        @Override
        protected String doInBackground(Void... params) {
            FullscreenActivity activity = activityReference.get();
            String accessToken = null;
            try {
                accessToken = GoogleAuthUtil.getToken(activity, account.getEmail(), "oauth2:profile email");
                activity.callJavaScriptFunction("window.googleGetTokenSuccessCallback(\'" + accessToken + "\')");
                Log.d("TW", "accessToken " + accessToken);
            } catch (IOException | GoogleAuthException e) {
                e.printStackTrace();
            }
            return accessToken;

        }

        @Override
        protected void onPostExecute(String result) {
            // get a reference to the activity if it is still there
            FullscreenActivity activity = activityReference.get();
        }
    }


    private void _callJavaScriptFunction(final String script) {
        mContext.callJavaScriptFunction(script);
    }
}
