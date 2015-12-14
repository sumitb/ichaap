package edu.stonybrook.cs.ichaap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.http.NextServiceFilterCallback;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilter;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterRequest;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterResponse;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;
import com.microsoft.windowsazure.mobileservices.table.sync.MobileServiceSyncContext;
import com.microsoft.windowsazure.mobileservices.table.sync.localstore.ColumnDataType;
import com.microsoft.windowsazure.mobileservices.table.sync.localstore.MobileServiceLocalStoreException;
import com.microsoft.windowsazure.mobileservices.table.sync.localstore.SQLiteLocalStore;
import com.microsoft.windowsazure.mobileservices.table.sync.synchandler.SimpleSyncHandler;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;


    /**
     * Mobile Service Client reference
     */
    private MobileServiceClient mClient;

    /**
     * Mobile Service Table used to access data
     */
    private MobileServiceTable<CellularData> mToDoTable;

    /**
     * Progress spinner to use for table operations
     */
    private ProgressBar mProgressBar;


    private View mContentView;
    private View mControlsView;
    private boolean mVisible;

    String logfilename="/storage/emulated/0/logger.txt";

    TextView tv;
    Button locationbutton;
    TelephonyManager tm;
    SignalStrength strength;
    OutputStreamWriter myOutWriter;

    static int log_results = 1;
    private double CurrentLocationLong = 0;
    private double CurrentLocationLat = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);


        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);

        //
        tm = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        tv = (TextView)findViewById(R.id.textView);
        // tv.setTextColor(Color.parseColor("#4FFF8F"));

        // Microsoft Azure db connector
        try {
            // Create the Mobile Service Client instance, using the provided

            // Mobile Service URL and key
            mClient = new MobileServiceClient(
                    "https://imap.azure-mobile.net/",
                    "fKqVLReDcZfHESQuzEmhQeXweNksJA27",
                    this).withFilter(new ProgressFilter());

            // Get the Mobile Service Table instance to use

            mToDoTable = mClient.getTable(CellularData.class);

            // Offline Sync
            //mToDoTable = mClient.getSyncTable("CellularData", CellularData.class);

            //Init local storage
            initLocalStore().get();

            // mTextNewToDo = (EditText) findViewById(R.id.textNewToDo);

            // Create an adapter to bind the items with the view
            // mAdapter = new ToDoItemAdapter(this, R.layout.row_list_to_do);
            // ListView listViewToDo = (ListView) findViewById(R.id.listViewToDo);
            // listViewToDo.setAdapter(mAdapter);

            // Load the items from the Mobile Service
            // refreshItemsFromTable();

        } catch (MalformedURLException e) {
            createAndShowDialog(new Exception("There was an error creating the Mobile Service. Verify the URL"), "Error");
        } catch (Exception e){
            createAndShowDialog(e, "Error");
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };

    private final Handler mHideHandler = new Handler();
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    /**
     * Performs cellular measurement of Cellular and Wi-fi networks
     */
    public void selfCellularMeasurement(View view) {
        Log.i("button", "clicked");
        refreshView();
    }

    public void refreshView(){

        int i;
        try {
            Writer output;
            output = new BufferedWriter(new FileWriter(logfilename, true));

            Date timestamp = new Date();
            @SuppressWarnings("deprecation")
            String time = timestamp.getDate()+"_"+timestamp.getHours()+"_"+timestamp.getMinutes()+"_"+timestamp.getSeconds();
            if(log_results == 1) output.append(time+"\n");

            String operator =  tm.getNetworkOperatorName();
            String phonetypes[] = {"None", "GSM", "CDMA", "SIP"};
            String phonetype = phonetypes[tm.getPhoneType()];
            CellLocation loc = tm.getCellLocation();

            if(log_results ==1) output.append(loc+"\n");

            tv.setText("Radio Type - "+phonetype+" "+time+"\n");

            if(log_results ==1) output.append("Loc: "+CurrentLocationLat+" "+CurrentLocationLong+" "+time+"\n");

            if(phonetype.equals("GSM")){
                tv.append("BER: "+strength.getGsmBitErrorRate()+"\n");
                tv.append("dBm: -"+(-113+2*strength.getGsmSignalStrength())+"\n");
                tv.append("ASU: "+strength.getGsmSignalStrength()+"\n\n");
                if(log_results ==1) {output.append("GSM RSSI: "+(-113+2*strength.getGsmSignalStrength())+"\n");}
            }

            if(phonetype.equals("CDMA")){
                tv.append("RSCP: "+strength.getCdmaDbm()+"\n");
                if(log_results ==1) output.append("CDMA RSCP:"+strength.getCdmaDbm()+"\n");
                tv.append("CDMA Ec/I0: "+(strength.getCdmaEcio())+"\n");
                if(log_results ==1) output.append("CDMA Ec/I0: "+(strength.getCdmaEcio())+"\n");
            }

            tv.append( "Operator   - "+operator+" ("+tm.getLine1Number()+")\n");
            tv.append( "Cellular Location   - "+loc.toString()+"\n\n");
            tv.append( "Geo Location   - "+CurrentLocationLat+" "+CurrentLocationLong+"\n\n");


            int net_type =  tm.getNetworkType();

            if(net_type == TelephonyManager.NETWORK_TYPE_EDGE ){
                tv.append("Network Type: EDGE\n");

                int rssi, lac, cid;

                List<NeighboringCellInfo> cellinfo = tm.getNeighboringCellInfo();
                for(i=0; i< cellinfo.size(); i++){
                    rssi = (-113 + 2 * cellinfo.get(i).getRssi());
                    lac = cellinfo.get(i).getLac();
                    cid = cellinfo.get(i).getCid();

                    tv.append("EDGE Cell "+i+"CID: "+cid+" LAC: "+lac+" rssi: "+rssi+"\n");
                    if(log_results ==1) output.append("EDGE Cell "+i+"CID: "+cid+" LAC: "+lac+" rssi: "+rssi+"\n");
                }
            }

            if(net_type == TelephonyManager.NETWORK_TYPE_CDMA) {
                tv.append("Network Type: CDMA\n");
                List<NeighboringCellInfo> cellinfo = tm.getNeighboringCellInfo();
                int rssi;
                for(i=0; i< cellinfo.size(); i++){
                    rssi = cellinfo.get(i).getRssi();
                    tv.append("CDMA Neighboring Cell "+i+" "+rssi+	"\n");
                    if(log_results ==1) output.append("CDMA Neighboring Cell "+i+" "+rssi+	"\n");

                }
            }

            if(net_type == TelephonyManager.NETWORK_TYPE_UMTS) {
                tv.append("Network Type: UMTS\n");
                List<NeighboringCellInfo> cellinfo = tm.getNeighboringCellInfo();
                int rssi,pch;
                for(i=0; i< cellinfo.size(); i++){
                    rssi = cellinfo.get(i).getRssi();
                    pch = cellinfo.get(i).getPsc();
                    tv.append("UMTS Neighboring Cell psc "+pch+" "+rssi+	"\n");
                    if(log_results ==1) output.append("UMTS Neighboring Cell "+pch+" "+rssi+	"\n");
                }
            }

            if(net_type == TelephonyManager.NETWORK_TYPE_HSPA) {

                tv.append("Network Type: HSPA\n");
                List<NeighboringCellInfo> cellinfo = tm.getNeighboringCellInfo();
                int rssi, pch;
                for(i=0; i< cellinfo.size(); i++){
                    rssi = cellinfo.get(i).getRssi();
                    pch = cellinfo.get(i).getPsc();
                    tv.append("HSPA Neighboring Cell psc "+pch+" "+rssi+	"\n");
                    if(log_results ==1)output.append("HSPA Neighboring Cell "+pch+" "+rssi+	"\n");
                }
            }

            if(net_type == TelephonyManager.NETWORK_TYPE_HSPAP) {

                tv.append("Network Type: HSPA+\n");
                List<NeighboringCellInfo> cellinfo = tm.getNeighboringCellInfo();
                int rssi, pch;
                for(i=0; i< cellinfo.size(); i++){
                    rssi = cellinfo.get(i).getRssi();
                    pch = cellinfo.get(i).getPsc();
                    tv.append("HSPA+ Neighboring Cell psc "+pch+" "+rssi+	"\n");
                    if(log_results ==1)output.append("HSPA Neighboring Cell "+pch+" "+rssi+	"\n");
                }
            }

            if(net_type == TelephonyManager.NETWORK_TYPE_LTE) {
                tv.append("Network Type: LTE\n");

                List<NeighboringCellInfo> cellinfo = tm.getNeighboringCellInfo();
                int rssi;
                for(i=0; i< cellinfo.size(); i++){
                    rssi = cellinfo.get(i).getRssi();
                    tv.append("LTE Neighboring Cell "+i+" "+rssi+	"\n");
                    if(log_results ==1) output.append("LTE Neighboring Cell "+i+" "+rssi+	"\n");

                }

            }

            if(net_type == TelephonyManager.NETWORK_TYPE_UNKNOWN) { tv.append("Unknown Network Type\n"); }


            WifiManager wifi;
            List<ScanResult> results;
            wifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);
            wifi.startScan();
            results = wifi.getScanResults();
            try {
                for(int k = 0; k<results.size(); k++) {
                    //tv.append("WiFi: "+results.get(k).BSSID+" "+results.get(k).SSID+" "+results.get(k).level+" "+results.get(k).frequency+"\n");
                    if(log_results ==1) output.append("WiFi: "+results.get(k).BSSID+" "+results.get(k).SSID+" "+results.get(k).level+" "+results.get(k).frequency+"\n");
                }
            }catch(Exception e){}

            output.close();

        } catch (Exception e) {}
    }


    // Listener for signal strength.
    final PhoneStateListener mListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength sStrength)
        {
            strength = sStrength;
            refreshView();
        }
    };

    /**
     * Add a new item
     *
     * @param view
     *            The view that originated the call
     */
    public void addItem(View view) {
        if (mClient == null) {
            return;
        }

        // Create a new item
        final CellularData item = new CellularData();

        // item.setText(mTextNewToDo.getText().toString());
        item.setComplete(false);

        // Insert the new item
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>(){
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    final CellularData entity = addItemInTable(item);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(!entity.isComplete()){
                                //mAdapter.add(entity);
                            }
                        }
                    });
                } catch (final Exception e) {
                    createAndShowDialogFromTask(e, "Error");
                }
                return null;
            }
        };

        runAsyncTask(task);

        // mTextNewToDo.setText("");
    }

    /**
     * Add an item to the Mobile Service Table
     *
     * @param item
     *            The item to Add
     */
    public CellularData addItemInTable(CellularData item) throws ExecutionException, InterruptedException {
        CellularData entity = mToDoTable.insert(item).get();
        return entity;
    }

    /**
     * Initialize local storage
     * @return
     * @throws MobileServiceLocalStoreException
     * @throws ExecutionException
     * @throws InterruptedException
     */
    private AsyncTask<Void, Void, Void> initLocalStore() throws MobileServiceLocalStoreException, ExecutionException, InterruptedException {

        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {

                    MobileServiceSyncContext syncContext = mClient.getSyncContext();

                    if (syncContext.isInitialized())
                        return null;

                    SQLiteLocalStore localStore = new SQLiteLocalStore(mClient.getContext(), "OfflineStore", null, 1);

                    Map<String, ColumnDataType> tableDefinition = new HashMap<String, ColumnDataType>();
                    tableDefinition.put("id", ColumnDataType.String);
                    tableDefinition.put("text", ColumnDataType.String);
                    tableDefinition.put("complete", ColumnDataType.Boolean);

                    localStore.defineTable("CellularData", tableDefinition);

                    SimpleSyncHandler handler = new SimpleSyncHandler();

                    syncContext.initialize(localStore, handler).get();

                } catch (final Exception e) {
                    createAndShowDialogFromTask(e, "Error");
                }

                return null;
            }
        };

        return runAsyncTask(task);
    }

    /**
     * Creates a dialog and shows it
     *
     * @param exception
     *            The exception to show in the dialog
     * @param title
     *            The dialog title
     */
    private void createAndShowDialogFromTask(final Exception exception, String title) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                createAndShowDialog(exception, "Error");
            }
        });
    }

    /**
     * Creates a dialog and shows it
     *
     * @param exception
     *            The exception to show in the dialog
     * @param title
     *            The dialog title
     */
    private void createAndShowDialog(Exception exception, String title) {
        Throwable ex = exception;
        if(exception.getCause() != null){
            ex = exception.getCause();
        }
        createAndShowDialog(ex.getMessage(), title);
    }

    /**
     * Creates a dialog and shows it
     *
     * @param message
     *            The dialog message
     * @param title
     *            The dialog title
     */
    private void createAndShowDialog(final String message, final String title) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setMessage(message);
        builder.setTitle(title);
        builder.create().show();
    }

    /**
     * Run an ASync task on the corresponding executor
     * @param task
     * @return
     */
    private AsyncTask<Void, Void, Void> runAsyncTask(AsyncTask<Void, Void, Void> task) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            return task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            return task.execute();
        }
    }

    private class ProgressFilter implements ServiceFilter {

        @Override
        public ListenableFuture<ServiceFilterResponse> handleRequest(ServiceFilterRequest request, NextServiceFilterCallback nextServiceFilterCallback) {

            final SettableFuture<ServiceFilterResponse> resultFuture = SettableFuture.create();


            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if (mProgressBar != null) mProgressBar.setVisibility(ProgressBar.VISIBLE);
                }
            });

            ListenableFuture<ServiceFilterResponse> future = nextServiceFilterCallback.onNext(request);

            Futures.addCallback(future, new FutureCallback<ServiceFilterResponse>() {
                @Override
                public void onFailure(Throwable e) {
                    resultFuture.setException(e);
                }

                @Override
                public void onSuccess(ServiceFilterResponse response) {
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            if (mProgressBar != null) mProgressBar.setVisibility(ProgressBar.GONE);
                        }
                    });

                    resultFuture.set(response);
                }
            });

            return resultFuture;
        }
    }
}