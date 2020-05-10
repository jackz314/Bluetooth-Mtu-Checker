package com.jackz314.blemtu

import android.app.Activity
import android.bluetooth.*
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jackz314.blemtu.databinding.ActivityMainBinding
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val REQUEST_ENABLE_BT = 1006
    val DISCOVERABLE_TIME = 120
    val TAG = MainActivity::class.java.simpleName
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var mBleMac: String? = null
    private var mPreviousMac: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        editText.filters = editText.filters + InputFilter.AllCaps()
        editText.addTextChangedListener(object: TextWatcher{
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int){
                val enteredMac = s.toString()
                val cleanMac = purifyMac(s.toString())
                var formattedMac = formatMacAddress(cleanMac)
                val selectionStart: Int = editText.selectionStart
                formattedMac = handleColonDeletion(enteredMac, formattedMac, selectionStart)
                val lengthDiff: Int = formattedMac.length - enteredMac.length
                setMacEdit(cleanMac, formattedMac, selectionStart, lengthDiff);
            }

            override fun afterTextChanged(s: Editable?) {
                val ss = s.toString()
                if(BluetoothAdapter.checkBluetoothAddress(ss)){//valid string
                    button.isEnabled = true
                    mBleMac = ss
                }else{
                    button.isEnabled = false
                }
            }

            /**
             * Removes TextChange listener, sets MAC EditText field value,
             * sets new cursor position and re-initiates the listener.
             * @param cleanMac          Clean MAC address.
             * @param formattedMac      Formatted MAC address.
             * @param selectionStart    MAC EditText field cursor position.
             * @param lengthDiff        Formatted/Entered MAC number of characters difference.
             */
            private fun setMacEdit(
                cleanMac: String,
                formattedMac: String,
                selectionStart: Int,
                lengthDiff: Int
            ) {
                editText.removeTextChangedListener(this)
                if (cleanMac.length <= 12) {
                    editText.setText(formattedMac)
                    editText.setSelection(Math.max(selectionStart + lengthDiff, 0))
                    mPreviousMac = formattedMac
                } else {
                    if(mPreviousMac != null){
                        editText.setText(mPreviousMac)
                        editText.setSelection(mPreviousMac!!.length)
                    }
                }
                editText.addTextChangedListener(this)
            }
        })
    }

    /**
     * Gets MAC address current colon count.
     * @param formattedMac      Formatted MAC address.
     * @return                  Current number of colons in MAC address.
     */
    private fun colonCount(formattedMac: String): Int {
        return formattedMac.replace("[^:]".toRegex(), "").length
    }

    /**
     * Upon users colon deletion, deletes MAC character preceding deleted colon as well.
     * @param enteredMac            User input MAC.
     * @param formattedMac          Formatted MAC address.
     * @param selectionStart        MAC EditText field cursor position.
     * @return                      Formatted MAC address.
     */
    private fun handleColonDeletion(
        enteredMac: String,
        formattedMac: String,
        selectionStart: Int
    ): String {
        var formattedMac = formattedMac
        if (mPreviousMac != null && mPreviousMac!!.length > 1) {
            val previousColonCount: Int = colonCount(mPreviousMac!!)
            val currentColonCount: Int = colonCount(enteredMac)
            if (currentColonCount < previousColonCount) {
                formattedMac =
                    formattedMac.substring(0, Math.max(selectionStart - 1,0)) + formattedMac.substring(
                        selectionStart
                    )
                val cleanMac: String = purifyMac(formattedMac)
                formattedMac = formatMacAddress(cleanMac)
            }
        }
        return formattedMac
    }

    /**
     * Strips all characters from a string except A-F and 0-9.
     * @param mac       User input string.
     * @return          String containing MAC-allowed characters.
     */
    private fun purifyMac(mac: String): String {
        return mac.toUpperCase().replace("[^A-Fa-f0-9]".toRegex(), "")
    }

    /**
     * Adds a colon character to an unformatted MAC address after
     * every second character (strips full MAC trailing colon)
     * @param cleanMac Unformatted MAC address.
     * @return Properly formatted MAC address.
     */
    private fun formatMacAddress(cleanMac:String):String {
        var grouppedCharacters = 0
        var formattedMac = ""
        for (i in 0 until cleanMac.length)
        {
            formattedMac += cleanMac.get(i)
            ++grouppedCharacters
            if (grouppedCharacters == 2)
            {
                formattedMac += ":"
                grouppedCharacters = 0
            }
        }
        // Removes trailing colon for complete MAC address
        if (cleanMac.length == 12)
            formattedMac = formattedMac.substring(0, formattedMac.length - 1)
        return formattedMac
    }

    private var mScanning: Boolean = false
    private var mHandler: Handler? = null

    private val leScanCallback = BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
        runOnUiThread {
            Log.i(TAG, "LE Device: ${device.name} ${device.address} ${device.type}")
        }
    }

    private fun scanLeDevice(handler: Handler, enable: Boolean) {
        when (enable) {
            true -> {
                // Stops scanning after a pre-defined scan period.
                handler.postDelayed({
                    mScanning = false
                    Log.i(TAG, "stop scanning")
                    bluetoothAdapter?.stopLeScan(leScanCallback)
                }, 15000)
                mScanning = true
                Log.i(TAG, "start scanning")
                bluetoothAdapter?.startLeScan(leScanCallback)
            }
            else -> {
                mScanning = false
                Log.i(TAG, "disabled, stop scanning")
                bluetoothAdapter?.stopLeScan(leScanCallback)
            }
        }
    }

    override fun onDestroy() {
        if(mHandler != null) scanLeDevice(mHandler!!, false)
        super.onDestroy()
    }

    fun checkMTUBtn(v : View){
        startCheckMTU()
    }

    fun startCheckMTU(){
        button.isEnabled = false
        textView.text = "Checking..."
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            textView.text = "Your device doesn't support Bluetooth :("
        }else{
            if (bluetoothAdapter?.isEnabled == false) {
                Toast.makeText(applicationContext,"Please enable Bluetooth", Toast.LENGTH_SHORT).show()
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }else{//real stuff
                mHandler = Handler.createAsync(Looper.getMainLooper())
                scanLeDevice(mHandler!!, true)
                checkMTU()
            }
        }
    }

    fun checkMTU(){
        //set self discoverable for 2 minutes
        val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_TIME)
        }
        startActivity(discoverableIntent)
        //don't care about the result for now
