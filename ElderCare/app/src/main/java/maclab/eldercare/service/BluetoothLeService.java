package maclab.eldercare.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/**
 * 特別說明：HC_BLE助手是廣州匯承資訊科技有限公司獨自研發的手機APP，方便使用者調試08藍牙模組。
 * 本軟體只能支援安卓版本4.3並且有藍牙4.0的手機使用。
 * 另外對於自家的05、06模組，要使用另外一套藍牙2.0的手機APP，用戶可以在匯承官方網的下載中心自行下載。
 * 本軟體提供代碼和注釋，免費給購買匯承08模組的使用者學習和研究，但不能用於商業開發，最終解析權在廣州匯承資訊科技有限公司。
 **/

/**
 * @author 廣州匯承資訊科技有限公司
 * @Description: TODO<藍牙服務，負責在後臺實現藍牙的連接，資料的發送接受>
 * @data: 2014-10-22 下午2:30:38
 * @version: V1.0
 */
public class BluetoothLeService extends Service {
    private final static String TAG = "BluetoothLeService";// luetoothLeService.class.getSimpleName();
    private List<Sensor> mEnabledSensors = new ArrayList<Sensor>();
    //藍牙相關類
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "com.example.bluetooth.le.EXTRA_DATA";

    // public final static UUID UUID_HEART_RATE_MEASUREMENT =zzzzzzzzzzzzz
    // UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);
    private OnDataAvailableListener mOnDataAvailableListener;

    // Implements callback methods for GATT events that the app cares about. For
    // example,
    // connection change and services discovered.

    public interface OnDataAvailableListener {
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status);

        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic);

        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic);
    }

    public void setOnDataAvailableListener(OnDataAvailableListener l) {
        mOnDataAvailableListener = l;
    }

    /* 連接遠端設備的回呼函數 */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED)//連接成功
            {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                /* 通過廣播更新連接狀態 */
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:"
                        + mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED)//連接失敗
            {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        /*
         * 重寫onServicesDiscovered，發現藍牙服務
         *
         * */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS)//發現到服務
            {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                Log.i(TAG, "--onServicesDiscovered called--");
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
                System.out.println("onServicesDiscovered received: " + status);
            }
        }

        /*
         * 特徵值的讀寫
         * */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "--onCharacteristicRead called--");
                //從特徵值讀取數據
                byte[] sucString = characteristic.getValue();
                String string = new String(sucString);
                //將資料通過廣播到Ble_Activity
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }

        }

        /*
         * 特徵值的改變
         * */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            System.out.println("++++++++++++++++");
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);

        }

        /*
         * 特徵值的寫
         * */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {

            Log.w(TAG, "--onCharacteristicWrite--: " + status);
            // 以下語句實現 發送完資料或也顯示到介面上
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        /*
         * 讀描述值
         * */
        @Override
        public void onDescriptorRead(BluetoothGatt gatt,
                                     BluetoothGattDescriptor descriptor, int status) {
            // TODO Auto-generated method stub
            // super.onDescriptorRead(gatt, descriptor, status);
            Log.w(TAG, "----onDescriptorRead status: " + status);
            byte[] desc = descriptor.getValue();
            if (desc != null) {
                Log.w(TAG, "----onDescriptorRead value: " + new String(desc));
            }

        }

        /*
         * 寫描述值
         * */
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor, int status) {
            // TODO Auto-generated method stub
            // super.onDescriptorWrite(gatt, descriptor, status);
            Log.w(TAG, "--onDescriptorWrite--: " + status);
        }

        /*
         * 讀寫藍牙信號值
         * */
        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            // TODO Auto-generated method stub
            // super.onReadRemoteRssi(gatt, rssi, status);
            Log.w(TAG, "--onReadRemoteRssi--: " + status);
            broadcastUpdate(ACTION_DATA_AVAILABLE, rssi);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            // TODO Auto-generated method stub
            // super.onReliableWriteCompleted(gatt, status);
            Log.w(TAG, "--onReliableWriteCompleted--: " + status);
        }

    };

    //廣播意圖
    private void broadcastUpdate(final String action, int rssi) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA, String.valueOf(rssi));
        sendBroadcast(intent);
    }

    //廣播意圖
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    /* 廣播遠端發送過來的資料 */
    public void broadcastUpdate(final String action,
                                final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        //從特徵值獲取資料
        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for (byte byteChar : data) {
                stringBuilder.append(String.format("%02X ", byteChar));

                Log.i(TAG, "***broadcastUpdate: byteChar = " + byteChar);

            }
            intent.putExtra(EXTRA_DATA, new String(data));
            System.out.println("broadcastUpdate for  read data:"
                    + new String(data));
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();


    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    /* service 中藍牙初始化 */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter
        // through
        // BluetoothManager.
        if (mBluetoothManager == null) {   //獲取系統的藍牙管理器
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The
     * connection result is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    // 連接遠端藍牙
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG,
                    "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device. Try to reconnect.
        if (mBluetoothDeviceAddress != null
                && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG,
                    "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect())//連接藍牙，其實就是調用BluetoothGatt的連接方法
            {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }
		/* 獲取遠端的藍牙設備 */
        final BluetoothDevice device = mBluetoothAdapter
                .getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the
        // autoConnect
        // parameter to false.
		/* 調用device中的connectGatt連接到遠端設備 */
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        System.out.println("device.getBondState==" + device.getBondState());
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The
     * disconnection result is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
	/*
	 * 取消連接
	 *
	 * */

    /**
     * @param
     * @return void
     * @throws
     * @Title: disconnect
     * @Description: TODO(取消藍牙連接)
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();

    }

    /**
     * After using a given BLE device, the app must call this method to ensure
     * resources are released properly.
     */
    /**
     * @param
     * @return void
     * @throws
     * @Title: close
     * @Description: TODO(關閉所有藍牙連接)
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read
     * result is reported asynchronously through the
     * {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic
     *            The characteristic to read from.
     */
    /**
     * @param @param characteristic（要讀的特徵值）
     * @return void    返回類型
     * @throws
     * @Title: readCharacteristic
     * @Description: TODO(讀取特徵值)
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);

    }

    // 寫入特徵值
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.writeCharacteristic(characteristic);

    }

    // 讀取RSSi
    public void readRssi() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readRemoteRssi();
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic
     *            Characteristic to act on.
     * @param enabled
     *            If true, enable notification. False otherwise.
     */
    /**
     * @param @param characteristic（特徵值）
     * @param @param enabled （使能）
     * @return void
     * @throws
     * @Title: setCharacteristicNotification
     * @Description: TODO(設置特徵值通變化通知)
     */
    public void setCharacteristicNotification(
            BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        BluetoothGattDescriptor clientConfig = characteristic
                .getDescriptor(UUID
                        .fromString("00002902-0000-1000-8000-00805f9b34fb"));

        if (enabled) {
            clientConfig
                    .setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            clientConfig
                    .setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }
        mBluetoothGatt.writeDescriptor(clientConfig);
    }

    /**
     * @param @param 無
     * @return void
     * @throws
     * @Title: getCharacteristicDescriptor
     * @Description: TODO(得到特徵值下的描述值)
     */
    public void getCharacteristicDescriptor(BluetoothGattDescriptor descriptor) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        mBluetoothGatt.readDescriptor(descriptor);
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This
     * should be invoked only after {@code BluetoothGatt#discoverServices()}
     * completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    /**
     * @param @return 無
     * @return List<BluetoothGattService>
     * @throws
     * @Title: getSupportedGattServices
     * @Description: TODO(得到藍牙的所有服務)
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null)
            return null;
        return mBluetoothGatt.getServices();

    }

}


