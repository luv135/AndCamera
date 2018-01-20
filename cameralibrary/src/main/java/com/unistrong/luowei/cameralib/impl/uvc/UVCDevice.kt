package com.unistrong.luowei.cameralib.impl.uvc

import android.hardware.usb.UsbDevice
import com.unistrong.luowei.cameralib.base.IDevice

/**
 * Created by luowei on 2017/9/23.
 */
data class UVCDevice(val device: UsbDevice) : IDevice