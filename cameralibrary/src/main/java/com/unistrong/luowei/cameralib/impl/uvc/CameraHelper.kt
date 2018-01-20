package com.unistrong.luowei.cameralib.impl.uvc

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.serenegiant.usb.DeviceFilter
import com.unistrong.luowei.cameralib.base.IDevice
import com.unistrong.luowei.cameralibrary.R
import com.unistrong.luowei.commlib.Log
import java.util.*
import kotlin.collections.ArrayList

/**
 * Created by luowei on 2017/9/25.
 */
object CameraHelper {
    fun getCameras(context: Context): Array<IDevice> {
        val filter = DeviceFilter.getDeviceFilters(context, R.xml.device_filter)
        val usb = getDeviceList(context, filter[0])
        val devices = arrayListOf<UVCDevice>()
        usb.forEach {
            Log.d("${it.productName}, ${it.productId}, ${it.vendorId}")
            devices.add(UVCDevice(it))
        }
        return devices.toTypedArray()
    }

    @Throws(IllegalStateException::class)
    private fun getDeviceList(context: Context, filter: DeviceFilter?): List<UsbDevice> {
        val mUsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = mUsbManager.deviceList
        val result = ArrayList<UsbDevice>()
        if (deviceList != null) {
            for (device in deviceList.values) {
//                Log.d("productName=${device.productName}, productId=${device.productId}, vendorId=${device.vendorId}")
                //device.vendorId==11388&&device.productId==293 android 设备
                if ((filter == null || filter.matches(device) && !filter.isExclude) && device.vendorId != 11388 && device.productId != 293) {
                    result.add(device)
                }
            }
        }
        return result
    }

    private val cameraAttachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            callback.forEach { it.invoke(device) }
        }
    }

//    private var callback: ((device: UsbDevice) -> Unit)? = null

    private val callback = ArrayList<(device: UsbDevice) -> Unit>()

    fun registerDevice(context: Application, callback: (device: UsbDevice) -> Unit) {
        if (this.callback.isEmpty()) {
            val intentFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            context.registerReceiver(cameraAttachReceiver, intentFilter)
        }
        this.callback.add(callback)
    }

    fun unregisterDevice(context: Application, callback: (device: UsbDevice) -> Unit) {
        if ( this.callback.remove(callback)&&this.callback.isEmpty()) {
            context.unregisterReceiver(cameraAttachReceiver)
        }
    }

    fun isPCCamera(device: UsbDevice): Boolean {
        //productName=USB 2.0 PC Camera, productId=14434, vendorId=1423
        return device.vendorId == 1423 && device.productId == 14434

    }

    fun isHDCamera(device: UsbDevice): Boolean {
        //productName=USB 2.0 HD Camera
        return device.vendorId == 1423 && device.productId == 14370

    }
}