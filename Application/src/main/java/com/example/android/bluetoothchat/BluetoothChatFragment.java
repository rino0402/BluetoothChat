/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothchat;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.camera2.params.InputConfiguration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.common.logger.Log;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Calendar;
import java.text.SimpleDateFormat;

/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
public class BluetoothChatFragment extends Fragment {

    private static final String TAG = "BluetoothChatFragment";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
/*
2016.4.28 kubota
    static {
        REQUEST_CONNECT_DEVICE_INSECURE = 2;
        REQUEST_CONNECT_DEVICE_SECURE = 1;
    }
*/
    private static final int REQUEST_ENABLE_BT = 3;

    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;

    private String mBarCode = null; // バーコード文字列
    private String mQty = null;     // 印刷枚数
    private String mAddress = null; //Bluetoothアドレス
    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mConversationArrayAdapter;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Member object for the chat services
     */
    private BluetoothChatService mChatService = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }


    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mChatService == null) {
            setupChat();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bluetooth_chat, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mConversationView = (ListView) view.findViewById(R.id.in);
        mOutEditText = (EditText) view.findViewById(R.id.edit_text_out);
        mSendButton = (Button) view.findViewById(R.id.button_send);
    }

    /**
     * チャット準備
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
//        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.message);

        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText.setOnEditorActionListener(mWriteListener);
        // 数字キーボードを表示
        mOutEditText.setRawInputType(Configuration.KEYBOARD_QWERTY);
//        mOutEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        // Initialize the send button with a listener that for click events
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    TextView textView = (TextView) view.findViewById(R.id.edit_text_out);
                    String message = textView.getText().toString();
                    inputText(message);
                }
            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(getActivity(), mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
        // アプリ名 バージョン表示
        addListView(getResources().getText(R.string.app_name).toString()
                    + " " + getResources().getText(R.string.version).toString());
        addListView("DaVinci本体のバーコードを読み込んで下さい。");
    }

    /**
     * Makes this device discoverable.
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
//        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
//            if ( mAddress != null) {
//                addListView("再接続: " + mAddress);
//                connectDeviceByAddress(mAddress, false);
//            }else{
//                addListView("未接続: アドレスなし");
//                Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
//            }
//            return;
//        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);
          // Reset out string buffer to zero and clear the edit text field
//            mOutStringBuffer.setLength(0);
//            mOutEditText.setText(mOutStringBuffer);
        }
    }

    /**
     * The action listener for the EditText widget, to listen for the return key
     * テキスト入力イベント KeyEvent.ACTION_UP
     */
    private TextView.OnEditorActionListener mWriteListener
            = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                inputText(message);
            }
            return true;
        }
    };

    /**
     *  テキストボックスクリア
     */
    private void clearText() {
        mOutStringBuffer.setLength(0);
        mOutEditText.setText(mOutStringBuffer);
    }

    /**
     * BlueTooth MACアドレス
     */
    private Boolean isMACAdrs(String macAdrs) {
//        String reg = "^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$";
        String reg = "^([0-9A-F]{2}){6}$";

        Pattern p = Pattern.compile(reg);
        Matcher m = p.matcher(macAdrs);

        return m.find();
    }

    /**
     * 印刷枚数(数字３桁)かどうか
     */
    private boolean isQty(String s) {
        s = s.trim();
        if (s.length() == 0) {
            // 空白は 1
            mQty = "1";
            return true;
        }
        // 数字３桁(999)
        Pattern p = Pattern.compile("^\\d{1,3}$");
        Matcher m = p.matcher(s);
        if (!m.find()) {
            return false;
        }
        mQty = s;
        return true;
    }

    /**
     * 入力処理
     */
    private void inputText(String s) {
        s = s.trim();
        if (isQty(s) == true) {
            //数字(枚数)
            //現在日時を取得する
            Calendar c = Calendar.getInstance();
            //フォーマットパターンを指定して表示する
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.M.d");
            String mDate = sdf.format(c.getTime());

            addListView("印刷開始");
            addListView(mConnectedDeviceName + "：" + mAddress);
            addListView("品番：" + mBarCode);
            addListView("日付：" + mDate);
            addListView("枚数：" + mQty);
//            addListView("接続: " + mAddress);
//            connectDeviceByAddress(mAddress,false);
            if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
                if ( mAddress != null) {
                    addListView("再接続: " + mAddress);
                    connectDeviceByAddress(mAddress, false);
                }else{
                    addListView("DaVinciと切断されました。本体のバーコードを読み込んで下さい。");
                    return;
                }
            }
            sendMessage("JOB\n");
            sendMessage("DEF MK=1,MD=1,DR=2,DK=12,MS=39,PO=45,TO=110,PH=344,PW=384,UM=12,BM=12,XO=0,AF=1\n");
            sendMessage("START\n");
            sendMessage("BCD TP=7,X=0,Y=0,NW=1,RA=2,MG=1,HT=80\n");
            sendMessage(mBarCode + "\n");
            sendMessage("FONT TP=7,CS=0,LG=60,WD=48,LS=0\n");
            sendMessage("TEXT X=0,Y=120,L=1\n");
            sendMessage(mBarCode + "\n");
            sendMessage("FONT TP=27,CS=0,LG=32,WD=32,LS=0\n");
            sendMessage("TEXT X=0,Y=260,L=1\n");
            sendMessage(mDate + "\n");
            sendMessage("TEXT X=250,Y=260,L=1,NS=1,NE=3,NK=1,NI=1,NZ=1,NB=0\n");
            sendMessage("001/" + mQty + "\n");
            sendMessage("QTY P=" + mQty + "\n");
            sendMessage("END\n");
            sendMessage("JOBE\n");
            addListView("印刷終了");
            //　入力欄をクリア
            clearText();
            return;
        }
        if (isMACAdrs(s) == true) {
            // 画面にMACアドレスを表示
            mAddress = s.substring(0,2)
                        + ":" + s.substring(2,4)
                        + ":" + s.substring(4,6)
                        + ":" + s.substring(6,8)
                        + ":" + s.substring(8,10)
                        + ":" + s.substring(10);
            addListView("接続: " + mAddress);
            // MACアドレス
            connectDeviceByAddress(mAddress,false);
            //　入力欄をクリア
            clearText();
            return;
        }
        // 品番
        mBarCode = s;
        addListView("品番:" + mBarCode);
        //　入力欄をクリア
        clearText();
