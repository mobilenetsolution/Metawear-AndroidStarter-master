/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 *
 * IMPORTANT: Your use of this Software is limited to those specific rights
 * granted under the terms of a software license agreement between the user who
 * downloaded the software, his/her employer (which must be your employer) and
 * MbientLab Inc, (the "License").  You may not use this Software unless you
 * agree to abide by the terms of the License which can be found at
 * www.mbientlab.com/terms . The License limits your use, and you acknowledge,
 * that the  Software may not be modified, copied or distributed and can be used
 * solely and exclusively in conjunction with a MbientLab Inc, product.  Other
 * than for the foregoing purpose, you may not use, reproduce, copy, prepare
 * derivative works of, modify, distribute, perform, display or sell this
 * Software and/or its documentation for any purpose.
 *
 * YOU FURTHER ACKNOWLEDGE AND AGREE THAT THE SOFTWARE AND DOCUMENTATION ARE
 * PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTY OF MERCHANTABILITY, TITLE,
 * NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL
 * MBIENTLAB OR ITS LICENSORS BE LIABLE OR OBLIGATED UNDER CONTRACT, NEGLIGENCE,
 * STRICT LIABILITY, CONTRIBUTION, BREACH OF WARRANTY, OR OTHER LEGAL EQUITABLE
 * THEORY ANY DIRECT OR INDIRECT DAMAGES OR EXPENSES INCLUDING BUT NOT LIMITED
 * TO ANY INCIDENTAL, SPECIAL, INDIRECT, PUNITIVE OR CONSEQUENTIAL DAMAGES, LOST
 * PROFITS OR LOST DATA, COST OF PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY,
 * SERVICES, OR ANY CLAIMS BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY
 * DEFENSE THEREOF), OR OTHER SIMILAR COSTS.
 *
 * Should you have any questions regarding your right to use this Software,
 * contact MbientLab Inc, at www.mbientlab.com.
 */

package com.mbientlab.metawear.starter;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;
import android.widget.TextView;

import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.Bmp280Barometer;
import com.mbientlab.metawear.module.Bmp280Barometer.*;
import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.MultiChannelTemperature;
import com.mbientlab.metawear.module.Timer;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mbientlab.metawear.starter.Geoid.*;
import static java.lang.StrictMath.pow;

/**
 * A placeholder fragment containing a simple view.
 */
public class DeviceSetupActivityFragment extends Fragment implements ILocation, ServiceConnection {
    @Override
    public void updateDirectionOnObjects(final android.location.Location location) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                double alt = egm96Altitude(location.getAltitude(), location.getLatitude(), location.getLongitude());
                //double alt = locationHelper.getLocation().getAltitude();
                for (int j = 11; j > 0; j--) {               //save momentaneous pressure in array in position 0
                    altitude_h[j] = altitude_h[j - 1];
                }
                altitude_h[0] = alt;

                int cpt = 0;
                for (int j = 0; j <= 11; j++) {               //save momentaneous pressure in array in position 0
                    if (altitude_h[j] != 0.0d)
                        cpt++;
                }
                double sum = 0d;
                for (double d : altitude_h) sum += d;

                altAverage = 1.0d * sum / cpt;


