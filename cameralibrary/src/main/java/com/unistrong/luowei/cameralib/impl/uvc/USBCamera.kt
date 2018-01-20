package com.unistrong.luowei.cameralib.impl.uvc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.hardware.usb.UsbDevice
import android.view.Surface
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.utils.HandlerThreadHandler
import com.serenegiant.widget.CameraViewInterface
import com.unistrong.luowei.cameralib.base.*
import com.unistrong.luowei.commlib.Log

/**
 * Created by luowei on 2017/9/23.
 *
 */

class USBCamera(val context: Context) : ICamera {
    override fun tackPicture(callback: (bitmap: Bitmap) -> Unit) {

    }

    private val DEBUG = true
    private lateinit var device: UsbDevice
    private var cfg = Config(currentResolution = Point(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT))
    private var mPreviewCallback: IPreviewCallback? = null

    override fun getCameras(): Array<IDevice> = CameraHelper.getCameras(context)


    /**
     * 线程阻塞,需异步
     */
    override fun tackPicture(): Bitmap? {
        if (isPreview()) {
            val bitmap = mUVCCameraView?.captureStillImage()
            return Bitmap.createBitmap(bitmap)
        }
        return null
    }

    /**
     * 打开摄像头
     */
    override fun open(device: IDevice, view: ICameraView): Boolean {
        mUSBMonitor.register()
        if (view !is UVCPreviewView) return false
        if (device !is UVCDevice) {
            return false
        }
        mUVCCameraView = view
        this.device = device.device
//        mUVCCameraView!!.surfaceTextureListener= object : TextureView.SurfaceTextureListener {
//            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
//
//            }
//
//            override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
//
//            }
//
//            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
//                close()
//                return false
//            }
//
//            override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
//                mUSBMonitor.requestPermission(this@USBCamera.device)
//            }
//
//        }
        mUVCCameraView!!.setCallback(object : CameraViewInterface.Callback {
            override fun onSurfaceCreated(view: CameraViewInterface?, surface: Surface?) {
                if (DEBUG) Log.d("surface created  request permission")
                mUSBMonitor.requestPermission(this@USBCamera.device)
            }

            override fun onSurfaceChanged(view: CameraViewInterface?, surface: Surface?, width: Int, height: Int) {
                if (DEBUG) Log.d("onSurfaceChanged ")
                mUSBMonitor.requestPermission(this@USBCamera.device)
            }

            override fun onSurfaceDestroy(view: CameraViewInterface?, surface: Surface?) {
                if (DEBUG) Log.d("onSurfaceDestroy and close ")
                close()
            }

        })
        if (mUVCCameraView!!.hasSurface()) {
//        if (mUVCCameraView!!.surfaceTexture != null) {
            if (DEBUG) Log.d("has surface request permission")
            mUSBMonitor.requestPermission(this@USBCamera.device)
        }
        return true
    }

    /**
     * 销毁该摄像头,此方法调用后,其他方法都不能调用,需要重新创建对象.
     */
    override fun close() {
        mUSBMonitor.unregister()
        synchronized(mSync) {
            preview = false
            mUVCCamera?.destroy()
            mUVCCamera = null
            mUSBMonitor.destroy()
        }
        mUVCCameraView = null
        mWorkerHandler?.looper?.quit()
        mWorkerHandler = null
    }

    override fun config(config: Config) {
        this.cfg = config
    }


    override fun getConfig(): Config = cfg


    override fun setPreviewCallback(callback: IPreviewCallback) {
        mPreviewCallback = callback
    }

    override fun startPreview() {
        if (!isPreview() && isActive) {
            mUSBMonitor.requestPermission(this@USBCamera.device)
        }
        /* if (!isPreview()) {
             synchronized(mSync) {
                 mUVCCamera?.startPreview()
             }
         }*/
    }

    override fun stopPreview() {
        if (isPreview() && isActive) {
            preview = false
            synchronized(mSync) {
                mUVCCamera?.destroy()
                mUVCCamera = null
            }
        }
        /*if (isPreview())
            synchronized(mSync) {
                mUVCCamera?.stopPreview()
            }*/
    }

    override fun isPreview(): Boolean = preview

    var mUVCCameraView: UVCPreviewView? = null
    private val mSync = Object()
    var isActive: Boolean = false
    var preview = false

    var mUSBMonitor: USBMonitor
    // ワーカースレッドを生成
    var mWorkerHandler = HandlerThreadHandler.createHandler("UVCCAMERA")
    var mUVCCamera: com.serenegiant.usb.UVCCamera? = null

    private var mPreviewSurface: Surface? = null
    private var frameCache: ByteArray? = null
    private val frameCallback = IFrameCallback { frame ->
        frame.clear()
        if (frameCache?.size != frame.remaining()) {
            frameCache = ByteArray(frame.remaining())
        }
        frame.get(frameCache)
//        if(DEBUG)Log.d("frame")
        mPreviewCallback?.onPreviewFrame(frameCache!!, this)
    }


