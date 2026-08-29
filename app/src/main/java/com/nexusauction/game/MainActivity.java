package com.nexusauction.game;
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.wifi.WifiManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.Base64;
public class MainActivity extends Activity {
    private WebView web;
    private static final int REQ_WIFI = 42;
    private LanService lanService;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        requestLanPermission();
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true); s.setAllowContentAccess(true); s.setMediaPlaybackRequiresUserGesture(false);
        // FIX: tanpa baris ini, sejumlah WebView Android salah menebak encoding
        // saat memuat file lokal (file://), sehingga emoji/simbol tampil sebagai
        // karakter aneh (mojibake) walau index.html sudah punya <meta charset="utf-8">.
        s.setDefaultTextEncodingName("UTF-8");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(final PermissionRequest r){
                runOnUiThread(() -> r.grant(r.getResources()));
            }
        });
        lanService = new LanService(this);
        web.addJavascriptInterface(new AndroidBridge(this, lanService), "AndroidLAN");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }
    private void requestLanPermission(){
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.NEARBY_WIFI_DEVICES}, REQ_WIFI);
        else if (android.os.Build.VERSION.SDK_INT >= 23 && android.os.Build.VERSION.SDK_INT <= 32 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_WIFI);
    }
    @Override public void onDestroy(){ if(lanService!=null) lanService.close(); super.onDestroy(); }
    @Override public void onBackPressed(){ if(web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    public static class AndroidBridge {
        private final Context c; private final LanService lan;
        AndroidBridge(Context c, LanService lan){this.c=c;this.lan=lan;}
        @JavascriptInterface public boolean isAndroid(){ return true; }
        @JavascriptInterface public String localIp(){ return LanService.localIp(); }
        @JavascriptInterface public String networkStatus(){
            try { WifiManager w=(WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE); return w!=null && w.isWifiEnabled()?"WIFI_ON":"WIFI_OFF"; } catch(Exception e){ return "UNKNOWN"; }
        }
        @JavascriptInterface public String hostRoom(String roomName){ return lan.startHost(roomName==null?"Nexus Auction":roomName); }
        @JavascriptInterface public void stopRoom(){ lan.stopHost(); }
        @JavascriptInterface public void discoverRooms(){ lan.startDiscovery(); }
    }
    static class LanService {
        static final String SERVICE_TYPE = "_nexusauction._tcp.";
        final MainActivity activity; final Context context; final ExecutorService pool=Executors.newCachedThreadPool();
        final List<WsClient> clients=new CopyOnWriteArrayList<>();
        NsdManager nsd; NsdManager.RegistrationListener reg; NsdManager.DiscoveryListener discovery;
        ServerSocket server; volatile boolean running=false; int port=0; String serviceName="";
        LanService(MainActivity a){activity=a; context=a.getApplicationContext(); nsd=(NsdManager)context.getSystemService(Context.NSD_SERVICE);}
        String startHost(String roomName){
            if(running) stopHost();
            try{
                for(int p=28700;p<28850;p++) try{ server=new ServerSocket(p,20,InetAddress.getByName("0.0.0.0")); port=p; break; }catch(IOException ignored){}
                if(server==null) throw new IOException("No free LAN port");
                running=true; serviceName="NexusAuction-"+UUID.randomUUID().toString().substring(0,5);
                registerService(serviceName, port);
                pool.execute(() -> acceptLoop());
                JSONObject o=new JSONObject();o.put("ok",true);o.put("name",roomName);o.put("service",serviceName);o.put("port",port);o.put("ip",localIp());return o.toString();
            }catch(Exception e){return "{\"ok\":false,\"error\":\""+safe(e.getMessage())+"\"}";}
        }
        void acceptLoop(){while(running){try{Socket s=server.accept(); WsClient c=new WsClient(s,this);clients.add(c);pool.execute(c);}catch(IOException e){if(running){} }}}
        void registerService(String name,int p){
            try{
                NsdServiceInfo info=new NsdServiceInfo(); info.setServiceName(name); info.setServiceType(SERVICE_TYPE); info.setPort(p);
                reg=new NsdManager.RegistrationListener(){public void onServiceRegistered(NsdServiceInfo i){} public void onRegistrationFailed(NsdServiceInfo i,int e){} public void onServiceUnregistered(NsdServiceInfo i){} public void onUnregistrationFailed(NsdServiceInfo i,int e){}};
                nsd.registerService(info,NsdManager.PROTOCOL_DNS_SD,reg);
            }catch(Exception ignored){}
        }
        void startDiscovery(){
            stopDiscovery();
            discovery=new NsdManager.DiscoveryListener(){
                public void onDiscoveryStarted(String t){}
                public void onServiceFound(NsdServiceInfo info){ if(!SERVICE_TYPE.equals(info.getServiceType())) return; nsd.resolveService(info,new NsdManager.ResolveListener(){
                    public void onServiceResolved(NsdServiceInfo r){
                        try{JSONObject o=new JSONObject();o.put("name",r.getServiceName());o.put("host",r.getHost().getHostAddress());o.put("port",r.getPort());o.put("type","nexus");emit("window.onAndroidLanRoom && window.onAndroidLanRoom("+JSONObject.quote(o.toString())+");");}catch(Exception ignored){}
                    } public void onResolveFailed(NsdServiceInfo i,int e){}
                });}
                public void onServiceLost(NsdServiceInfo i){try{emit("window.onAndroidLanRoomLost && window.onAndroidLanRoomLost("+JSONObject.quote(i.getServiceName())+");");}catch(Exception ignored){}}
                public void onDiscoveryStopped(String t){} public void onStartDiscoveryFailed(String t,int e){} public void onStopDiscoveryFailed(String t,int e){}
            };
            try{nsd.discoverServices(SERVICE_TYPE,NsdManager.PROTOCOL_DNS_SD,discovery);}catch(Exception ignored){}
        }
        void stopDiscovery(){try{if(discovery!=null)nsd.stopServiceDiscovery(discovery);}catch(Exception ignored){} discovery=null;}
        void stopHost(){running=false;try{if(server!=null)server.close();}catch(Exception ignored){}for(WsClient c:clients)c.close();clients.clear();try{if(reg!=null)nsd.unregisterService(reg);}catch(Exception ignored){}reg=null;}
        void close(){stopDiscovery();stopHost();pool.shutdownNow();}
        void relay(WsClient from,String msg){for(WsClient c:clients)if(c!=from)c.send(msg);}
        void emit(String js){ activity.runOnUiThread(() -> { if(activity.web!=null) activity.web.evaluateJavascript("javascript:"+js, null); }); }
        static String localIp(){try{Enumeration<NetworkInterface> ns=NetworkInterface.getNetworkInterfaces();while(ns.hasMoreElements()){NetworkInterface n=ns.nextElement();Enumeration<InetAddress> as=n.getInetAddresses();while(as.hasMoreElements()){InetAddress a=as.nextElement();if(!a.isLoopbackAddress()&&a.getHostAddress().indexOf(':')<0)return a.getHostAddress();}}}catch(Exception ignored){}return "0.0.0.0";}
        static String safe(String s){return s==null?"error":s.replace("\\","\\\\").replace("\"","\\\"");}
    }
    static class WsClient implements Runnable {
        final Socket socket; final LanService owner; OutputStream out; InputStream in; volatile boolean open=true;
        WsClient(Socket s,LanService o){socket=s;owner=o;}
        public void run(){try{socket.setTcpNoDelay(true);in=socket.getInputStream();out=socket.getOutputStream();handshake();while(open){String m=readText();if(m==null)break;owner.relay(this,m);}}catch(Exception ignored){}finally{close();}}
        void handshake() throws Exception{BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.US_ASCII));String line,key=null;while((line=br.readLine())!=null&&!line.isEmpty()){if(line.toLowerCase(Locale.US).startsWith("sec-websocket-key:"))key=line.substring(line.indexOf(':')+1).trim();}if(key==null)throw new IOException("No websocket key");String accept=Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)), Base64.NO_WRAP);out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "+accept+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();}
        String readText() throws Exception{int b=in.read();if(b<0)return null;int b2=in.read();if(b2<0)return null;boolean masked=(b2&128)!=0;long len=b2&127;if(len==126)len=((in.read()&255)<<8)|(in.read()&255);else if(len==127){len=0;for(int i=0;i<8;i++)len=(len<<8)|(in.read()&255);}if(len>2_000_000)throw new IOException("frame too large");byte[] mask=masked?in.readNBytes(4):new byte[0];byte[] data=in.readNBytes((int)len);if(masked)for(int i=0;i<data.length;i++)data[i]=(byte)(data[i]^mask[i%4]);int op=b&15;if(op==8)return null;if(op==9){sendFrame(data,10);return readText();}if(op!=1)return null;return new String(data,StandardCharsets.UTF_8);}
        synchronized void send(String s){try{sendFrame(s.getBytes(StandardCharsets.UTF_8),1);}catch(Exception ignored){}}
        void sendFrame(byte[] d,int op)throws Exception{ByteArrayOutputStream h=new ByteArrayOutputStream();h.write(0x80|op);if(d.length<126)h.write(d.length);else if(d.length<=65535){h.write(126);h.write((d.length>>8)&255);h.write(d.length&255);}else{h.write(127);for(int i=7;i>=0;i--)h.write((d.length>>(8*i))&255);}out.write(h.toByteArray());out.write(d);out.flush();}
        void close(){open=false;try{socket.close();}catch(Exception ignored){}owner.clients.remove(this);}
    }
}
