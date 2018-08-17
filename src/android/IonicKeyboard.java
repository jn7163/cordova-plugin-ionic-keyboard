package io.ionic.keyboard;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.inputmethod.InputMethodManager;

// import additionally required classes for calculating screen height
import android.view.Display;
import android.graphics.Point;
import android.os.Build;

import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import android.provider.Settings;


public class IonicKeyboard extends CordovaPlugin {
    private OnGlobalLayoutListener list;
    private View rootView;

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if ("hide".equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    //http://stackoverflow.com/a/7696791/1091751
                    InputMethodManager inputManager = (InputMethodManager) cordova.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    View v = cordova.getActivity().getCurrentFocus();

                    if (v == null) {
                        callbackContext.error("No current focus");
                    } else {
                        inputManager.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                        callbackContext.success(); // Thread-safe.
                    }
                }
            });
            return true;
        }
        if ("show".equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    ((InputMethodManager) cordova.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE)).toggleSoftInput(0, InputMethodManager.HIDE_IMPLICIT_ONLY);
                    callbackContext.success(); // Thread-safe.
                }
            });
            return true;
        }
        if ("init".equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    //calculate density-independent pixels (dp)
                    //http://developer.android.com/guide/practices/screens_support.html
                    DisplayMetrics dm = new DisplayMetrics();
                    cordova.getActivity().getWindowManager().getDefaultDisplay().getRealMetrics(dm);
                    final float density = dm.density;

                    //http://stackoverflow.com/a/4737265/1091751 detect if keyboard is showing
                    rootView = cordova.getActivity().getWindow().getDecorView().findViewById(android.R.id.content).getRootView();
                    list = new OnGlobalLayoutListener() {
                        int previousHeightDiff = 0;

                        @Override
                        public void onGlobalLayout() {
                            Rect r = new Rect();
                            //r will be populated with the coordinates of your view that area still visible.
                            rootView.getWindowVisibleDisplayFrame(r);

                            PluginResult result;

                            // cache properties for later use
                            int rootViewHeight = rootView.getRootView().getHeight();
                            int resultBottom = r.bottom;

                            // calculate screen height differently for android versions >= 21: Lollipop 5.x, Marshmallow 6.x
                            //http://stackoverflow.com/a/29257533/3642890 beware of nexus 5
                            int screenHeight;

                            if (Build.VERSION.SDK_INT >= 21) {
                                Display display = cordova.getActivity().getWindowManager().getDefaultDisplay();
                                Point size = new Point();
                                display.getSize(size);
                                screenHeight = size.y;

                                if (Rom.isMiui()) {
                                    //小米全面屏开启
                                    if (Settings.Global.getInt(cordova.getActivity().getContentResolver(), "force_fsg_nav_bar", 0) != 0) {
                                        DisplayMetrics metric = new DisplayMetrics();
                                        cordova.getActivity().getWindowManager().getDefaultDisplay().getRealMetrics(metric);
                                        screenHeight = metric.heightPixels;
                                    }
                                }
                            } else {
                                screenHeight = rootViewHeight;
                            }

                            int heightDiff = screenHeight - resultBottom;

                            int pixelHeightDiff = (int) (heightDiff / density);
                            if (pixelHeightDiff > 100 && pixelHeightDiff != previousHeightDiff) { // if more than 100 pixels, its probably a keyboard...
                                String msg = "S" + Integer.toString(pixelHeightDiff);
                                result = new PluginResult(PluginResult.Status.OK, msg);
                                result.setKeepCallback(true);
                                callbackContext.sendPluginResult(result);
                            } else if (pixelHeightDiff != previousHeightDiff && (previousHeightDiff - pixelHeightDiff) > 100) {
                                String msg = "H";
                                result = new PluginResult(PluginResult.Status.OK, msg);
                                result.setKeepCallback(true);
                                callbackContext.sendPluginResult(result);
                            }
                            previousHeightDiff = pixelHeightDiff;
                        }
                    };

                    rootView.getViewTreeObserver().addOnGlobalLayoutListener(list);


                    PluginResult dataResult = new PluginResult(PluginResult.Status.OK);
                    dataResult.setKeepCallback(true);
                    callbackContext.sendPluginResult(dataResult);
                }
            });
            return true;
        }
        return false;  // Returning false results in a "MethodNotFound" error.
    }

    @Override
    public void onDestroy() {
        rootView.getViewTreeObserver().removeOnGlobalLayoutListener(list);
    }

}

class Rom {

    private static final String TAG = "Rom";

    public static final String ROM_MIUI = "MIUI";
    public static final String ROM_EMUI = "EMUI";
    public static final String ROM_FLYME = "FLYME";
    public static final String ROM_OPPO = "OPPO";
    public static final String ROM_SMARTISAN = "SMARTISAN";
    public static final String ROM_VIVO = "VIVO";
    public static final String ROM_QIKU = "QIKU";

    private static final String KEY_VERSION_MIUI = "ro.miui.ui.version.name";
    private static final String KEY_VERSION_EMUI = "ro.build.version.emui";
    private static final String KEY_VERSION_OPPO = "ro.build.version.opporom";
    private static final String KEY_VERSION_SMARTISAN = "ro.smartisan.version";
    private static final String KEY_VERSION_VIVO = "ro.vivo.os.version";

    private static String sName;
    private static String sVersion;

    public static boolean isEmui() {
        return check(ROM_EMUI);
    }

    public static boolean isMiui() {
        return check(ROM_MIUI);
    }

    public static boolean isVivo() {
        return check(ROM_VIVO);
    }

    public static boolean isOppo() {
        return check(ROM_OPPO);
    }

    public static boolean isFlyme() {
        return check(ROM_FLYME);
    }

    public static boolean is360() {
        return check(ROM_QIKU) || check("360");
    }

    public static boolean isSmartisan() {
        return check(ROM_SMARTISAN);
    }

    public static String getName() {
        if (sName == null) {
            check("");
        }
        return sName;
    }

    public static String getVersion() {
        if (sVersion == null) {
            check("");
        }
        return sVersion;
    }

    public static boolean check(String rom) {
        if (sName != null) {
            return sName.equals(rom);
        }

        if (!TextUtils.isEmpty(sVersion = getProp(KEY_VERSION_MIUI))) {
            sName = ROM_MIUI;
        } else if (!TextUtils.isEmpty(sVersion = getProp(KEY_VERSION_EMUI))) {
            sName = ROM_EMUI;
        } else if (!TextUtils.isEmpty(sVersion = getProp(KEY_VERSION_OPPO))) {
            sName = ROM_OPPO;
        } else if (!TextUtils.isEmpty(sVersion = getProp(KEY_VERSION_VIVO))) {
            sName = ROM_VIVO;
        } else if (!TextUtils.isEmpty(sVersion = getProp(KEY_VERSION_SMARTISAN))) {
            sName = ROM_SMARTISAN;
        } else {
            sVersion = Build.DISPLAY;
            if (sVersion.toUpperCase().contains(ROM_FLYME)) {
                sName = ROM_FLYME;
            } else {
                sVersion = Build.UNKNOWN;
                sName = Build.MANUFACTURER.toUpperCase();
            }
        }
        return sName.equals(rom);
    }

    public static String getProp(String name) {
        String line = null;
        BufferedReader input = null;
        try {
            Process p = Runtime.getRuntime().exec("getprop " + name);
            input = new BufferedReader(new InputStreamReader(p.getInputStream()), 1024);
            line = input.readLine();
            input.close();
        } catch (IOException ex) {
            Log.e(TAG, "Unable to read prop " + name, ex);
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return line;
    }
}
