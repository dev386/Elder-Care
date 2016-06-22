package maclab.eldercare;

import java.util.ArrayList;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import maclab.eldercare.ui.Ble_Activity;

/**
 * 特別說明：HC_BLE助手是廣州匯承資訊科技有限公司獨自研發的手機APP，方便使用者調試08藍牙模組。
 * 本軟體只能支援安卓版本4.3並且有藍牙4.0的手機使用。
 * 另外對於自家的05、06模組，要使用另外一套藍牙2.0的手機APP，用戶可以在匯承官方網的下載中心自行下載。
 * 本軟體提供代碼和注釋，免費給購買匯承08模組的使用者學習和研究，但不能用於商業開發，最終解析權在廣州匯承資訊科技有限公司。
 **/

/**
 * @Description: TODO<MainActivity類實現打開藍牙、掃描藍牙>
 * @author 廣州匯承資訊科技有限公司
 * @data: 2014-10-12 上午10:28:18
 * @version: V1.0
 */
public class MainActivity extends Activity implements OnClickListener {
    // 藍牙掃描時間
    private static final long SCAN_PERIOD = 10000;
    // 藍牙適配器
    BluetoothAdapter mBluetoothAdapter;
    // 自訂Adapter
    LeDeviceListAdapter mleDeviceListAdapter;
    // listview顯示掃描到的藍牙資訊
    ListView lv;
    int REQUEST_ENABLE_BT = 1;
    // 掃描藍牙按鈕
    private Button scan_btn;
    // 藍牙信號強度
    private ArrayList<Integer> rssis;
    // 描述掃描藍牙的狀態
    private boolean mScanning;
    private boolean scan_flag;
    private Handler mHandler;
    /**
     * 藍牙掃描回呼函數 實現掃描藍牙設備，回檔藍牙BluetoothDevice，可以獲取name MAC等資訊
     *
     * **/
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi,
                             byte[] scanRecord) {
            // TODO Auto-generated method stub

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
					/* 講掃描到設備的資訊輸出到listview的適配器 */
                    mleDeviceListAdapter.addDevice(device, rssi);
                    mleDeviceListAdapter.notifyDataSetChanged();
                }
            });

            System.out.println("Address:" + device.getAddress());
            System.out.println("Name:" + device.getName());
            System.out.println("rssi:" + rssi);

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 初始化控制項
        init();
        // 初始化藍牙
        init_ble();
        scan_flag = true;
        // 自訂適配器
        mleDeviceListAdapter = new LeDeviceListAdapter();
        // 為listview指定適配器
        lv.setAdapter(mleDeviceListAdapter);

		/* listview點擊函數 */
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View v, int position,
                                    long id) {
                // TODO Auto-generated method stub
                final BluetoothDevice device = mleDeviceListAdapter
                        .getDevice(position);
                if (device == null)
                    return;
                final Intent intent = new Intent(MainActivity.this,
                        Ble_Activity.class);
                intent.putExtra(Ble_Activity.EXTRAS_DEVICE_NAME,
                        device.getName());
                intent.putExtra(Ble_Activity.EXTRAS_DEVICE_ADDRESS,
                        device.getAddress());
                intent.putExtra(Ble_Activity.EXTRAS_DEVICE_RSSI,
                        rssis.get(position).toString());
                if (mScanning) {
                    /* 停止掃描設備 */
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    mScanning = false;
                }

                try {
                    // 啟動Ble_Activity
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                    // TODO: handle exception
                }

            }
        });

    }

    /**
     * @Title: init
     * @Description: TODO(初始化UI控制項)
     * @param
     * @return void
     * @throws
     */
    private void init() {
        scan_btn = (Button) this.findViewById(R.id.scan_dev_btn);
        scan_btn.setOnClickListener(this);
        lv = (ListView) this.findViewById(R.id.lv);
        mHandler = new Handler();
    }

    /**
     * @Title: init_ble
     * @Description: TODO(初始化藍牙)
     * @param
     * @return void
     * @throws
     */
    private void init_ble() {
        // 手機硬體支援藍牙
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "不支持BLE", Toast.LENGTH_SHORT).show();
            finish();
        }
        // Initializes Bluetooth adapter.
        // 獲取手機本地的藍牙適配器
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        // 打開藍牙許可權
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

    }

    /*
     * 按鈕回應事件
     */
    @Override
    public void onClick(View v) {
        // TODO Auto-generated method stub

        if (scan_flag) {
            mleDeviceListAdapter = new LeDeviceListAdapter();
            lv.setAdapter(mleDeviceListAdapter);
            scanLeDevice(true);
        } else {

            scanLeDevice(false);
            scan_btn.setText("掃描設備");
        }
    }

    /**
     * @Title: scanLeDevice
     * @Description: TODO(掃描藍牙設備)
     * @param enable
     *            (掃描使能，true:掃描開始,false:掃描停止)
     * @return void
     * @throws
     */
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    scan_flag = true;
                    scan_btn.setText("掃描設備");
                    Log.i("SCAN", "stop.....................");
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);
			/* 開始掃描藍牙設備，帶mLeScanCallback 回呼函數 */
            Log.i("SCAN", "begin.....................");
            mScanning = true;
            scan_flag = false;
            scan_btn.setText("停止掃描");
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            Log.i("Stop", "stoping................");
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            scan_flag = true;
        }

    }

    /**
     * @Description: TODO<自訂適配器Adapter,作為listview的適配器>
     * @author 廣州匯承資訊科技有限公司
     * @data: 2014-10-12 上午10:46:30
     * @version: V1.0
     */
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;

        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            rssis = new ArrayList<Integer>();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device, int rssi) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
                rssis.add(rssi);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
            rssis.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        /**
         * 重寫getview
         *
         * **/
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {

            // General ListView optimization code.
            // 載入listview每一項的視圖
            view = mInflator.inflate(R.layout.listitem, null);
            // 初始化三個textview顯示藍牙資訊
            TextView deviceAddress = (TextView) view
                    .findViewById(R.id.tv_deviceAddr);
            TextView deviceName = (TextView) view
                    .findViewById(R.id.tv_deviceName);
            TextView rssi = (TextView) view.findViewById(R.id.tv_rssi);

            BluetoothDevice device = mLeDevices.get(i);
            deviceAddress.setText(device.getAddress());
            deviceName.setText(device.getName());
            rssi.setText("" + rssis.get(i));

            return view;
        }
    }

}