//        OpenSocketAsServerThread().run()//act as server, not using
        if(mBleMac != null){
            val blDevice: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(mBleMac);
            if(blDevice != null){
//                ConnectAsClientThread(blDevice)
                var bluetoothGatt: BluetoothGatt? = null

                //call back stuff
                val gattCallback = object : BluetoothGattCallback() {

                    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                        super.onMtuChanged(gatt, mtu, status)
                        Log.i(TAG, "New MTU: "+mtu)
                        if(status == BluetoothGatt.GATT_SUCCESS){
                            Log.i(TAG, "new mtu succeed")
                            gatt?.close()
                            runOnUiThread {
                                button.isEnabled = true
                                textView.text = "Bluetooth MTU Max Size: " + mtu
                            }
                        }else{
                            Log.i(TAG, "new mtu failed")
                            val newMtu = mtu/2
                            if(newMtu > 0) gatt?.requestMtu(newMtu)//retry with lower mtu
                            else{
                                gatt?.close()
                                runOnUiThread {
                                    button.isEnabled = true
                                    textView.text = "Check failed, your device is probably not supported"
                                }
                            }
                        }
                    }

                    override fun onConnectionStateChange(
                        gatt: BluetoothGatt,
                        status: Int,
                        newState: Int
                    ) {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                Log.i(TAG, "Connected to GATT server.")
                                val result = gatt.requestMtu(1024)//will fall back to the agreed-upon size in onMtuChanged
                                if(!result){
                                    Log.i(TAG, "Request MTU failed")
                                    button.isEnabled = true
                                    textView.text = "Check failed, your device is probably not supported"
                                    gatt.close()
                                }
                                Log.i(TAG, "Attempting to start service discovery: " +
                                        bluetoothGatt?.discoverServices())
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                Log.i(TAG, "Disconnected from GATT server.")
                                runOnUiThread {
                                    button.isEnabled = true
                                    textView.text = "Check failed, Connection lost"
                                }
//                                gatt.connect()
                            }
                            else->{
                                Log.i(TAG, "Connection state change, new state: " + newState + " status: " + status)
                            }
                        }
                    }

                    // New services discovered
                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        when (status) {
                            BluetoothGatt.GATT_SUCCESS -> Log.i(TAG, "New Services Discovered")
                            else -> Log.w(TAG, "onServicesDiscovered received: $status")
                        }
                    }

                    override fun onCharacteristicWrite(
                        gatt: BluetoothGatt?,
                        characteristic: BluetoothGattCharacteristic?,
                        status: Int
                    ) {
                        Log.i(TAG, "Characteristic write: $status")
                    }

                    // Result of a characteristic read operation
                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                    ) {
                        Log.i(TAG, "Characteristic read $status")
                        when (status) {
                            BluetoothGatt.GATT_SUCCESS -> {
                                Log.i(TAG, "Characteristic read successful")
                            }
                        }
                    }
                }
                Log.i(TAG, "Got device, connecting: " + blDevice.address)
                bluetoothGatt = blDevice.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                val result = bluetoothGatt.requestMtu(512)//will fall back to the agreed-upon size in onMtuChanged
//                bluetoothGatt.connect()
            }
        }
    }

    fun manageConnectedSocket(socket: BluetoothSocket?){
        if(socket != null){
            textView.text = "Connected to target, checking MTU..."

        }
    }

 /*   private inner class ConnectBLEThread(device: BluetoothDevice) : Thread() {
        override fun run() {

        }
    }*/

    private inner class ConnectAsClientThread(device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(UUID.randomUUID())
        }

        override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter?.cancelDiscovery()

            mmSocket?.use { socket ->
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                try {
                    socket.connect()

                    // The connection attempt succeeded. Perform work associated with
                    // the connection in a separate thread.
                    manageConnectedSocket(socket)
                } catch (e: IOException) {
                    //timed out or failed
                    Toast.makeText(applicationContext,"Connection failed, please check the MAC address or try again", Toast.LENGTH_SHORT).show()
                }


            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }

    private inner class OpenSocketAsServerThread : Thread() {

        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord("BLEMTU", UUID.randomUUID())
        }

        override fun run() {
            // Keep listening until exception occurs or a socket is returned.
            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's accept() method failed", e)
                    shouldLoop = false
                    null
                }
                socket?.also {
                    manageConnectedSocket(it)
                    mmServerSocket?.close()
                    shouldLoop = false
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        fun cancel() {
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) startCheckMTU()

    }
}
