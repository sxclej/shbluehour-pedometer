package com.today.step.lib;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;

import io.flutter.app.FlutterActivity;
import io.flutter.plugin.common.EventChannel;

/**
 * SensorsPlugin
 */
public class PedometerPlugin extends FlutterActivity implements EventChannel.StreamHandler {
  private static final String STEP_COUNT_CHANNEL_NAME =
          "pedometer.eventChannel";

  private static final int REFRESH_STEP_WHAT = 0;

  //循环取当前时刻的步数中间的间隔时间
  private long TIME_INTERVAL_REFRESH = 3000;

  private Handler mDelayHandler = new Handler(new TodayStepCounterCall());
  private int mStepSum;

  private ISportStepInterface iSportStepInterface;

  /**
   * Plugin registration.
   */
  public static void registerWith(Registrar registrar) {
    final EventChannel eventChannel =
            new EventChannel(registrar.messenger(), STEP_COUNT_CHANNEL_NAME);
    eventChannel.setStreamHandler(
            new PedometerPlugin(registrar.context(), Sensor.TYPE_STEP_COUNTER));
  }

  private SensorEventListener sensorEventListener;
  private final SensorManager sensorManager;
  private final Sensor sensor;

  @TargetApi(Build.VERSION_CODES.CUPCAKE)
  private PedometerPlugin(Context context, int sensorType) {
    sensorManager = (SensorManager) context.getSystemService(context.SENSOR_SERVICE);
    sensor = sensorManager.getDefaultSensor(sensorType);
  }

  @TargetApi(Build.VERSION_CODES.CUPCAKE)
  @Override
  public void onListen(Object arguments, EventChannel.EventSink events) {
    sensorEventListener = createSensorEventListener(events);
    sensorManager.registerListener(sensorEventListener, sensor, sensorManager.SENSOR_DELAY_FASTEST);
  }

  @TargetApi(Build.VERSION_CODES.CUPCAKE)
  @Override
  public void onCancel(Object arguments) {
    sensorManager.unregisterListener(sensorEventListener);
  }

  SensorEventListener createSensorEventListener(final EventChannel.EventSink events) {
    return new SensorEventListener() {
      @Override
      public void onAccuracyChanged(Sensor sensor, int accuracy) {
      }

      @TargetApi(Build.VERSION_CODES.CUPCAKE)
      @Override
      public void onSensorChanged(SensorEvent event) {
        int stepCount = (int) event.values[0];
        try {
          stepCount = iSportStepInterface.getCurrentTimeSportStep();
        } catch (RemoteException e) {
          e.printStackTrace();
        }
        events.success(stepCount);
      }
    };
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    //初始化计步模块
    TodayStepManager.startTodayStepService(getApplication());

    //开启计步Service，同时绑定Activity进行aidl通信
    Intent intent = new Intent(this, TodayStepService.class);
    startService(intent);
    bindService(intent, new ServiceConnection() {
      @Override
      public void onServiceConnected(ComponentName name, IBinder service) {
        //Activity和Service通过aidl进行通信
        iSportStepInterface = ISportStepInterface.Stub.asInterface(service);
        try {
          mStepSum = iSportStepInterface.getCurrentTimeSportStep();
          updateStepCount();
        } catch (RemoteException e) {
          e.printStackTrace();
        }
//        mDelayHandler.sendEmptyMessageDelayed(REFRESH_STEP_WHAT, TIME_INTERVAL_REFRESH);

      }

      @Override
      public void onServiceDisconnected(ComponentName name) {

      }
    }, Context.BIND_AUTO_CREATE);


  }


  class TodayStepCounterCall implements Handler.Callback {

    @Override
    public boolean handleMessage(Message msg) {
      switch (msg.what) {
        case REFRESH_STEP_WHAT: {
          //每隔500毫秒获取一次计步数据刷新UI
          if (null != iSportStepInterface) {
            int step = 0;
            try {
              step = iSportStepInterface.getCurrentTimeSportStep();
            } catch (RemoteException e) {
              e.printStackTrace();
            }
            if (mStepSum != step) {
              mStepSum = step;
              updateStepCount();
            }
          }
          mDelayHandler.sendEmptyMessageDelayed(REFRESH_STEP_WHAT, TIME_INTERVAL_REFRESH);

          break;
        }
      }
      return false;
    }
  }

  private void updateStepCount() {


  }

}