    private val mOnDeviceConnectListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice) {}

        override fun onConnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock,
                               createNew: Boolean) {
            if (DEBUG) Log.d("onConnect: open camera")
            synchronized(mSync) {
                mUVCCamera?.destroy()
                preview = false
                isActive = false
            }
            mWorkerHandler?.post({
                synchronized(mSync) {
                    val camera = UVCCamera()
                    try {
                        camera.open(ctrlBlock)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val supportedSize = camera.getSupportedSize()
                    if(DEBUG) Log.d("supportedSize:$supportedSize")
                    val resolutionRatio = Gson().fromJson<ResolutionRatio>(supportedSize, ResolutionRatio::class.java)

                    cfg.supportResolution = resolutionRatio?.formats?.flatMap { it.size }
                            ?.map {
                                val split = it.split("x")
                                Point(split[0].toInt(), split[1].toInt())
                            }
                            ?.toHashSet()
                            ?.toTypedArray()

                    if (DEBUG) {
                        Log.d("supportResolution=${cfg.supportResolution?.map { it.toString() }}")
                        Log.d("supportedSize:$supportedSize, json:$resolutionRatio")
                    }

                    val mjpeg_camera_sizes = camera.getSupportedSizeList(UVCCamera.FRAME_FORMAT_MJPEG)
                    val yuv_camera_sizes = camera.getSupportedSizeList(UVCCamera.FRAME_FORMAT_YUYV)

                    // Pick the size that is closest to our required resolution
                    //                        UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT
                    if (DEBUG) if (DEBUG) Log.d("request resolution=${cfg.currentResolution}")
                    val required_width = cfg.currentResolution.x
                    val required_height = cfg.currentResolution.y
                    val required_area = required_width * required_height

                    var preview_width = 0
                    var preview_height = 0
                    var error = Integer.MAX_VALUE // trying to get this as small as possible

                    for (s in mjpeg_camera_sizes) {
                        // calculate the area for each camera size
                        val s_area = s.width * s.height
                        // calculate the difference between this size and the target size
                        val abs_error = Math.abs(s_area - required_area)
                        // check if the abs_error is smaller than what we have already
                        // then use the new size
                        if (abs_error < error) {
                            preview_width = s.width
                            preview_height = s.height
                            error = abs_error
                        }
                    }
                    cfg.currentResolution.x = preview_width
                    cfg.currentResolution.y = preview_height
                    try {
                        camera.setPreviewSize(preview_width, preview_height, UVCCamera.FRAME_FORMAT_MJPEG)
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                        try {
                            // fallback to YUV mode

                            // find closest matching size
                            // Pick the size that is closest to our required resolution
                            var yuv_preview_width = 0
                            var yuv_preview_height = 0
                            var yuv_error = Integer.MAX_VALUE // trying to get this as small as possible

                            for (s in yuv_camera_sizes) {
                                // calculate the area for each camera size
                                val s_area = s.width * s.height
                                // calculate the difference between this size and the target size
                                val abs_error = Math.abs(s_area - required_area)
                                // check if the abs_error is smaller than what we have already
                                // then use the new size
                                if (abs_error < yuv_error) {
                                    yuv_preview_width = s.width
                                    yuv_preview_height = s.height
                                    yuv_error = abs_error
                                }
                            }
                            cfg.currentResolution.x = yuv_preview_width
                            cfg.currentResolution.y = yuv_preview_height
                            camera.setPreviewSize(yuv_preview_width, yuv_preview_height, UVCCamera.FRAME_FORMAT_YUYV)
                        } catch (e1: IllegalArgumentException) {
                            e1.printStackTrace()
                            camera.destroy()
                            return@synchronized
                        }

                    }
                    if (DEBUG) Log.d("real resolution=${cfg.currentResolution}")
                    mPreviewSurface = mUVCCameraView?.getSurface()
                    if (mPreviewSurface != null) {
                        isActive = true
                        camera.setPreviewDisplay(mPreviewSurface)
                        if (DEBUG) Log.d("start preview")
                        camera.startPreview()
                        val format =
                                if (cfg.format == CameraFormat.PIXEL_FORMAT_YUV420SP)
                                    UVCCamera.PIXEL_FORMAT_YUV420SP
                                else UVCCamera.PIXEL_FORMAT_YUV420SP
                        camera.setFrameCallback(frameCallback, format)
                        preview = true
                    }
                    synchronized(mSync) {
                        mUVCCamera = camera
                    }
                }
            })
        }

        override fun onDisconnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
            if (DEBUG) Log.d("onDisconnect: ")
            mWorkerHandler.post(Runnable {
                synchronized(mSync) {
                    mUVCCamera?.close()
//                    mPreviewSurface?.release()
                    mPreviewSurface = null
                    preview = false
                }
            })
        }

        override fun onDettach(device: UsbDevice) {
            if (DEBUG) Log.d("onDettach: ${device.productName}, ${device.productId} ")
        }

        override fun onCancel(device: UsbDevice) {
            if (DEBUG) Log.d("onCancel: ")
        }
    }

    init {
        mUSBMonitor = USBMonitor(context, mOnDeviceConnectListener)
    }

    fun updateResolution(point: Point) {
        cfg.currentResolution=point
        mUSBMonitor.requestPermission(this@USBCamera.device)
    }
}
