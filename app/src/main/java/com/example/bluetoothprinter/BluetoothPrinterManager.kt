package com.example.bluetoothprinter

import android.bluetooth.BluetoothManager
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import android.util.Log
// Classe para gerenciar conexões Bluetooth e impressão na PT-260
class BluetoothPrinterManager(
    private val context: Context,
    private val callback: PrinterCallback
) {
    //private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // UUID para SPP
    private var discoveryReceiver: BroadcastReceiver? = null

    // Interface para callbacks
    interface PrinterCallback {
        fun onBluetoothStateChanged(enabled: Boolean)
        fun onDeviceDiscovered(device: BluetoothDevice)
        fun onConnectionStateChanged(connected: Boolean, device: BluetoothDevice?)
        fun onPrintSuccess()
        fun onError(message: String)
    }
    private val discoveredDevicesMap = mutableMapOf<String, BluetoothDevice>()
    private val pairingRequestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_PAIRING_REQUEST) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val pin = "0000" // Altere conforme o PIN padrão da sua impressora
                device?.setPin(pin.toByteArray())
                abortBroadcast()
            }
        }
    }

    // Verifica se o Bluetooth é suportado e está ativado
   /* fun isBluetoothSupportedAndEnabled(): Boolean {
        if (bluetoothAdapter == null) {
            callback.onError("Dispositivo não suporta Bluetooth")
            return false
        }
        if (!bluetoothAdapter.isEnabled) {
            callback.onBluetoothStateChanged(false)          return false
        }
        return true
    }*/



    // Verifica permissões necessárias
    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
    fun isBluetoothSupportedAndEnabled(): Boolean {
        if (bluetoothAdapter?.isEnabled != true) {
            callback.onError("Dispositivo Bluetooth está desativado")
            callback.onBluetoothStateChanged(false)
            return false
        }
        return true
    }

    // Inicia a descoberta de dispositivos
    /*fun startDiscovery() {
        if (!isBluetoothSupportedAndEnabled() || !hasBluetoothPermissions()) {
            callback.onError("Bluetooth desativado ou permissões ausentes")
            return
        }

        bluetoothAdapter?.cancelDiscovery()

        discoveryReceiver = object : BroadcastReceiver() {
            /*override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        device?.let { callback.onDeviceDiscovered(it) }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        callback.onError("Descoberta de dispositivos finalizada")
                    }
                }
            }*/
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        device?.let { callback.onDeviceDiscovered(it) }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        callback.onError("Descoberta de dispositivos finalizada")
                    }
                }
            }
        }*/
    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (!isBluetoothSupportedAndEnabled() || !hasBluetoothPermissions()) {
            callback.onError("Bluetooth desativado ou permissões ausentes")
            return
        }

        discoveredDevicesMap.clear()
        bluetoothAdapter?.cancelDiscovery()

        discoveryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        device?.let {
                            val address = it.address
                            if (!discoveredDevicesMap.containsKey(address)) {
                                discoveredDevicesMap[address] = it
                                callback.onDeviceDiscovered(it)
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        callback.onError("Descoberta de dispositivos finalizada")
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        context.registerReceiver(discoveryReceiver, filter)
        bluetoothAdapter?.startDiscovery()


    }

    // Cancela a descoberta
    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        discoveryReceiver?.let { context.unregisterReceiver(it) }
        bluetoothAdapter?.cancelDiscovery()
    }
    //descobrir dispositivos descobertos
    fun getDiscoveredDevice(address: String): BluetoothDevice? {
        return discoveredDevicesMap[address]
    }


    // Conecta a um dispositivo pareado
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        if (!isBluetoothSupportedAndEnabled() || !hasBluetoothPermissions()) {
            callback.onError("Bluetooth desativado ou permissões ausentes")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                bluetoothAdapter?.cancelDiscovery()

                val filter = IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST)
                filter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
                context.registerReceiver(pairingRequestReceiver, filter)

                if (device.bondState == BluetoothDevice.BOND_NONE) {
                    device.createBond() // Inicia pareamento (dispara o Broadcast do PIN)
                }

                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                callback.onConnectionStateChanged(true, device)
            } catch (e: IOException) {
                callback.onError("Falha na conexão: ${e.message}")
                closeConnection()
            }
        }
    }

    /*Função anterior grok
    fun connectToDevice(device: BluetoothDevice) {
        if (!isBluetoothSupportedAndEnabled() || !hasBluetoothPermissions()) {
            callback.onError("Bluetooth desativado ou permissões ausentes")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                bluetoothAdapter?.cancelDiscovery()
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                callback.onConnectionStateChanged(true, device)
            } catch (e: IOException) {
                callback.onError("Falha na conexão ")
                Log.d("BluetoothPrinter", "erro discov")
                closeConnection()
            }
        }
    }*/

    // Imprime uma etiqueta com texto e código de barras
    @SuppressLint("MissingPermission")
    /*fun printLabel(text: String, barcode: String? = null) {
        if (bluetoothSocket == null || outputStream == null) {
            callback.onError("Nenhuma conexão ativa")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Inicializa a impressora (comando ESC/POS)
                outputStream?.write(byteArrayOf(0x1B, 0x40)) // ESC @ (Inicializar)

                // Configura alinhamento à esquerda
                outputStream?.write(byteArrayOf(0x1B, 0x61, 0x00)) // ESC a 0

                // Imprime texto
                outputStream?.write(text.toByteArray(Charsets.UTF_8))
                outputStream?.write(byteArrayOf(0x0A)) // Nova linha

                // Imprime código de barras (se fornecido)
                barcode?.let {
                    // Configura CODE128
                    outputStream?.write(byteArrayOf(0x1D, 0x6B, 0x49, it.length.toByte()))
                    outputStream?.write(it.toByteArray(Charsets.UTF_8))
                    outputStream?.write(byteArrayOf(0x0A)) // Nova linha
                }

                // Avança o papel e corta (se suportado)
                outputStream?.write(byteArrayOf(0x1D, 0x56, 0x00)) // GS V 0 (Corte total)
                outputStream?.flush()
                callback.onPrintSuccess()
            } catch (e: IOException) {
                callback.onError("Erro ao imprimir: ${e.message}")
                closeConnection()
            }
        }
    }*/
    fun printLabel(text: String, barcode: String? = null) {
        if (bluetoothSocket == null || outputStream == null) {
            callback.onError("Nenhuma conexão ativa")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                //Log.d("BluetoothPrinter", "Inicializando impressora")
                //outputStream?.write(byteArrayOf(0x1B, 0x40))
                //Log.d("BluetoothPrinter", "Configurando alinhamento")
                //outputStream?.write(byteArrayOf(0x1B, 0x61, 0x00))
                //Log.d("BluetoothPrinter", "Enviando texto: $text")
                //outputStream?.write(text.toByteArray(Charsets.UTF_8))
                //outputStream?.write(byteArrayOf(0x0A))
                /*barcode?.let {
                    Log.d("BluetoothPrinter", "Enviando código de barras: $it")
                    outputStream?.write(byteArrayOf(0x1D, 0x6B, 0x49, it.length.toByte()))
                    outputStream?.write(it.toByteArray(Charsets.UTF_8))
                    outputStream?.write(byteArrayOf(0x0A))
                }*/
                //Log.d("BluetoothPrinter", "Enviando comando de corte")
                //outputStream?.write(byteArrayOf(0x1D, 0x56, 0x00))
                //    Log.d("BluetoothPrinter", "Flushing dados")
                /*val tsplCommand = """
    SIZE 40 mm,30 mm
    GAP 0 mm,0
    CLS
    TEXT 10,10,"3",0,1,1,"ola mundo"
    PRINT 1
""".trimIndent()

                outputStream?.write(tsplCommand.toByteArray(Charsets.UTF_8))
                //outputStream.flush()
                outputStream?.flush()*/
                val textoMultilinha = listOf(
                    "Produto: $text.",
                    "Data: $barcode"
                )

                val comandosTexto = textoMultilinha.mapIndexed { i, linha ->
                    val y = 10 + (i * 30) // cada linha 30px abaixo da anterior
                    """TEXT 10,$y,"3",0,1,1,"$linha""""
                }.joinToString("\n")

                val comandoCompleto = """
    SIZE 40 mm,30 mm
    BLINE 30 mm,0
    CLS
    $comandosTexto
    PRINT 1
""".trimIndent()

                outputStream?.write(comandoCompleto.toByteArray(Charsets.UTF_8))
                outputStream?.flush()

                callback.onPrintSuccess()
            } catch (e: IOException) {
                Log.e("BluetoothPrinter", "Erro: ${e.message}")
                callback.onError("Erro ao imprimir: ${e.message}")
                closeConnection()
            }
        }
    }

    // Fecha a conexão
    fun closeConnection() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
            callback.onConnectionStateChanged(false, null)
        } catch (e: IOException) {
            callback.onError("Erro ao fechar conexão: ${e.message}")
        }
        try {
            context.unregisterReceiver(pairingRequestReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver já não registrado
        }
    }

    // Lista dispositivos pareados
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): Set<BluetoothDevice>? {
        if (!isBluetoothSupportedAndEnabled() || !hasBluetoothPermissions()) {
            callback.onError("Bluetooth desativado ou permissões ausentes")
            return null
        }
        return bluetoothAdapter?.bondedDevices
    }
}