package com.example.zb.mybluetoothdemo;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    private static final int EXQUEAT_OPEN = 11;
    @BindView(R.id.bt_open)
    Button mBtOpen;
    BluetoothAdapter bluetoothAdapter;
    @BindView(R.id.bt_close)
    Button   mBtClose;
    @BindView(R.id.bt_query)
    Button   mBtQuery;
    @BindView(R.id.result)
    ListView resultList;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    IntentFilter  intentFilter;
    MyBtReceiver  btReceiver;
    MyListAdapter listAdapter;
    @BindView(R.id.show_data_lv)
    NoScrollListView mShowDataLv;
    @BindView(R.id.input_et)
    EditText mInputEt;
    @BindView(R.id.send_bt)
    Button   mSendBt;
    ServerThread serverThread;
    ClientThread clientThread;
    ArrayAdapter<String> dataListAdapter;
    Handler uiHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case Params.MSG_REV_A_CLIENT:
                    Log.e("Tag", "--------- uihandler set device name, go to data frag");
                    BluetoothDevice clientDevice = (BluetoothDevice) msg.obj;
                    receiveClient(clientDevice);

                    break;
                case Params.MSG_CONNECT_TO_SERVER:
                    Log.e("TAG", "--------- uihandler set device name, go to data frag");
                    BluetoothDevice serverDevice = (BluetoothDevice) msg.obj;
                    connectServer(serverDevice);

                    break;
                case Params.MSG_SERVER_REV_NEW:
                    String newMsgFromClient = msg.obj.toString();
                    updateDataView(newMsgFromClient, Params.REMOTE);
                    break;
                case Params.MSG_CLIENT_REV_NEW:
                    String newMsgFromServer = msg.obj.toString();
                    updateDataView(newMsgFromServer, Params.REMOTE);
                    break;
                case Params.MSG_WRITE_DATA:
                    String dataSend = msg.obj.toString();
                    updateDataView(dataSend, Params.ME);
                    writeData(dataSend);
                    break;

            }
        }
    };
    @BindView(R.id.tv_shebei)
    TextView mTvShebei;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //获取本地蓝牙适配器
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        //判断蓝牙功能是否存在、
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "该设备不支持蓝牙设备", Toast.LENGTH_SHORT).show();
            return;
        }