                ((TextView) currentView.findViewById(R.id.gpsAltitude)).setText(String.format("GPS altitude= %.3fm",
                        altAverage));
                ((TextView) currentView.findViewById(R.id.gpsLatitude)).setText(String.format("Latitude= %.3fm", location.getLatitude()));
                ((TextView) currentView.findViewById(R.id.gpsLongitude)).setText(String.format("Longitude= %.3fm", location.getLongitude()));

            }
        });
    }

    public interface FragmentSettings {
        BluetoothDevice getBtDevice();
    }

    private MetaWearBoard mwBoard= null;
    //private Accelerometer accModule= null;
    Bmp280Barometer bmp280Module= null;
    private FragmentSettings settings;

    GPSTracker gpsTracker;

    private double gps_altitude=0.0f;
    private float [] values_pressure = new float[12];
    private float mean_pressure=0.0f;
    private volatile float mom_altitude=0.0f;
    private float delta_altitude=0.0f;
    private float delta_pressure=0.0f;
    private float [] delta_pressure_h = new float[12];
    private float delta_pressure_shorttime=0.0f;
    private float [] delta_pressure_shorttime_h = new float[12];
    private volatile float pressure_read=0.0f;
    private String [] values_forecast = new String[12];
    private double [] altitude_h = new double[12];

    private long timer = System.currentTimeMillis();
    private long timer2 = System.currentTimeMillis();
    private int forecast=2;

    private MultiChannelTemperature tempModule;
    private List<MultiChannelTemperature.Source> tempSources;
    private final int TIME_DELAY_PERIOD = 10000;
    private float currentTemperature=0.0f;
    private float currentAltitude=0.0f;
    double altAverage=0d;

    private View currentView;

    private LocationHelper locationHelper;

    private float [] WeatherPressArray = new float[180];
    private float Pressure_1st_5min;
    private float Pressure_1st_15min;
    private float Pressure_2nd_30min;
    private float Pressure_2nd_45min;
    private float Pressure_3rd_55min;
    private float Pressure_4th_90min;
    private float Pressure_5th_115min;
    private float Pressure_6th_150min;
    private float Pressure_7th_180min;
    private int weatherstatus;
    private int previousweatherstatus=0;
    private double dP_dt;
    private float Weather_change;
    private int Minutes = 0;
    private int weather_cntr = 0;
    private boolean pressure_second_round_flag = false;
    private boolean newForecast = false;
    private static Map<Integer, Integer> dicPressureADC;
    static {
        Map<Integer, Integer> aMap = new HashMap<Integer, Integer>();
        aMap.put(50, 0);
        aMap.put(51, 15);
        aMap.put(52, 31);
        aMap.put(53, 47);
        aMap.put(54, 62);
        aMap.put(55, 78);
        aMap.put(56, 94);
        aMap.put(57, 110);
        aMap.put(58, 125);
        aMap.put(59, 141);
        aMap.put(60, 157);
        aMap.put(61, 173);
        aMap.put(62, 188);
        aMap.put(63, 204);
        aMap.put(64, 220);
        aMap.put(65, 236);
        aMap.put(66, 251);
        aMap.put(67, 267);
        aMap.put(68, 283);
        aMap.put(69, 299);
        aMap.put(70, 314);
        aMap.put(71, 330);
        aMap.put(72, 346);
        aMap.put(73, 361);
        aMap.put(74, 377);
        aMap.put(75, 393);
        aMap.put(76, 409);
        aMap.put(77, 424);
        aMap.put(78, 440);
        aMap.put(79, 456);
        aMap.put(80, 472);
        aMap.put(81, 487);
        aMap.put(82, 503);
        aMap.put(83, 519);
        aMap.put(84, 535);
        aMap.put(85, 550);
        aMap.put(86, 566);
        aMap.put(87, 582);
        aMap.put(88, 598);
        aMap.put(89, 613);
        aMap.put(90, 629);
        aMap.put(91, 645);
        aMap.put(92, 661);
        aMap.put(93, 676);
        aMap.put(94, 692);
        aMap.put(95, 708);
        aMap.put(96, 723);
        aMap.put(97, 739);
        aMap.put(98, 755);
        aMap.put(99, 771);
        aMap.put(100,786);
        aMap.put(101,802);
        aMap.put(102,818);
        aMap.put(103,834);
        aMap.put(104,849);
        aMap.put(105,865);
        aMap.put(106,881);
        aMap.put(107,897);
        aMap.put(108,912);
        aMap.put(109,928);
        aMap.put(110,944);
        aMap.put(111,960);
        aMap.put(112,975);
        aMap.put(113,991);
        aMap.put(114,1007);
        aMap.put(115,1022);
        dicPressureADC = Collections.unmodifiableMap(aMap);
    }

    public DeviceSetupActivityFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Activity owner= getActivity();
        if (!(owner instanceof FragmentSettings)) {
            throw new ClassCastException("Owning activity must implement the FragmentSettings interface");
        }

        settings= (FragmentSettings) owner;
        owner.getApplicationContext().bindService(new Intent(owner, MetaWearBleService.class), this, Context.BIND_AUTO_CREATE);

        //locationHelper = new LocationHelper(getActivity().getApplicationContext());
        locationHelper = new LocationHelper(getActivity().getApplicationContext(), this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        ///< Unbind the service when the activity is destroyed
        getActivity().getApplicationContext().unbindService(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        locationHelper.register();
    }

    @Override
    public void onPause() {
        super.onPause();

        locationHelper.unregister();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setRetainInstance(true);

        //gpsTracker = new GPSTracker(getActivity().getApplicationContext());

        boolean result = init();

        return inflater.inflate(R.layout.fragment_device_setup, container, false);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mwBoard= ((MetaWearBleService.LocalBinder) service).getMetaWearBoard(settings.getBtDevice());
        ready();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        currentView = view;
        view.findViewById(R.id.acc_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //gps_altitude = gpsTracker.getAltitude();
                init_forecast(0);

                //nxp
                timer2 = System.currentTimeMillis();

                bmp280Module.routeData().fromPressure().stream("pressure_stream").commit()
                //accModule.routeData().fromAxes().stream("acc_stream").commit()
                        .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                            @Override
                            public void success(RouteManager result) {
                                /*result.subscribe("acc_stream", new RouteManager.MessageHandler() {
                                    @Override
                                    public void process(Message msg) {
                                        Log.i("tutorial", msg.getData(CartesianFloat.class).toString());
                                    }
                                });

                                accModule.enableAxisSampling();
                                accModule.start();*/
                                result.subscribe("pressure_stream", new RouteManager.MessageHandler() {
                                    @Override
                                    public void process(final Message msg) {
                                        String forecastToDisplay = "";

                                        //nxp comment
                                        //if (timer2 > System.currentTimeMillis()) timer2 = System.currentTimeMillis();
                                        // approximately every  3 minutes  or so, make a new prediction
                                        //nxp
                                        //if (System.currentTimeMillis() - timer2 > 180000) {
                                        if (System.currentTimeMillis() - timer2 > 60000) {
                                            timer2 = System.currentTimeMillis(); // reset the timer
                                            pressure_read = msg.getData(Float.class);
                                            ////if (GPS.fix) {
                                            //    gps_altitude = gpsTracker.getAltitude();
                                            ////}
                                            //forecast = makeforecast((float) altAverage);
                                            //nxp
                                            //forecast = makeforecast2(20f);
                                            forecast = makeforecast4(20f);
                                            //forecast = makeforecast();

                                            if(newForecast) {
                                                for (int j = 11; j > 0; j--) {               //save momentaneous forecast in array in position 0
                                                    values_forecast[j] = values_forecast[j - 1];
                                                }

                                                //nxp
                                                /*switch (forecast) {
                                                    case 1:
                                                        forecastToDisplay = "Ensoleillement (court terme)";//"soleil";
                                                        break;
                                                    //drawsun();
                                                    //return;
                                                    case 2:
                                                        forecastToDisplay = "Nuageux (ensoleillement long terme)";//"nuage";
                                                        break;
                                                    //drawcloud();
                                                    //return;
                                                    case 3:
                                                        forecastToDisplay = "Veille tempête, fort vent"; //  tempête
                                                        break;
                                                    //drawstorm();
                                                    //return;
                                                    case 4:
                                                        forecastToDisplay = "Pluie";
                                                        break;
                                                    //drawrain();
                                                    //return;
                                                    case 5:
                                                        //displayForecast(((TextView)view.findViewById(R.id.forecast)), "soleil ou nuage");
                                                        // break;
                                                        if (currentTemperature > 15) {
                                                            forecastToDisplay = "Aucun changement (soleil)";
                                                            //drawsun();
                                                            //    return;
                                                        }
                                                        else {
                                                            forecastToDisplay = "Aucun changement (nuage)";
                                                            //drawcloud();
                                                            //    return;
                                                        }
                                                        //return;
                                                        break;
                                                    case 6:
                                                        forecastToDisplay = "Tempête, fort vent";
                                                        break;
                                                    case 7:
                                                        forecastToDisplay = "Soleil / nuage";
                                                        break;
                                                    default:
                                                        forecastToDisplay = "n/d";
                                                        break;
                                                    //drawsun();
                                                    //return;
                                                }*/

                                                if (weatherstatus == 0) {
                                                    forecastToDisplay = "Stable Weather Patt";
                                                    if (previousweatherstatus != 0)
                                                    {
                                                        if (previousweatherstatus == 1) {
                                                            forecastToDisplay = forecastToDisplay + "(Clear/Sunny)";
                                                        }
                                                        if (previousweatherstatus == 2) {
                                                            forecastToDisplay = forecastToDisplay + "(Cloudy/Rain)";
                                                        }
                                                        if (previousweatherstatus == 3) {
                                                            forecastToDisplay = forecastToDisplay + "(Not Stable)";
                                                        }
                                                        if (previousweatherstatus == 4) {
                                                            forecastToDisplay = forecastToDisplay + "(Thunderstorm)";
                                                        }
                                                    }
                                                }
                                                if (weatherstatus == 1) {
                                                    forecastToDisplay = "Slowly rising Good Weather,Clear/Sunny";
                                                }
                                                if (weatherstatus == 2) {
                                                    forecastToDisplay = "Slowly falling L-Pressure,Cloudy/Rain";
                                                }
                                                if (weatherstatus == 3) {
                                                    forecastToDisplay = "Quickly rising H-Press,Not Stable";
                                                }
                                                if (weatherstatus == 4) {
                                                    forecastToDisplay = "Quickly falling L-Press,Thunderstorm)";
                                                }
                                                if (weatherstatus == 5) {
                                                    forecastToDisplay = "Unknown (More Time)";
                                                }

                                                values_forecast[0] = forecastToDisplay;
                                                //displayForecast(((TextView)view.findViewById(R.id.forecast)), forecastToDisplay, ((TextView)view.findViewById(R.id.f1)));
                                            }
                                        }

                                        final String finalForecastToDisplay = forecastToDisplay;
                                        getActivity().runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                ((TextView)view.findViewById(R.id.pressure)).setText(String.format("Pressure= %.3fPa",
                                                        msg.getData(Float.class)));

                                                if(finalForecastToDisplay !="")
                                                {
                                                    ((TextView)view.findViewById(R.id.forecast)).setText(finalForecastToDisplay);
                                                    String s = java.util.Arrays.toString(values_forecast);
                                                    ((TextView)view.findViewById(R.id.f1)).setText(s);
                                                    s = java.util.Arrays.toString(values_pressure);
                                                    ((TextView) view.findViewById(R.id.p1)).setText(s);
                                                    s = java.util.Arrays.toString(delta_pressure_h);
                                                    ((TextView) view.findViewById(R.id.dph)).setText(s);
                                                    s = java.util.Arrays.toString(delta_pressure_shorttime_h);
                                                    ((TextView) view.findViewById(R.id.dpsth)).setText(s);
                                                }

                                            }
                                        });

                                        Log.i("MainActivity", String.format("Pressure= %.3fPa",
                                                msg.getData(Float.class)));
                                    }
                                });
                                bmp280Module.start();
                            }
                        });

                bmp280Module.routeData().fromAltitude().stream("altitude_stream").commit()
                        .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                            @Override
                            public void success(RouteManager result) {
                                result.subscribe("altitude_stream", new RouteManager.MessageHandler() {
                                    @Override
                                    public void process(final Message msg) {

                                        currentAltitude = msg.getData(Float.class);
                                        getActivity().runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                ((TextView)view.findViewById(R.id.altitude)).setText(String.format("Altitude= %.3fm",
                                                        currentAltitude));

                                                //double alt = egm96Altitude(locationHelper.getLocation().getAltitude(),locationHelper.getLocation().getLatitude(),locationHelper.getLocation().getLongitude());
                                                //double alt = locationHelper.getLocation().getAltitude();
                                                //for (int j=11;j>0;j--) {               //save momentaneous pressure in array in position 0
                                                //    altitude_h[j] = altitude_h[j-1];
                                                //}
                                                //altitude_h[0]=alt;

                                                //int cpt = 0;
                                                //for (int j=0;j<=11;j++) {               //save momentaneous pressure in array in position 0
                                                //    if (altitude_h[j] != 0.0d)
                                                //        cpt++;
                                                //}
                                                //double sum = 0d;
                                                //for (double d : altitude_h) sum += d;

                                                //altAverage = 1.0d * sum / cpt;
                                                /*final double[] alt = {0};
                                                Thread thread = new Thread(new Runnable()
                                                {
                                                    @Override
                                                    public void run()
                                                    {
                                                        try
                                                        {
                                                            try {
                                                                alt[0] = getAltitudeFromService(locationHelper.getLocation().getLongitude(),locationHelper.getLocation().getLatitude());
                                                            } catch (IOException e) {
                                                                e.printStackTrace();
                                                            }
                                                            ((TextView)view.findViewById(R.id.gpsAltitude)).setText(String.format("GPS= %.3fm",
                                                                    alt[0]));
                                                        }
                                                        catch (Exception e)
                                                        {
                                                            e.printStackTrace();
                                                        }
                                                    }
                                                });

                                                thread.start();*/

                                                //((TextView)view.findViewById(R.id.gpsAltitude)).setText(String.format("GPS altitude= %.3fm",
                                                //        altAverage));
                                                //((TextView)view.findViewById(R.id.gpsLatitude)).setText(String.format("Latitude= %.3fm",locationHelper.getLocation().getLatitude()));
                                                //((TextView)view.findViewById(R.id.gpsLongitude)).setText(String.format("Longitude= %.3fm",locationHelper.getLocation().getLongitude()));


                                                /*if(gpsTracker.canGetLocation()) {
                                                    //double alt = egm96Altitude(gpsTracker.getAltitude(),gpsTracker.getLatitude(),gpsTracker.getLongitude());
                                                    double alt = gpsTracker.getAltitude();
                                                    ((TextView)view.findViewById(R.id.gpsAltitude)).setText(String.format("GPS= %.3fm",
                                                            alt));
                                                    ((TextView)view.findViewById(R.id.gpsLatitude)).setText(String.format("Latitude= %.3fm",gpsTracker.getLatitude()));
                                                    ((TextView)view.findViewById(R.id.gpsLongitude)).setText(String.format("Longitude= %.3fm",gpsTracker.getLongitude()));
                                                }
                                                else
                                                {
                                                    ((TextView)view.findViewById(R.id.gpsAltitude)).setText("");
                                                    ((TextView)view.findViewById(R.id.gpsLatitude)).setText("");
                                                    ((TextView)view.findViewById(R.id.gpsLongitude)).setText("");
                                                }*/

                                            }
                                        });

                                        Log.i("MainActivity", String.format("Altitude= %.3fm",
                                                msg.getData(Float.class)));
                                    }
                                });
                                bmp280Module.enableAltitudeSampling();
                                bmp280Module.start();
                            }
                        });

                //MultiChannelTemperature mcTempModuleTemp =null;
                try {
                    tempModule = mwBoard.getModule(MultiChannelTemperature.class);
                } catch (UnsupportedModuleException e) {
                    e.printStackTrace();
                }
                //final MultiChannelTemperature mcTempModule = mcTempModuleTemp;
                tempSources = tempModule.getSources();
                // Route data from the nrf soc temperature sensor
                tempModule.routeData()
                        .fromSource(tempSources.get(MultiChannelTemperature.MetaWearProChannel.BMP_280)).stream("temp_nrf_stream")
                        .commit().onComplete(temperatureHandler);
                        //.commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                    /*@Override
                    public void sucess(RouteManager result) {
                        result.subscribe("temp_nrf_stream", new RouteManager.MessageHandler() {
                            @Override
                            public void process(final Message msg) {
                                Log.i("MainActivity", String.format("Ext thermistor: %.3fC",
                                        msg.getData(Float.class)));

                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ((TextView) view.findViewById(R.id.temperature)).setText(String.format("Temperature= %.3fPa",
                                                msg.getData(Float.class)));
                                    }
                                });
                            }
                        });

                        // Read temperature from the NRF soc chip
                        tempModule.readTemperature(tempSources.get(MultiChannelTemperature.MetaWearProChannel.BMP_280));
                    }
                });*/
            }
        });
        view.findViewById(R.id.acc_stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bmp280Module.stop();
                //accModule.stop();
                //accModule.disableAxisSampling();
                mwBoard.removeRoutes();

                //gpsTracker.stopUsingGPS();
            }
        });
    }

    private final AsyncOperation.CompletionHandler<RouteManager> temperatureHandler = new AsyncOperation.CompletionHandler<RouteManager>() {
        @Override
        public void success(RouteManager result) {

            result.subscribe("temp_nrf_stream", new RouteManager.MessageHandler() {
                @Override
                public void process(final Message msg) {
                    currentTemperature = msg.getData(Float.class);
                    Log.i("MainActivity", String.format("Ext thermistor: %.3fC",
                            msg.getData(Float.class)));

                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ((TextView) currentView.findViewById(R.id.temperature)).setText(String.format("Temperature= %.3fC",
                                    msg.getData(Float.class)));
                        }
                    });
                }
            });

            // Read temperature from the NRF soc chip
            tempModule.readTemperature(tempSources.get(MultiChannelTemperature.MetaWearProChannel.BMP_280));

            /*result.setLogMessageHandler("mystream", loggingMessageHandler);
            //editor.putInt(mwBoard.getMacAddress() + "_log_id", result.id());
            //editor.apply();
            //editor.commit();

            // Read temperature from the NRF soc chip
            try {
                AsyncOperation<Timer.Controller> taskResult = mwBoard.getModule(Timer.class)
                        .scheduleTask(new Timer.Task() {
                            @Override
                            public void commands() {
                                tempModule.readTemperature(tempModule.getSources().get(MultiChannelTemperature.MetaWearProChannel.BMP_280));
                            }
                        }, TIME_DELAY_PERIOD, false);
                taskResult.onComplete(new AsyncOperation.CompletionHandler<Timer.Controller>() {
                    @Override
                    public void success(Timer.Controller result) {
                        // start executing the task
                        result.start();
                    }
                });
            }catch (UnsupportedModuleException e){
                Log.e("Temperature Fragment", e.toString());
            }*/

        }
    };

    private final RouteManager.MessageHandler loggingMessageHandler =  new RouteManager.MessageHandler() {
        @Override
        public void process(Message msg) {
            Log.i("MainActivity", String.format("Ext thermistor: %.3fC",

                    msg.getData(Float.class)));
            //java.sql.Date date = new java.sql.Date(msg.getTimestamp().getTimeInMillis());
            //TemperatureSample sample = new TemperatureSample(date,  msg.getData(Float.class).longValue(), mwBoard.getMacAddress());
            //sample.save();
        }
    };

    /**
     * Called when the app has reconnected to the board
     */
    public void reconnected() { }

    /**
     * Called when the mwBoard field is ready to be used
     */
    public void ready() {
        try {
            //accModule = mwBoard.getModule(Accelerometer.class);
            bmp280Module= mwBoard.getModule(Bmp280Barometer.class);
            // Set the output data rate to 25Hz or closet valid value
            //accModule.setOutputDataRate(25.f);
            bmp280Module.configure()
                    .setFilterMode(FilterMode.AVG_4)
                    .setPressureOversampling(OversamplingMode.LOW_POWER)
                    .setStandbyTime(StandbyTime.TIME_4000)
                    .commit();
        } catch (UnsupportedModuleException e) {
            Snackbar.make(getActivity().findViewById(R.id.device_setup_fragment), e.getMessage(),
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    /**
     * convert the wgs84 altitude to the egm96 altitude at the provided location.
     */
    private double egm96Altitude(double wgs84altitude, double latitude, double longitude) {
        return wgs84altitude + Geoid.getOffset(new Location(latitude, longitude));
    }

    private void init_forecast(double gps_altitude) {
        mean_pressure= pressure_read;

        //convert pressure to altitude
        mom_altitude= (float) ((288.15/0.0065)*(1-Math.pow((mean_pressure/101325),0.1903)));

        //delta_altitude = pressurealtitude - gpsaltitude
        delta_altitude = (float) (mom_altitude - gps_altitude);

        //convert delta_altitude to pressure
        mean_pressure = (float) (101325*Math.pow((1-((0.0065*delta_altitude)/288.15)),5.255));

        for (int j=0;j<12;j++) {               //fill the array with the initial value
            values_pressure[j] = mean_pressure;
        }

        for (int j=0;j<12;j++) {               //fill the array with the initial value
            values_forecast[j] = "";
        }
    }

    int makeforecast(float gps_altitude) {

        mean_pressure= pressure_read;

        //convert pressure to altitude
        mom_altitude= (float) ((288.15/0.0065)*(1-Math.pow((mean_pressure/101325),0.1903)));

        //delta_altitude = pressurealtitude - gpsaltitude
        delta_altitude = mom_altitude - gps_altitude;
        //delta_altitude = mom_altitude - currentAltitude;

        //convert delta_altitude to pressure
        mean_pressure = (float) (101325*Math.pow((1-((0.0065*delta_altitude)/288.15)),5.255));

        for (int j=11;j>0;j--) {               //save momentaneous pressure in array in position 0
            values_pressure[j] = values_pressure[j-1];
        }
        values_pressure[0]=mean_pressure;

        //String s = java.util.Arrays.toString(values_pressure);
        //((TextView) currentView.findViewById(R.id.p1)).setText(s);

        //calculate difference between old pressure and momentaneous pressure
        delta_pressure = values_pressure[0] - values_pressure[11];
        for (int j=11;j>0;j--) {               //save momentaneous pressure in array in position 0
            delta_pressure_h[j] = delta_pressure_h[j-1];
        }
        delta_pressure_h[0]=delta_pressure;

        delta_pressure_shorttime = values_pressure [0] - values_pressure[1];
        for (int j=11;j>0;j--) {               //save momentaneous pressure in array in position 0
            delta_pressure_shorttime_h[j] = delta_pressure_shorttime_h[j-1];
        }
        delta_pressure_shorttime_h[0]=delta_pressure_shorttime;

        if (delta_pressure_shorttime < -30) return 3; // strong wind, storm (early warning)
        else {
            if (delta_pressure > 100) return 1; // weather gets better (short term)
            else if ((delta_pressure <= 100) && (delta_pressure >= 20)) return 2; // weather gets better (long term)

            else if (delta_pressure < -200) return 6; // strong wind, storm

            else if ((delta_pressure >= -200) && (delta_pressure <= -20)) return 4; // bad weather

            else if ((delta_pressure < 20) && (delta_pressure > -20)) return 5; // weather will remain the same
        }
        return 0;
    }

    int makeforecast2(float gps_altitude) {

        float pWeather = (float) (101.3 * Math.exp(((20f))/(-7900)));

        mean_pressure= pressure_read/1000;

        float simpleweatherdiff = mean_pressure-pWeather;
        if (simpleweatherdiff >0.25)
            return 2; //Sun Symbol
        if ((simpleweatherdiff <=0.25) || (simpleweatherdiff >=(-0.25)))
            return 7; //Sun/Cloud Symbol
        if (simpleweatherdiff <(-0.25))
            return 4; //Rain Symbol


        //convert pressure to altitude
        //mom_altitude= (float) ((288.15/0.0065)*(1-Math.pow((mean_pressure/101325),0.1903)));

        //delta_altitude = pressurealtitude - gpsaltitude
       // delta_altitude = mom_altitude - gps_altitude;
        //delta_altitude = mom_altitude - currentAltitude;

        //convert delta_altitude to pressure
        //mean_pressure = (float) (101325*Math.pow((1-((0.0065*delta_altitude)/288.15)),5.255));

        for (int j=11;j>0;j--) {               //save momentaneous pressure in array in position 0
            values_pressure[j] = values_pressure[j-1];
        }
        values_pressure[0]=pWeather;

        String s = java.util.Arrays.toString(values_pressure);
        ((TextView) currentView.findViewById(R.id.p1)).setText(s);

        //calculate difference between old pressure and momentaneous pressure
        delta_pressure = values_pressure[0] - values_pressure[11];
        for (int j=11;j>0;j--) {               //save momentaneous pressure in array in position 0
            delta_pressure_h[j] = delta_pressure_h[j-1];
        }
        delta_pressure_h[0]=simpleweatherdiff;


        return 0;
    }

    int makeforecast3(float gps_altitude) {
        newForecast = false;
        //float pWeather = (float) (101.3 * Math.exp(((20f))/(-7900)));

        //mean_pressure= pressure_read/1000;

        double adcCount = getAdcCount();

        Minutes = Minutes +1;
        //PTGD_PTGD3 ^= 1; //D1 RED LED
        if (weather_cntr > 180)
            weather_cntr =6;
        WeatherPressArray[weather_cntr] = (float) adcCount; //pressure_read/1000;
        weather_cntr++;
        if (weather_cntr ==5){
            //Avg pressure in first 5 min, value averaged from 0 to 5 min.
            Pressure_1st_5min = ((WeatherPressArray[0]+WeatherPressArray[1]+WeatherPressArray[2]+WeatherPressArray[3]+WeatherPressArray[4])/5);
        }
        if (weather_cntr ==35){
            newForecast = true;
            //Avg pressure in 30 min, value averaged from 0 to 5 min.
            Pressure_2nd_30min = ((WeatherPressArray[30]+WeatherPressArray[31]+WeatherPressArray[32]+WeatherPressArray[33]+WeatherPressArray[34])/5);
            Weather_change = (Pressure_2nd_30min - Pressure_1st_5min);
            if(pressure_second_round_flag ==false) //first time initial 3 hour
                dP_dt = ((65.0/1023.0)*2*Weather_change); //note this is for t = 0.5hour

            if(pressure_second_round_flag ==true) //more than inital 3 hour.
                dP_dt = (((65.0/1023.0)*Weather_change)/1.5); //divide by 1.5 as this is the difference in time from 0 value.
        }
        if (weather_cntr ==60){
            newForecast = true;
            //Avg pressure at end of the hour, value averaged from 0 to 5 min.
            Pressure_3rd_55min = ((WeatherPressArray[55]+WeatherPressArray[56]+WeatherPressArray[57]+WeatherPressArray[58]+WeatherPressArray[59])/5);
            Weather_change = (Pressure_3rd_55min - Pressure_1st_5min);
            if(pressure_second_round_flag ==false) //first time initial 3 hour
                dP_dt = ((65.0/1023.0)*Weather_change); //note this is for t = 1 hour

            if(pressure_second_round_flag ==true) //more than initial 3 hour.
                dP_dt = (((65.0/1023.0)*Weather_change)/2); //divide by 2 as this is the difference in time from 0 value
        }

        if (weather_cntr ==95){
            newForecast = true;
            //Avg pressure at end of the hour, value averaged from 0 to 5 min.
            Pressure_4th_90min = ((WeatherPressArray[90]+WeatherPressArray[91]+WeatherPressArray[92]+WeatherPressArray[93]+WeatherPressArray[94])/5);
            Weather_change = (Pressure_4th_90min - Pressure_1st_5min);
            if(pressure_second_round_flag ==false) //first time initial 3 hour
                dP_dt = (((65.0/1023.0)*Weather_change)/1.5); //note this is for t = 1.5 hour
            if(pressure_second_round_flag ==true) //more than initial 3 hour.
                dP_dt = (((65.0/1023.0)*Weather_change)/2.5); //divide by 2.5 as this is the difference in time from 0 value
        }

        if (weather_cntr ==120){
            newForecast = true;
            //Avg pressure at end of the hour, value averaged from 0 to 5 min.
            Pressure_5th_115min = ((WeatherPressArray[115]+WeatherPressArray[116]+WeatherPressArray[117]+WeatherPressArray[118]+WeatherPressArray[119])/5);
            Weather_change = (Pressure_5th_115min - Pressure_1st_5min);
            if(pressure_second_round_flag ==false) //first time initial 3 hour
                dP_dt = (((65.0/1023.0)*Weather_change)/2); //note this is for t = 2 hour
            if(pressure_second_round_flag ==true) //more than initial 3 hour.
                dP_dt = (((65.0/1023.0)*Weather_change)/3); //divide by 3 as this is the difference in time from 0 value
        }
        if (weather_cntr ==155){
            newForecast = true;
            //Avg pressure at end of the hour, value averaged from 0 to 5 min.
            Pressure_6th_150min = ((WeatherPressArray[150]+WeatherPressArray[151]+WeatherPressArray[152]+WeatherPressArray[153]+WeatherPressArray[154])/5);
            Weather_change = (Pressure_6th_150min - Pressure_1st_5min);
            if(pressure_second_round_flag ==false) //first time initial 3 hour
                dP_dt = (((65.0/1023.0)*Weather_change)/2.5); //note this is for t = 2.5 hour
            if(pressure_second_round_flag ==true) //more than initial 3 hour.
                dP_dt = (((65.0/1023.0)*Weather_change)/3.5); //divide by 3.5 as this is the difference in time from 0 value
        }
        if (weather_cntr ==180){
            newForecast = true;
            //Avg pressure at end of the hour, value averaged from 0 to 5 min.
            Pressure_7th_180min = ((WeatherPressArray[175]+WeatherPressArray[176]+WeatherPressArray[177]+WeatherPressArray[178]+WeatherPressArray[179])/5);
            Weather_change = (Pressure_7th_180min - Pressure_1st_5min);
            if(pressure_second_round_flag ==false) //first time initial 3 hour
                dP_dt = (((65.0/1023.0)*Weather_change)/3); //note this is for t = 3 hour
            if(pressure_second_round_flag ==true) //more than initial 3 hour.
                dP_dt = (((65.0/1023.0)*Weather_change)/4); //divide by 4 as this is the difference in time from 0 value
            Pressure_1st_5min = Pressure_6th_150min; //Equating the pressure at 0 to the pressure at 2 hour after 3 hours have past.
            pressure_second_round_flag =true; // flag to let you know that this is on the past 3 hour mark. Initialized to 0 outside main loop.
        }

        if(newForecast)
        {
            delta_pressure = (float) dP_dt;
            for (int j=11;j>0;j--) {               //save momentaneous pressure in array in position 0
                delta_pressure_h[j] = delta_pressure_h[j-1];
            }
            delta_pressure_h[0]= (float) dP_dt;
        }

        previousweatherstatus = weatherstatus;
        if ((dP_dt > (-0.05)) && (dP_dt <0.05))
            weatherstatus = 0; // Stable weather
        if ((dP_dt >0.05) && (dP_dt <0.25))
            weatherstatus = 1; // Slowly rising HP stable good weather
        if ((dP_dt > (-0.25)) && (dP_dt <(-0.05)))
            weatherstatus = 2; // Slowly falling Low Pressure System, stable rainy weather
        if (dP_dt > 0.25)
            weatherstatus = 3; // Quickly rising HP, not stable weather
        if (dP_dt < (-0.25))
            weatherstatus = 4; // Quickly falling LP, Thunderstorm, not stable
        if ((weather_cntr <35) && (pressure_second_round_flag !=true)) //if time is less than 35 min on the first 3 hour interval.
            weatherstatus =5; //Unknown, more TIME needed
        return weatherstatus;

        //Tmr2ms8b_weather = 29500; //Sampling every minute


        /*float simpleweatherdiff = mean_pressure-pWeather;
        if (simpleweatherdiff >0.25)
            return 2; //Sun Symbol
        if ((simpleweatherdiff <=0.25) || (simpleweatherdiff >=(-0.25)))
            return 7; //Sun/Cloud Symbol
        if (simpleweatherdiff <(-0.25))
            return 4; //Rain Symbol


        //convert pressure to altitude
        //mom_altitude= (float) ((288.15/0.0065)*(1-Math.pow((mean_pressure/101325),0.1903)));

        //delta_altitude = pressurealtitude - gpsaltitude
        // delta_altitude = mom_altitude - gps_altitude;
        //delta_altitude = mom_altitude - currentAltitude;

        //convert delta_altitude to pressure
        //mean_pressure = (float) (101325*Math.pow((1-((0.0065*delta_altitude)/288.15)),5.255));

        for (int j=11;j>0;j--) {               //save momentaneous pressure in array in position 0
            values_pressure[j] = values_pressure[j-1];
        }
        values_pressure[0]=pWeather;

        String s = java.util.Arrays.toString(values_pressure);
        ((TextView) currentView.findViewById(R.id.p1)).setText(s);

        //calculate difference between old pressure and momentaneous pressure
        delta_pressure = values_pressure[0] - values_pressure[11];
        for (int j=11;j>0;j--) {               //save momentaneous pressure in array in position 0
            delta_pressure_h[j] = delta_pressure_h[j-1];
        }
        delta_pressure_h[0]=simpleweatherdiff;


        return 0;*/
    }

    int makeforecast4(float gps_altitude) {
        newForecast = false;

        double adcCount = getAdcCount();

        Minutes = Minutes +1;
        //PTGD_PTGD3 ^= 1; //D1 RED LED
        if (weather_cntr > 120)
        {
            weather_cntr =6;
        }

        WeatherPressArray[weather_cntr] = (float) adcCount; //pressure_read/1000;
        weather_cntr++;

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) currentView.findViewById(R.id.txtTimer)).setText(String.valueOf(weather_cntr));
            }
        });

        if (weather_cntr ==5){
            newForecast = true;
            //Avg pressure in first 5 min, value averaged from 0 to 5 min.
            Pressure_1st_5min = ((WeatherPressArray[0]+WeatherPressArray[1]+WeatherPressArray[2]+WeatherPressArray[3]+WeatherPressArray[4])/5);
        }
        if (weather_cntr ==20){
            newForecast = true;
            //Avg pressure in 20 min, value averaged from 0 to 5 min.
            Pressure_1st_15min = ((WeatherPressArray[15]+WeatherPressArray[16]+WeatherPressArray[17]+WeatherPressArray[18]+WeatherPressArray[19])/5);
            Weather_change = (Pressure_1st_15min - Pressure_1st_5min);
            if(pressure_second_round_flag ==false) //first time initial 3 hour
                dP_dt = ((65.0/1023.0)*4*Weather_change); //note this is for t = 0.25hour

            if(pressure_second_round_flag ==true) //more than inital 3 hour.
                dP_dt = (((65.0/1023.0)*Weather_change)/1.25); //divide by 1.25 as this is the difference in time from 0 value.
        }
        if (weather_cntr ==35){
            newForecast = true;
            //Avg pressure in 30 min, value averaged from 0 to 5 min.
            Pressure_2nd_30min = ((WeatherPressArray[30]+WeatherPressArray[31]+WeatherPressArray[32]+WeatherPressArray[33]+WeatherPressArray[34])/5);
            Weather_change = (Pressure_2nd_30min - Pressure_1st_5min);
            if(pressure_second_round_flag ==false) //first time initial 3 hour
                dP_dt = ((65.0/1023.0)*2*Weather_change); //note this is for t = 0.5hour

            if(pressure_second_round_flag ==true) //more than inital 3 hour.
                dP_dt = (((65.0/1023.0)*Weather_change)/1.5); //divide by 1.5 as this is the difference in time from 0 value.
        }
        if (weather_cntr ==45){
            newForecast = true;
            //Avg pressure in 40 min, value averaged from 0 to 5 min.
            Pressure_2nd_45min = ((WeatherPressArray[40]+WeatherPressArray[41]+WeatherPressArray[42]+WeatherPressArray[43]+WeatherPressArray[44])/5);
            Weather_change = (Pressure_2nd_45min - Pressure_1st_5min);
            if(pressure_second_round_flag ==false) //first time initial 3 hour
                dP_dt = ((65.0/1023.0)*1.5*Weather_change); //note this is for t = 0.5hour

            if(pressure_second_round_flag ==true) //more than inital 3 hour.
                dP_dt = (((65.0/1023.0)*Weather_change)/1.75); //divide by 1.5 as this is the difference in time from 0 value.
        }
        if (weather_cntr ==60){
            newForecast = true;
            //Avg pressure at end of the hour, value averaged from 0 to 5 min.
            Pressure_3rd_55min = ((WeatherPressArray[55]+WeatherPressArray[56]+WeatherPressArray[57]+WeatherPressArray[58]+WeatherPressArray[59])/5);
            Weather_change = (Pressure_3rd_55min - Pressure_1st_5min);
            if(pressure_second_round_flag ==false) //first time initial 3 hour
                dP_dt = ((65.0/1023.0)*Weather_change); //note this is for t = 1 hour

            if(pressure_second_round_flag ==true) //more than initial 3 hour.
                dP_dt = (((65.0/1023.0)*Weather_change)/2); //divide by 2 as this is the difference in time from 0 value
        }

        if (weather_cntr ==95){
            newForecast = true;
            //Avg pressure at end of the hour, value averaged from 0 to 5 min.
            Pressure_4th_90min = ((WeatherPressArray[90]+WeatherPressArray[91]+WeatherPressArray[92]+WeatherPressArray[93]+WeatherPressArray[94])/5);
            Weather_change = (Pressure_4th_90min - Pressure_1st_5min);
            if(pressure_second_round_flag ==false) //first time initial 3 hour
                dP_dt = (((65.0/1023.0)*Weather_change)/1.5); //note this is for t = 1.5 hour
            if(pressure_second_round_flag ==true) //more than initial 3 hour.
                dP_dt = (((65.0/1023.0)*Weather_change)/2.5); //divide by 2.5 as this is the difference in time from 0 value
        }

        if (weather_cntr ==120){
            newForecast = true;
            //Avg pressure at end of the hour, value averaged from 0 to 5 min.
            Pressure_5th_115min = ((WeatherPressArray[115]+WeatherPressArray[116]+WeatherPressArray[117]+WeatherPressArray[118]+WeatherPressArray[119])/5);
            Weather_change = (Pressure_5th_115min - Pressure_1st_5min);
            if(pressure_second_round_flag ==false) //first time initial 3 hour
                dP_dt = (((65.0/1023.0)*Weather_change)/2); //note this is for t = 2 hour
            if(pressure_second_round_flag ==true) //more than initial 3 hour.
                dP_dt = (((65.0/1023.0)*Weather_change)/3); //divide by 3 as this is the difference in time from 0 value
            Pressure_1st_5min = Pressure_5th_115min; //Equating the pressure at 0 to the pressure at 2 hour after 3 hours have past.
            pressure_second_round_flag =true; // flag to let you know that this is on the past 3 hour mark. Initialized to 0 outside main loop.
        }
        if (weather_cntr ==155){
            newForecast = true;
            //Avg pressure at end of the hour, value averaged from 0 to 5 min.
            Pressure_6th_150min = ((WeatherPressArray[150]+WeatherPressArray[151]+WeatherPressArray[152]+WeatherPressArray[153]+WeatherPressArray[154])/5);
            Weather_change = (Pressure_6th_150min - Pressure_1st_5min);
            if(pressure_second_round_flag ==false) //first time initial 3 hour
                dP_dt = (((65.0/1023.0)*Weather_change)/2.5); //note this is for t = 2.5 hour
            if(pressure_second_round_flag ==true) //more than initial 3 hour.
                dP_dt = (((65.0/1023.0)*Weather_change)/3.5); //divide by 3.5 as this is the difference in time from 0 value
        }
        if (weather_cntr ==180){
            newForecast = true;
            //Avg pressure at end of the hour, value averaged from 0 to 5 min.
            Pressure_7th_180min = ((WeatherPressArray[175]+WeatherPressArray[176]+WeatherPressArray[177]+WeatherPressArray[178]+WeatherPressArray[179])/5);
            Weather_change = (Pressure_7th_180min - Pressure_1st_5min);
            if(pressure_second_round_flag ==false) //first time initial 3 hour
                dP_dt = (((65.0/1023.0)*Weather_change)/3); //note this is for t = 3 hour
            if(pressure_second_round_flag ==true) //more than initial 3 hour.
                dP_dt = (((65.0/1023.0)*Weather_change)/4); //divide by 4 as this is the difference in time from 0 value
            Pressure_1st_5min = Pressure_6th_150min; //Equating the pressure at 0 to the pressure at 2 hour after 3 hours have past.
            pressure_second_round_flag =true; // flag to let you know that this is on the past 3 hour mark. Initialized to 0 outside main loop.
        }

        if(newForecast)
        {
            delta_pressure = (float) dP_dt;
            for (int j=11;j>0;j--) {               //save momentaneous pressure in array in position 0
                delta_pressure_h[j] = delta_pressure_h[j-1];
            }
            delta_pressure_h[0]= (float) dP_dt;
        }

        if ((dP_dt > (-0.05)) && (dP_dt <0.05))
            weatherstatus = 0; // Stable weather
        if ((dP_dt >0.05) && (dP_dt <0.25))
            weatherstatus = 1; // Slowly rising HP stable good weather
        if ((dP_dt > (-0.25)) && (dP_dt <(-0.05)))
            weatherstatus = 2; // Slowly falling Low Pressure System, stable rainy weather
        if (dP_dt > 0.25)
            weatherstatus = 3; // Quickly rising HP, not stable weather
        if (dP_dt < (-0.25))
            weatherstatus = 4; // Quickly falling LP, Thunderstorm, not stable
        if ((weather_cntr <35) && (pressure_second_round_flag !=true)) //if time is less than 35 min on the first 3 hour interval.
            weatherstatus =5; //Unknown, more TIME needed
        return weatherstatus;
    }

    private double getAdcCount()
    {
        float p = pressure_read/1000;

        long iPart = (long) p;
        double fPart = p - iPart;

        int adc = dicPressureADC.get((int) iPart);
        int adcNext = dicPressureADC.get((int) iPart+1);
        int deltaAdc = adcNext - adc;

        double adcToAdd = fPart * deltaAdc;

        return adc + adcToAdd;
    }

    /*private double getAltitudeFromService(Double longitude, Double latitude) throws IOException {


        double result = Double.NaN;

        String url = "http://gisdata.usgs.gov/"
                + "xmlwebservices2/elevation_service.asmx/"
                + "getElevation?X_Value=" + String.valueOf(longitude)
                + "&Y_Value=" + String.valueOf(latitude)
                + "&Elevation_Units=METERS&Source_Layer=-1&Elevation_Only=true";

        try {
            // Ouverture de la connexion
            HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();

            // Connexion à l'URL
            urlConnection.connect();

            // Si le serveur nous répond avec un code OK
            if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream instream = urlConnection.getInputStream();
                int r = -1;
                StringBuffer respStr = new StringBuffer();
                while ((r = instream.read()) != -1)
                    respStr.append((char) r);
                String tagOpen = "<double>";
                String tagClose = "</double>";
                if (respStr.indexOf(tagOpen) != -1) {
                    int start = respStr.indexOf(tagOpen) + tagOpen.length();
                    int end = respStr.indexOf(tagClose);
                    String value = respStr.substring(start, end);
                    result = Double.parseDouble(value);
                }
                instream.close();
            }
        } catch (IOException e)        {
            throw e;
        } catch (Exception e) {
            throw e;
        }
       return result;
    }*/

    /*private double getAltitudeFromService2(Double longitude, Double latitude) {
        double result = Double.NaN;
        HttpClient httpClient = new DefaultHttpClient();
        HttpContext localContext = new BasicHttpContext();
        String url = "http://gisdata.usgs.gov/"
                + "xmlwebservices2/elevation_service.asmx/"
                + "getElevation?X_Value=" + String.valueOf(longitude)
                + "&Y_Value=" + String.valueOf(latitude)
                + "&Elevation_Units=METERS&Source_Layer=-1&Elevation_Only=true";
        HttpGet httpGet = new HttpGet(url);
        try {
            HttpResponse response = httpClient.execute(httpGet, localContext);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                InputStream instream = entity.getContent();
                int r = -1;
                StringBuffer respStr = new StringBuffer();
                while ((r = instream.read()) != -1)
                    respStr.append((char) r);
                String tagOpen = "<double>";
                String tagClose = "</double>";
                if (respStr.indexOf(tagOpen) != -1) {
                    int start = respStr.indexOf(tagOpen) + tagOpen.length();
                    int end = respStr.indexOf(tagClose);
                    String value = respStr.substring(start, end);
                    result = Double.parseDouble(value);
                }
                instream.close();
            }
        } catch (ClientProtocolException e) {}
        catch (IOException e) {}
        return result;
    }*/

    /*void displayForecast(final TextView textViewForecast, final String forecast, final TextView textViewF1)
    {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textViewForecast.setText(forecast);
                String s = java.util.Arrays.toString(values_forecast);
                textViewF1.setText(s);
            }
        });
    }*/
}
