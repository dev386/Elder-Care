package maclab.eldercare.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.wifi.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import maclab.eldercare.R;
import maclab.eldercare.service.BluetoothLeService;

/**
 * 特別說明：HC_BLE助手是廣州匯承資訊科技有限公司獨自研發的手機APP，方便使用者調試08藍牙模組。
 * 本軟體只能支援安卓版本4.3並且有藍牙4.0的手機使用。
 * 另外對於自家的05、06模組，要使用另外一套藍牙2.0的手機APP，用戶可以在匯承官方網的下載中心自行下載。
 * 本軟體提供代碼和注釋，免費給購買匯承08模組的使用者學習和研究，但不能用於商業開發，最終解析權在廣州匯承資訊科技有限公司。
 * **/

/**
 * @Description:  TODO<Ble_Activity實現連接BLE,發送和接受BLE的資料>
 * @author  廣州匯承資訊科技有限公司
 * @data:  2014-10-20 下午12:12:04
 * @version:  V1.0
 */
public class Ble_Activity extends Activity{

    private final static String TAG = Ble_Activity.class.getSimpleName();
    //藍牙4.0的UUID,其中0000ffe1-0000-1000-8000-00805f9b34fb是廣州匯承資訊科技有限公司08藍牙模組的UUID
    public static String HEART_RATE_MEASUREMENT = "0000ffe1-0000-1000-8000-00805f9b34fb";
    public static String EXTRAS_DEVICE_NAME = "DEVICE_NAME";;
    public static String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static String EXTRAS_DEVICE_RSSI = "RSSI";
    //藍牙連接狀態
    private boolean mConnected = false;
    private String status = "disconnected";
    //藍牙名字
    private String mDeviceName;
    //藍牙地址
    private String mDeviceAddress;
    //藍牙信號值
    private String mRssi;
    private Bundle b;
    private String rev_str = "";
    //藍牙service,負責後臺的藍牙服務
    private static BluetoothLeService mBluetoothLeService;
    //文字方塊，顯示接受的內容
    private TextView connect_state;

    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    //藍牙特徵值
    private static BluetoothGattCharacteristic target_chara = null;
    private Handler mhandler = new Handler();
    private Handler myHandler = new Handler()
    {
        // 2.重寫消息處理函數
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                // 判斷發送的消息
                case 1:
                {
                    // 更新View
                    String state = msg.getData().getString("connect_state");
                    connect_state.setText("bluetooth : "+state);

                    break;
                }

            }
            super.handleMessage(msg);
        }

    };


    // UI Views
    private Button sendPhoneNum;
    private Button sendWifi;
    private EditText inputPhoneNum;
    private EditText inputWifiPwd;
    private Spinner spinnerWifiSSID;
    private String wifiSSID;
    private ArrayList<String> listSSID;
    private WifiManager wifiManager;

    // const string for protocol
    public static final String GET_PHONE = "!0";
    public static final String SET_PHONE = "!1";
    public static final String SET_WIFI_SSID = "!2";
    public static final String SET_WIFI_PWD = "!3";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_activity);

        sendPhoneNum = (Button) findViewById(R.id.btn_phone_number);
        sendWifi = (Button) findViewById(R.id.btn_wifi);
        inputPhoneNum = (EditText) findViewById(R.id.input_phone_number);
        inputWifiPwd = (EditText) findViewById(R.id.input_wifi_pwd);
        spinnerWifiSSID = (Spinner) findViewById(R.id.spinner_wifi);
        connect_state = (TextView) this.findViewById(R.id.connect_state);
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);



        b = getIntent().getExtras();
        //從意圖獲取顯示的藍牙資訊
        mDeviceName = b.getString(EXTRAS_DEVICE_NAME);
        mDeviceAddress = b.getString(EXTRAS_DEVICE_ADDRESS);
        mRssi = b.getString(EXTRAS_DEVICE_RSSI);

		/* 啟動藍牙service */
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        init();

    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        //解除廣播接收器
        unregisterReceiver(mGattUpdateReceiver);
        mBluetoothLeService = null;
    }

    // Activity出來時候，綁定廣播接收器，監聽藍牙連接服務傳過來的事件
    @Override
    protected void onResume()
    {
        super.onResume();
        //綁定廣播接收器
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null)
        {
            //根據藍牙位址，建立連接
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
        init();
    }

    /**
     * @Title: init
     * @Description: TODO(初始化UI控制項)
     * @param
     * @return void
     * @throws
     */
    private void init()
    {
        connect_state.setText("Bluetooth : " + status);

        scanWifi();

        spinnerWifiSSID.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scanWifi();
                return false;
            }
        });
        spinnerWifiSSID.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                wifiSSID = String.valueOf(parent.getItemIdAtPosition(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, listSSID);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerWifiSSID.setAdapter(adapter);

        sendPhoneNum.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setPhoneNumBLE();
            }
        });

        sendWifi.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setWifiSSIDBLE();
                setSetWifiPwdBLE();
            }
        });
    }

    private void scanWifi(){
        List<android.net.wifi.ScanResult> wifiScanList = wifiManager.getScanResults();
        listSSID = new ArrayList<String>();
        for (int i = 0; i < wifiScanList.size(); i++) {
            listSSID.add(((wifiScanList.get(i).SSID)));
        }
    }

    /* BluetoothLeService綁定的回呼函數 */
    private final ServiceConnection mServiceConnection = new ServiceConnection()
    {

        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service)
        {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service)
                    .getService();
            if (!mBluetoothLeService.initialize())
            {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up
            // initialization.
            // 根據藍牙位址，連接設備
            mBluetoothLeService.connect(mDeviceAddress);

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName)
        {
            mBluetoothLeService = null;
        }

    };

    /**
     * 廣播接收器，負責接收BluetoothLeService類發送的資料
     */
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action))//Gatt連接成功
            {
                mConnected = true;
                status = "connected";
                //更新連接狀態
                updateConnectionState(status);
                System.out.println("BroadcastReceiver :" + "device connected");

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED//Gatt連接失敗
                    .equals(action))
            {
                mConnected = false;
                status = "disconnected";
                //更新連接狀態
                updateConnectionState(status);
                System.out.println("BroadcastReceiver :"
                        + "device disconnected");

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED//發現GATT伺服器
                    .equals(action))
            {
                // Show all the supported services and characteristics on the
                // user interface.
                //獲取設備的所有藍牙服務
                displayGattServices(mBluetoothLeService
                        .getSupportedGattServices());
                System.out.println("BroadcastReceiver :"
                        + "device SERVICES_DISCOVERED");
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action))//有效資料
            {
                //處理發送過來的資料
                displayData(intent.getExtras().getString(
                        BluetoothLeService.EXTRA_DATA));
                System.out.println("BroadcastReceiver onData:"
                        + intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    /* 更新連接狀態 */
    private void updateConnectionState(String status)
    {
        Message msg = new Message();
        msg.what = 1;
        Bundle b = new Bundle();
        b.putString("connect_state", status);
        msg.setData(b);
        //將連接狀態更新的UI的textview上
        myHandler.sendMessage(msg);
        System.out.println("connect_state:" + status);

    }

    /* 意圖篩檢程式 */
    private static IntentFilter makeGattUpdateIntentFilter()
    {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter
                .addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    /**
     * @Title: displayData
     * @Description: TODO(接收到的資料在scrollview上顯示)
     * @param @param rev_string(接受的數據)
     * @return void
     * @throws
     */
    private void displayData(final String rev_string)
    {
        rev_str = rev_string;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                switch (rev_str.charAt(0)) {
                    case '!':
                        inputPhoneNum.setText(rev_string.substring(1));
                        break;
                }

            }
        });

    }

    /**
     * @Title: displayGattServices
     * @Description: TODO(處理藍牙服務)
     * @param
     * @return void
     * @throws
     */
    private void displayGattServices(List<BluetoothGattService> gattServices)
    {

        if (gattServices == null)
            return;
        String uuid = null;
        String unknownServiceString = "unknown_service";
        String unknownCharaString = "unknown_characteristic";

        // 服務資料,可擴展下拉清單的第一級數據
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();

        // 特徵資料（隸屬於某一級服務下面的特徵值集合）
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData = new ArrayList<ArrayList<HashMap<String, String>>>();

        // 部分層次，所有特徵值集合
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices)
        {

            // 獲取服務清單
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();

            // 查表，根據該uuid獲取對應的服務名稱。SampleGattAttributes這個表需要自訂。

            gattServiceData.add(currentServiceData);

            System.out.println("Service uuid:" + uuid);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData = new ArrayList<HashMap<String, String>>();

            // 從當前迴圈所指向的服務中讀取特徵值清單
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService
                    .getCharacteristics();

            ArrayList<BluetoothGattCharacteristic> charas = new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            // 對於當前迴圈所指向的服務中的每一個特徵值
            for (final BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics)
            {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();

                if (gattCharacteristic.getUuid().toString()
                        .equals(HEART_RATE_MEASUREMENT))
                {
                    // 測試讀取當前Characteristic資料，會觸發mOnDataAvailable.onCharacteristicRead()
                    mhandler.postDelayed(new Runnable()
                    {

                        @Override
                        public void run()
                        {
                            // TODO Auto-generated method stub
                            mBluetoothLeService
                                    .readCharacteristic(gattCharacteristic);
                        }
                    }, 200);

                    // 接受Characteristic被寫的通知,收到藍牙模組的資料後會觸發mOnDataAvailable.onCharacteristicWrite()
                    mBluetoothLeService.setCharacteristicNotification(
                            gattCharacteristic, true);
                    target_chara = gattCharacteristic;
                    // 設置資料內容
                    // 往藍牙模組寫入資料
                    // mBluetoothLeService.writeCharacteristic(gattCharacteristic);
                }
                List<BluetoothGattDescriptor> descriptors = gattCharacteristic
                        .getDescriptors();
                for (BluetoothGattDescriptor descriptor : descriptors)
                {
                    System.out.println("---descriptor UUID:"
                            + descriptor.getUuid());
                    // 獲取特徵值的描述
                    mBluetoothLeService.getCharacteristicDescriptor(descriptor);
                    // mBluetoothLeService.setCharacteristicNotification(gattCharacteristic,
                    // true);
                }

                gattCharacteristicGroupData.add(currentCharaData);
            }
            // 按先後順序，分層次放入特徵值集合中，只有特徵值
            mGattCharacteristics.add(charas);
            // 構件第二級擴展清單（服務下面的特徵值）
            gattCharacteristicData.add(gattCharacteristicGroupData);

        }

    }

    private void getPhoneNum(){
        target_chara.setValue(GET_PHONE+"@");
        mBluetoothLeService.writeCharacteristic(target_chara);
    }

    private void setPhoneNumBLE(){
        target_chara.setValue(SET_PHONE);
        mBluetoothLeService.writeCharacteristic(target_chara);
        target_chara.setValue(inputPhoneNum.getText()+"@");
        mBluetoothLeService.writeCharacteristic(target_chara);
    }

    private void setWifiSSIDBLE(){
        target_chara.setValue(SET_WIFI_SSID);
        mBluetoothLeService.writeCharacteristic(target_chara);
        target_chara.setValue(spinnerWifiSSID.getSelectedItem().toString()+"@");
        mBluetoothLeService.writeCharacteristic(target_chara);
    }

    private void setSetWifiPwdBLE(){
        target_chara.setValue(SET_WIFI_PWD);
        mBluetoothLeService.writeCharacteristic(target_chara);
        target_chara.setValue(inputWifiPwd.getText()+"@");
        mBluetoothLeService.writeCharacteristic(target_chara);
    }

}