//        //获取名字  mac地址
//        String name = bluetoothAdapter.getName();
//        String mac = bluetoothAdapter.getAddress();
//        //获取当前蓝牙的状态、
//        int state = bluetoothAdapter.getState();
//        switch (state) {
//            case BluetoothAdapter.STATE_ON:
//                Toast.makeText(this, "蓝牙已经打开", Toast.LENGTH_SHORT).show();
//                break;
//            case BluetoothAdapter.STATE_TURNING_ON:
//                Toast.makeText(this, "蓝牙正在打开。。", Toast.LENGTH_SHORT).show();
//                break;
//            case BluetoothAdapter.STATE_TURNING_OFF:
//                Toast.makeText(this, "蓝牙正在关闭。。", Toast.LENGTH_SHORT).show();
//                break;
//            case BluetoothAdapter.STATE_OFF:
//                Toast.makeText(this, "蓝牙已经关闭", Toast.LENGTH_SHORT).show();
//                break;
//        }
        ButterKnife.bind(this);
        initListener();
    }

    /**
     * 向 socket 写入发送的数据
     *
     * @param dataSend
     */
    public void writeData(String dataSend) {
        if (serverThread != null) {
            serverThread.write(dataSend);
        } else if (clientThread != null) {
            clientThread.write(dataSend);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        intentFilter = new IntentFilter();
        btReceiver = new MyBtReceiver();
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        MainActivity.this.registerReceiver(btReceiver, intentFilter);
        // 蓝牙已开启
        if (bluetoothAdapter.isEnabled()) {
            showBondDevice();
            // 默认开启服务线程监听
            if (serverThread != null) {
                serverThread.cancel();
            }
            Log.e("tag", "-------------- new server thread");
            serverThread = new ServerThread(bluetoothAdapter, uiHandler);
            new Thread(serverThread).start();
        }

        resultList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // 关闭服务器监听
                if (serverThread != null) {
                    serverThread.cancel();
                    serverThread = null;
                    Log.e("TAg", "---------------client item click , cancel server thread ," +
                            "server thread is null");
                }
                BluetoothDevice device = deviceList.get(position);
                // 开启客户端线程，连接点击的远程设备
                clientThread = new ClientThread(bluetoothAdapter, device,uiHandler);
                new Thread(clientThread).start();
                // 通知 ui 连接的服务器端设备
                Message message = new Message();
                message.what = Params.MSG_CONNECT_TO_SERVER;
                message.obj = device;
                uiHandler.sendMessage(message);

            }
        });
    }

    List<BluetoothDevice> deviceList = new ArrayList<>();

    /**
     * 广播接受器
     */
    private class MyBtReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                toast("开始搜索 ...");
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                toast("搜索结束");
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (isNewDevice(device)) {
                    deviceList.add(device);
                    listAdapter.notifyDataSetChanged();
                    Log.e("Tag", "---------------- " + device.getName());
                }
            }
        }
    }

    /**
     * 判断搜索的设备是新蓝牙设备，且不重复
     *
     * @param device
     * @return
     */
    private boolean isNewDevice(BluetoothDevice device) {
        boolean repeatFlag = false;
        for (BluetoothDevice d :
                deviceList) {
            if (d.getAddress().equals(device.getAddress())) {
                repeatFlag = true;
            }
        }
        //不是已绑定状态，且列表中不重复
        return device.getBondState() != BluetoothDevice.BOND_BONDED && !repeatFlag;
    }

    /**
     * Toast 提示
     */
    public void toast(String str) {
        Toast.makeText(MainActivity.this, str, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        MainActivity.this.unregisterReceiver(btReceiver);
    }

    /**
     * 设备列表的adapter
     */
    private class MyListAdapter extends BaseAdapter {

        public MyListAdapter() {
        }

        @Override
        public int getCount() {
            return deviceList.size();
        }

        @Override
        public Object getItem(int position) {
            return deviceList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = View.inflate(MainActivity.this, R.layout.layout_item_bt_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceName = (TextView) convertView.findViewById(R.id.device_name);
                viewHolder.deviceMac = (TextView) convertView.findViewById(R.id.device_mac);
                viewHolder.deviceState = (TextView) convertView.findViewById(R.id.device_state);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            int code = deviceList.get(position).getBondState();
            String name = deviceList.get(position).getName();
            String mac = deviceList.get(position).getAddress();
            String state;
            if (name == null || name.length() == 0) {
                name = "未命名设备";
            }
            if (code == BluetoothDevice.BOND_BONDED) {
                state = "已配对设备";
                viewHolder.deviceState.setTextColor(getResources().getColor(R.color.green));
            } else {
                state = "可用设备";
                viewHolder.deviceState.setTextColor(getResources().getColor(R.color.red));
            }
            if (mac == null || mac.length() == 0) {
                mac = "未知 mac 地址";
            }
            viewHolder.deviceName.setText(name);
            viewHolder.deviceMac.setText(mac);
            viewHolder.deviceState.setText(state);
            return convertView;
        }

    }
    /**
     * 与 adapter 配合的 viewholder
     */
    static class ViewHolder {
        public TextView deviceName;
        public TextView deviceMac;
        public TextView deviceState;
    }
    private void initListener() {
        mBtClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //关闭蓝牙
                bluetoothAdapter.disable();
            }
        });
        mBtQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 蓝牙未打开，询问打开
                if (!bluetoothAdapter.isEnabled()) {
                    Toast.makeText(MainActivity.this, "请先打开蓝牙", Toast.LENGTH_SHORT).show();
                } else {
                    //搜索蓝牙设备
                    //如果点击了搜索，清空适配器,开始搜索蓝牙设备
                    if (bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.cancelDiscovery();
                    }
                    if (Build.VERSION.SDK_INT >= 6.0) {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                Params.MY_PERMISSION_REQUEST_CONSTANT);
                    }
                    //发现新设备
                    bluetoothAdapter.startDiscovery();
                    listAdapter = new MyListAdapter();
                    resultList.setAdapter(listAdapter);
                    listAdapter.notifyDataSetChanged();

                }
            }
        });
        mSendBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //远程发送消息
                String msgSend = mInputEt.getText().toString();
                if(TextUtils.isEmpty(msgSend)){
                    Toast.makeText(MainActivity.this,"请输入发送消息",Toast.LENGTH_SHORT).show();
                }else{
                Message message = new Message();
                message.what = Params.MSG_WRITE_DATA;
                message.obj = msgSend;
                uiHandler.sendMessage(message);
                mInputEt.setText("");
                }

            }
        });
        dataListAdapter = new ArrayAdapter<String>(MainActivity.this, R.layout.layout_item_new_data);
        mShowDataLv.setAdapter(dataListAdapter);

    }
    BluetoothDevice remoteDevice;
    /**
     * 显示新消息
     *
     * @param newMsg
     */
    public void updateDataView(String newMsg, int role) {

        if (role == Params.REMOTE) {
            String remoteName = remoteDevice.getName() == null ? "未命名设备" : remoteDevice.getName();
            newMsg = remoteName + " : " + newMsg;
        } else if (role == Params.ME) {
            newMsg = "我 : " + newMsg;
        }
        dataListAdapter.add(newMsg);
    }

    /**
     * 显示连接远端(客户端)设备
     */
    public void receiveClient(BluetoothDevice clientDevice) {
        this.remoteDevice = clientDevice;
        mTvShebei.setText("连接设备: " + remoteDevice.getName());
    }

    /**
     * 客户端连接服务器端设备后，显示
     *
     * @param serverDevice
     */
    public void connectServer(BluetoothDevice serverDevice) {
        this.remoteDevice = serverDevice;
        mTvShebei.setText("连接设备: " + remoteDevice.getName());
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // TODO request success
                    // 运行时权限已授权
                }
                break;
        }
    }


    /**
     * 用户打开蓝牙后，显示已绑定的设备列表
     */
    private void showBondDevice() {
        deviceList.clear();
        // 查询已绑定设备
        Set<BluetoothDevice> tmp = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice d :
                tmp) {
            deviceList.add(d);
        }
    }

    @OnClick(R.id.bt_open)
    public void onViewClicked() {
        //关闭状态下，打开本地蓝牙设备
        //判断蓝牙是否已经打开
        if (bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "蓝牙已经处于打开状态。。", Toast.LENGTH_SHORT).show();
            //关闭蓝牙
            bluetoothAdapter.disable();
        } else {
            //调用系统Api打开
//            Intent open = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(open, EXQUEAT_OPEN);

            // 强行打开蓝牙
             bluetoothAdapter.enable();
            //开启被其它蓝牙设备发现的功能
            if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                //设置为一直开启
                i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
                startActivity(i);
            }




        }
    }

//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if (EXQUEAT_OPEN == requestCode) {
//            if (resultCode == RESULT_CANCELED) {
//                Toast.makeText(this, "打开蓝牙失败", Toast.LENGTH_SHORT).show();
//            } else {
//                Toast.makeText(this, "打开蓝牙成功", Toast.LENGTH_SHORT).show();
//
//            }
//
//        }
//
//    }


}