//        mOutEditText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_CLASS_TEXT);
        return;
    }

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }
    /*
    *    リストビューにメッセージ表示
    */
    private void addListView(String msg) {
        if (null != mConversationArrayAdapter) {
            // メッセージを先頭行に追加
//            mConversationArrayAdapter.insert(msg,0);
            // メッセージを最下行に追加
            mConversationArrayAdapter.add(msg);
        }
    }
    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            addListView(getString(R.string.title_connected_to, mConnectedDeviceName));
//                          mConversationArrayAdapter.clear();
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            addListView(getString(R.string.title_connecting));
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
//                            setStatus(R.string.title_not_connected);
//                            addListView(getString(R.string.title_not_connected));
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
 //                   byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
//                    String writeMessage = new String(writeBuf);
//                    writeMessage = writeMessage.replaceAll("\n","");
//                  addListView(writeMessage);
                    break;
                case Constants.MESSAGE_READ:
//                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
//                    String readMessage = new String(readBuf, 0, msg.arg1);
//                    addListView(mConnectedDeviceName + ":  " + readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
//                        Toast.makeText(activity, "Connected to "
//                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
//                        Toast.makeText(activity, "Bt接続OK "
//                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                        addListView("Bt接続OK " + mConnectedDeviceName);
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
//                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
//                                Toast.LENGTH_SHORT).show();
                        addListView(msg.getData().getString(Constants.TOAST));
                    }
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }

    /**
     * Establish connection with other divice
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    /**
     * Bluetooth接続 アドレス指定
     * @param address
     * @param secure
     */
    private void connectDeviceByAddress(String address,boolean secure) {
//        DeviceListActivity.EXTRA_DEVICE_ADDRESS = "ANDES";
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return false;
    }

}
