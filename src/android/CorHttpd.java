package com.rjfun.cordova.httpd;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.KeyStore;
import java.util.Enumeration;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;
import android.content.Context;
import android.content.res.AssetManager;

/**
 * This class echoes a string called from JavaScript.
 */
public class CorHttpd extends CordovaPlugin {

    /** Common tag used for logging statements. */
    private static final String LOGTAG = "CorHttpd";
    
    /** Cordova Actions. */
    private static final String ACTION_START_SERVER = "startServer";
    private static final String ACTION_STOP_SERVER = "stopServer";
    private static final String ACTION_GET_URL = "getURL";
    private static final String ACTION_GET_LOCAL_PATH = "getLocalPath";
    
    private static final String OPT_WWW_ROOT = "www_root";
    private static final String OPT_PORT = "port";
    private static final String OPT_LOCALHOST_ONLY = "localhost_only";

    private String www_root = "";
	private int port = 8888;
	private boolean localhost_only = false;

	private String localPath = "";
	//private WebServer server = null;
    private SimpleWebServer server = null;
	private String	url = "";

    @Override
    public boolean execute(String action, JSONArray inputs, CallbackContext callbackContext) throws JSONException {
        PluginResult result = null;
        if (ACTION_START_SERVER.equals(action)) {
            result = startServer(inputs, callbackContext);
            
        } else if (ACTION_STOP_SERVER.equals(action)) {
            result = stopServer(inputs, callbackContext);
            
        } else if (ACTION_GET_URL.equals(action)) {
            result = getURL(inputs, callbackContext);
            
        } else if (ACTION_GET_LOCAL_PATH.equals(action)) {
            result = getLocalPath(inputs, callbackContext);
            
        } else {
            Log.d(LOGTAG, String.format("Invalid action passed: %s", action));
            result = new PluginResult(Status.INVALID_ACTION);
        }
        
        if(result != null) callbackContext.sendPluginResult( result );
        
        return true;
    }
    
    private String __getLocalIpAddress() {
    	try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (! inetAddress.isLoopbackAddress()) {
                        if (inetAddress instanceof Inet4Address) {
                            String ip = inetAddress.getHostAddress();
                    		Log.w(LOGTAG, "local IP: "+ ip);
                    		return ip;
                    	}
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(LOGTAG, ex.toString());
        }
    	
		return "127.0.0.1";
    }

    private PluginResult startServer(JSONArray inputs, CallbackContext callbackContext) {
		Log.w(LOGTAG, "startServer");

        JSONObject options = inputs.optJSONObject(0);
        if(options == null) return null;
        
        www_root = options.optString(OPT_WWW_ROOT);
        port = options.optInt(OPT_PORT, 8888);
        localhost_only = options.optBoolean(OPT_LOCALHOST_ONLY, false);
        
        if(www_root.startsWith("/")) {
    		//localPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        	localPath = www_root;
        } else {
        	//localPath = "file:///android_asset/www";
        	localPath = "www";
        	if(www_root.length()>0) {
        		localPath += "/";
        		localPath += www_root;
        	}
        }

        final CallbackContext delayCallback = callbackContext;
        cordova.getActivity().runOnUiThread(new Runnable(){
			@Override
            public void run() {
				String errmsg = __startServer();
				if (errmsg.length() > 0) {
					delayCallback.error( errmsg );
				} else {
                    if (localhost_only) {
                        url = "https://127.0.0.1:" + port;
                    } else {
                        url = "https://" + __getLocalIpAddress() + ":" + port;
                    }
	                delayCallback.success( url );
				}
            }
        });
        
        return null;
    }
    
    private String __startServer() {
    	String errmsg = "";
    	try {
    		AndroidFile f = new AndroidFile(localPath);
    		
	        Context ctx = cordova.getActivity().getApplicationContext();
			AssetManager am = ctx.getResources().getAssets();
    		f.setAssetManager( am );
    		
    		/*if(localhost_only) {
    			InetSocketAddress localAddr = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}), port);
    			server = new WebServer(localAddr, f);
    		} else {
    			server = new WebServer(port, f);
    		}*/
            server = new SimpleWebServer(null, port, f, false, "*");
            //System.setProperty("javax.net.ssl.trustStore", new File("/com/rjfun/cordova/httpd/server-ec.bks").getAbsolutePath());
            System.setProperty("java.io.tmpdir", localPath);
            String assetsFileNames = String.join(";", am.list(""));
            if (assetsFileNames.indexOf("server-ec.bks") == -1) {
                throw new IOException("server-ec.bks does not exist!");
            }
            InputStream keystoreStream = am.open("server-ec.bks");
            //server.makeSecure(NanoHTTPD.makeSSLSocketFactory(System.getProperty("javax.net.ssl.trustStore"), "123456".toCharArray()), null);
            server.makeSecure(NanoHTTPD.makeSSLSocketFactory(keystoreStream, "123456".toCharArray()), null);
            server.start();
		} catch (IOException e) {
			//errmsg = String.format("IO Exception: %s;keyStoreDefaultType: %s;trustStore: %s;tmpdir: %s", e.getMessage(), KeyStore.getDefaultType(), System.getProperty("javax.net.ssl.trustStore"), System.getProperty("java.io.tmpdir"));
            errmsg = String.format("IO Exception: %s;keyStoreDefaultType: %s;tmpdir: %s", e.getMessage(), KeyStore.getDefaultType(), System.getProperty("java.io.tmpdir"));
			Log.w(LOGTAG, errmsg);
		}
    	return errmsg;
    }

    private void __stopServer() {
		if (server != null) {
			server.stop();
			server = null;
		}
    }
    
   private PluginResult getURL(JSONArray inputs, CallbackContext callbackContext) {
		Log.w(LOGTAG, "getURL");
		
    	callbackContext.success( this.url );
        return null;
    }

    private PluginResult getLocalPath(JSONArray inputs, CallbackContext callbackContext) {
		Log.w(LOGTAG, "getLocalPath");
		
    	callbackContext.success( this.localPath );
        return null;
    }

    private PluginResult stopServer(JSONArray inputs, CallbackContext callbackContext) {
		Log.w(LOGTAG, "stopServer");
		
        final CallbackContext delayCallback = callbackContext;
        cordova.getActivity().runOnUiThread(new Runnable(){
			@Override
            public void run() {
				__stopServer();
				url = "";
				localPath = "";
                delayCallback.success();
            }
        });
        
        return null;
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     *
     * @param multitasking		Flag indicating if multitasking is turned on for app
     */
    public void onPause(boolean multitasking) {
    	//if(! multitasking) __stopServer();
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     * @param multitasking		Flag indicating if multitasking is turned on for app
     */
    public void onResume(boolean multitasking) {
    	//if(! multitasking) __startServer();
    }

    /**
     * The final call you receive before your activity is destroyed.
     */
    public void onDestroy() {
    	__stopServer();
    }
}
