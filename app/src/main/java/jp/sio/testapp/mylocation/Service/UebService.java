package jp.sio.testapp.mylocation.Service;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.app.Service;
import android.os.IBinder;
import android.util.Log;
import android.os.Handler;

import java.util.Timer;
import java.util.TimerTask;

import jp.sio.testapp.mylocation.L;
import jp.sio.testapp.mylocation.R;
import jp.sio.testapp.mylocation.Repository.LocationLog;

/**
 * UEB測位を行うためのService
 * 測位回数、測位間隔、タイムアウト、SuplEndWaitTimeあたりが渡されればいいか？
 * Created by NTT docomo on 2017/05/22.
 */

public class UebService extends Service implements LocationListener{

    LocationManager locationManager;
    LocationLog locationLog;

    private Handler resultHandler;
    private Handler intervalHandler;
    private Handler stopHandler;
    private Timer stopTimer;
    private Timer intervalTimer;
    private StopTimerTask stopTimerTask;
    private IntervalTimerTask intervalTimerTask;

    //設定の測位回数、測位間隔、測位タイムアウト、SuplEndWaitTime
    private int settingCount;
    private long settingInterval;
    private long settingTimeout;
    private int settingSuplEndWaitTime;

    //測位中の測位回数
    private int runningCount;

    //測位成功の場合:true 測位失敗の場合:false を設定
    private boolean isLocationFix;

    public class UebService_Binder extends Binder{
        public UebService getService(){
            return UebService.this;
        }
    }

    //TODO:
    //サービスがKillされるのを防止する処理
    // Android 6.0 以降の省電力の処理確認してから作る予定

    //TODO:
    //スリープ時に停止するのを防止
    //これもAndroid 6.0 以降の省電力の処理確認してから作る予定
    //PowerManagerを使う

    @Override
    public void onCreate(){
        super.onCreate();

        resultHandler = new Handler();
        intervalHandler = new Handler();
        stopHandler = new Handler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startid){
        super.onStartCommand(intent,flags,startid);
        L.d("onStartCommand");

        settingCount = intent.getIntExtra(getBaseContext().getString(R.string.settingCount),0);
        settingTimeout = intent.getLongExtra(getBaseContext().getString(R.string.settingTimeout),0) * 1000;
        settingInterval = intent.getLongExtra(getBaseContext().getString(R.string.settingInterval),0) * 1000;
        runningCount = 0;
        L.d("count:" + settingCount + " Timeout:" + settingTimeout + " Interval:" + settingInterval);
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        locationStart();

        return START_STICKY;
    }

    /**
     * 測位を開始する時の処理
     */
    public void locationStart(){

        L.d("locationStart");

        //TODO:設定でColdすることになっているならって判定を入れる
        coldLocation(locationManager);

        //MyLocationUsecaseで起動時にPermissionCheckを行っているので
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0,0,this);

        L.d("requestLocationUpdates");

