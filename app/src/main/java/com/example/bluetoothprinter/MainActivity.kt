package com.example.bluetoothprinter

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), BluetoothPrinterManager.PrinterCallback {
    private lateinit var bluetoothManager: BluetoothPrinterManager
    private lateinit var deviceList: MutableList<String>
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private lateinit var devicesListView: ListView
    private lateinit var labelInput: EditText
    private lateinit var barcodeInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Pedir permissões
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED ||
                        checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED){
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN),
                    100)}else{
                        //pedir localização para versões abaixo de android 12
                        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
                            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),101) }
            }
        }*/

        bluetoothManager = BluetoothPrinterManager(this, this)
        devicesListView = findViewById(R.id.devicesListView)
        labelInput = findViewById(R.id.labelInput)
        barcodeInput = findViewById(R.id.barcodeInput)
        deviceList = mutableListOf()
        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        devicesListView.adapter = deviceAdapter

        // Botões
        findViewById<Button>(R.id.btnEnableBluetooth).setOnClickListener { enableBluetooth() }
        findViewById<Button>(R.id.btnDiscoverDevices).setOnClickListener { startDiscovery() }
        findViewById<Button>(R.id.btnPrintLabel).setOnClickListener {
            val text = labelInput.text.toString()
            val barcode = barcodeInput.text.toString().takeIf { it.isNotBlank() }
            if (text.isNotBlank()) {
                bluetoothManager.printLabel(text, barcode)
            } else {
                Toast.makeText(this, "Digite um texto para a etiqueta", Toast.LENGTH_SHORT).show()
            }
        }

        // Configura clique na lista de dispositivos
        devicesListView.setOnItemClickListener { _, _, position, _ ->
            val deviceAddress = deviceList[position].split("\n")[1]
            val device = bluetoothManager.getPairedDevices()?.find { it.address == deviceAddress }
            device?.let { bluetoothManager.connectToDevice(it) }
        }

        // Verifica permissões
        checkPermissions()
    }

    // Verifica e solicita permissões
    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.all { it.value }) {
            Toast.makeText(this, "Permissões concedidas", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissões necessárias não concedidas", Toast.LENGTH_LONG).show()
        }
    }

    // Ativa o Bluetooth
    private fun enableBluetooth() {
        if (!bluetoothManager.isBluetoothSupportedAndEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
        }
    }

    // Inicia a descoberta de dispositivos
    private fun startDiscovery() {
        deviceList.clear()
        deviceAdapter.notifyDataSetChanged()
        bluetoothManager.getPairedDevices()?.forEach { device ->
            deviceList.add("${device.name}\n${device.address}")
        }
        deviceAdapter.notifyDataSetChanged()
        bluetoothManager.startDiscovery()
    }

    override fun onBluetoothStateChanged(enabled: Boolean) {
        Toast.makeText(this, if (enabled) "Bluetooth ativado" else "Bluetooth desativado", Toast.LENGTH_SHORT).show()
    }

    override fun onDeviceDiscovered(device: BluetoothDevice) {
        deviceList.add("${device.name}\n${device.address}")
        deviceAdapter.notifyDataSetChanged()
    }

    override fun onConnectionStateChanged(connected: Boolean, device: BluetoothDevice?) {
        Toast.makeText(this, if (connected) "Conectado a ${device?.name}" else "Desconectado", Toast.LENGTH_SHORT).show()
    }

    override fun onPrintSuccess() {
        runOnUiThread { Toast.makeText(this, "Etiqueta impressa com sucesso", Toast.LENGTH_SHORT).show() }
    }

    override fun onError(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.stopDiscovery()
        bluetoothManager.closeConnection()
    }

    companion object {
        private const val REQUEST_ENABLE_BLUETOOTH = 1
    }
}