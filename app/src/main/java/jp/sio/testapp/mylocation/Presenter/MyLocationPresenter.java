package jp.sio.testapp.mylocation.Presenter;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import java.util.logging.Handler;

import jp.sio.testapp.mylocation.Activity.MyLocationActivity;
import jp.sio.testapp.mylocation.Activity.SettingActivity;
import jp.sio.testapp.mylocation.L;
import jp.sio.testapp.mylocation.R;
import jp.sio.testapp.mylocation.Service.UebService;
import jp.sio.testapp.mylocation.Usecase.MyLocationUsecase;
import jp.sio.testapp.mylocation.Usecase.SettingUsecase ;


/**
 * Created by NTT docomo on 2017/05/23.
 * ActivityとServiceの橋渡し
 * Activityはなるべく描画だけに専念させたいから分けるため
 */

public class MyLocationPresenter {
    private MyLocationActivity activity;
    SettingUsecase settingUsecase;
    MyLocationUsecase myLocationUsecase;
    Intent settingIntent;
    Intent locationserviceIntent;

    private String receiveCategory;
    private String categoryLocation;
    private String categoryColdStart;
    private String categoryColdStop;

    private UebService uebService;
    private ServiceConnection serviceConnectionUeb = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            uebService = ((UebService.UebService_Binder)service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            uebService = null;
        }
    };

    private final LocationReceiver locationReceiver = new LocationReceiver();

    public MyLocationPresenter(MyLocationActivity activity){
        this.activity = activity;
        myLocationUsecase = new MyLocationUsecase(activity);
        settingUsecase = new SettingUsecase(this.activity.getApplicationContext());

        categoryLocation = activity.getResources().getString(R.string.categoryLocation);
        categoryColdStart = activity.getResources().getString(R.string.categoryColdStart);
        categoryColdStop = activity.getResources().getString(R.string.categoryColdStop);
    }

    public void checkPermission(){
        myLocationUsecase.hasPermissions();
    }

    public void locationStart(){

        //TODO どの測位を行うかをSettingから読み込み、実行するServiceを選択する処理を追加する
        locationserviceIntent = new Intent(activity.getApplicationContext(),UebService.class);

        //TODO:とりあえずテスト用の適当数値を設定 あとで設定から読むように変える
        int count = 3;
        long timeout = 30;
        long interval = 30;
        int suplendwaittime = 3;
        int delassisttime = 3;
        locationserviceIntent.putExtra(activity.getResources().getString(R.string.settingCount),count);
        locationserviceIntent.putExtra(activity.getResources().getString(R.string.settingTimeout),timeout);
        locationserviceIntent.putExtra(activity.getResources().getString(R.string.settingInterval),interval);
        locationserviceIntent.putExtra(activity.getResources().getString(R.string.settingSuplEndWaitTime),suplendwaittime);
        locationserviceIntent.putExtra(activity.getResources().getString(R.string.settinDelAssistdataTime),delassisttime);
        activity.startService(locationserviceIntent);
        IntentFilter filter = new IntentFilter(activity.getResources().getString(R.string.locationUeb));
        activity.registerReceiver(locationReceiver,filter);
        activity.bindService(locationserviceIntent,serviceConnectionUeb ,Context.BIND_AUTO_CREATE);
    }

    public void locationStop(){
        activity.stopService(locationserviceIntent);
    }

    public void settingStart(){
        settingIntent = new Intent(activity.getApplicationContext(), SettingActivity.class);
        activity.startActivity(settingIntent);
    }

    public void startProgressDialog(String message){
        activity.startProgressDialog(message);
    }

    public void stopProgressDialog(){
        activity.stopProgressDialog();
    }

    public void showToast(String message){
        activity.showToast(message);
    }

    public class LocationReceiver extends BroadcastReceiver{
        Boolean isFix;
        double lattude, longitude, ttff;

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            receiveCategory = bundle.getString(activity.getResources().getString(R.string.category));

            if(receiveCategory.equals(categoryLocation)){
                isFix = bundle.getBoolean(activity.getResources().getString(R.string.TagisFix));
                lattude = bundle.getDouble(activity.getResources().getString(R.string.TagLat));
                longitude = bundle.getDouble(activity.getResources().getString(R.string.TagLong));
                ttff = bundle.getDouble(activity.getResources().getString(R.string.Tagttff));
                L.d("onReceive");
                L.d(isFix + "," + lattude + "," + longitude + "," + ttff );

            }else if(receiveCategory.equals(categoryColdStart)){
                L.d("ReceiceColdStart");
                startProgressDialog("アシストデータ削除中");
                showToast("アシストデータ削除中");
            }else if(receiveCategory.equals(categoryColdStop)){
                L.d("ReceiceColdStop");
                stopProgressDialog();
            }
        }
    }
}