        //測位停止Timerの設定
        if(stopTimer == null){
            L.d("stoptimer");
            stopTimerTask = new StopTimerTask();
            stopTimer = new Timer(true);
            stopTimer.schedule(stopTimerTask,settingTimeout);
        }
        //TODO: 測位開始の時刻を取得する処理を追加する
    }

    /**
     * 測位成功の場合の処理
     */
    public void locationSuccess(final Location location){
        L.d("locationSuccess");

        //測位タイムアウトのタイマーをクリア
        stopTimer.cancel();

        runningCount++;
        isLocationFix = true;

        //TODO: 測位成功の時間を取得する処理を追加する

        //測位結果の通知
        resultHandler.post(new Runnable() {
            double ttff = 20.0;
            @Override
            public void run() {
                L.d("resultHandler.post");
                sendBroadCast(isLocationFix,location.getLatitude(),location.getLongitude(),ttff);
            }
        });
        L.d(location.getLatitude() + " " + location.getLongitude());

        //TODO: SulpEndWaitTime待つ処理を入れる
        locationManager.removeUpdates(this);

        //測位回数が設定値に到達しているかチェック
        if(runningCount == settingCount){
            serviceStop();
        }else{
            //回数満了してなければ測位間隔Timerを設定して次の測位の準備
            L.d("SuccessのIntervalTimer");
            intervalTimerTask = new IntervalTimerTask();
            intervalTimer = new Timer(true);
            L.d("Interval:" + settingInterval);
            intervalTimer.schedule(intervalTimerTask, settingInterval);
        }
    }

    /**
     * 測位失敗の場合の処理
     * 今のところタイムアウトした場合のみを想定
     */
    public void locationFailed(){
        L.d("locationFailed");

        runningCount++;
        isLocationFix = false;
        locationManager.removeUpdates(this);
        //TODO: 測位失敗の時間を取得する処理を追加する

        //測位結果の通知
        resultHandler.post(new Runnable() {
            double ttff = 30.0;
            @Override
            public void run() {
                L.d("resultHandler.post");
                sendBroadCast(isLocationFix,-1,-1,ttff);
            }
        });

        //測位回数が設定値に到達しているかチェック
        if(runningCount == settingCount){
            serviceStop();
        }else{
            L.d("FailedのIntervalTimer");
            //回数満了してなければ測位間隔Timerを設定して次の測位の準備
            intervalTimerTask = new IntervalTimerTask();
            intervalTimer = new Timer(true);
            L.d("Interval:" + settingInterval);
            intervalTimer.schedule(intervalTimerTask, settingInterval);
        }
    }
    /**
     * 測位が終了してこのServiceを閉じるときの処理
     * 測位回数満了、停止ボタンによる停止を想定した処理
     */
    public void serviceStop(){
        L.d("serviceStop");
        if(locationManager != null){
            //TODO: suplEndWaitTime待つ処理を入れる
            locationManager.removeUpdates(this);
            locationManager = null;
        }
        if(stopTimer != null){
            stopTimer.cancel();
            stopTimer = null;
        }
        if(intervalTimer != null){
            intervalTimer.cancel();
            intervalTimer = null;
        }
        //TODO: サービスがKillされるのを防ぐのにAlarmManagerを使ってたらそれもここで消す
        //TODO: スリープ時の停止を防止するのにPowerManagerを使っていたらそれもここで消す
        //TODO: ログ保存にReader、Writerを使ってたらそれもここで消す
    }

    @Override
    public void onLocationChanged(final Location location) {
        locationSuccess(location);
    }

    @Override
    public void onDestroy(){
        Log.d(this.getPackageName().getClass().getName(),"onDestroy");
        serviceStop();
        super.onDestroy();
    }

    /**
     * アシストデータの削除
     */
    private void coldLocation(LocationManager lm){
        boolean coldResult = lm.sendExtraCommand(LocationManager.GPS_PROVIDER,"delete_aiding_data",null);
        L.d("delete_aiding_data:result " + coldResult);
        //TODO 削除中であることを通知して3秒ぐらい待つ処理を上に投げたい
    }

    /**
     * 測位停止タイマー
     * 測位タイムアウトしたときの処理
     */
    class StopTimerTask extends TimerTask{

        @Override
        public void run() {
            stopHandler.post(new Runnable() {
                @Override
                public void run() {
                    L.d("StopTimerTask");
                    locationFailed();
                }
            });
        }
    }

    /**
     * 測位間隔タイマー
     * 測位間隔を満たしたときの次の動作（次の測位など）を処理
     */
    class IntervalTimerTask extends TimerTask{

        @Override
        public void run() {
            intervalHandler.post(new Runnable() {
                @Override
                public void run() {
                    L.d("IntervalTimerTask");
                    locationStart();
                }
            });
        }
    }

    /**
     * RemoveUpdateを設定時間分待ってから行う
     * TODO:待つ処理は後で入れる
     */
    private void waitRemoveUpdate(){
        locationManager.removeUpdates(this);
    }

    protected void sendBroadCast(Boolean fix,double lattude,double longitude,double ttff){
        L.d("send");
        Intent broadcastIntent = new Intent(getResources().getString(R.string.locationUeb));
        broadcastIntent.putExtra(getResources().getString(R.string.TagisFix),fix);
        broadcastIntent.putExtra(getResources().getString(R.string.TagLat),lattude);
        broadcastIntent.putExtra(getResources().getString(R.string.TagLong),longitude);
        broadcastIntent.putExtra(getResources().getString(R.string.Tagttff),ttff);

        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }
    @Override
    public void onProviderEnabled(String provider) {

    }
    @Override
    public void onProviderDisabled(String provider) {

    }
    @Override
    public boolean onUnbind(Intent intent) {
        return true; // 再度クライアントから接続された際に onRebind を呼び出させる場合は true を返す
    }
    @Override
    public IBinder onBind(Intent intent) {
        return new UebService_Binder();
    }

    @Override
    public void onRebind(Intent intent) {
    }
}