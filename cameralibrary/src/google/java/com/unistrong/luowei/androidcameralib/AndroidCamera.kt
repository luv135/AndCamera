package com.unistrong.luowei.androidcameralib

import android.content.Context
import android.graphics.*
import android.hardware.Camera
import android.hardware.display.DisplayManager
import android.util.SparseIntArray
import android.view.Display
import android.view.Surface
import android.view.TextureView
import com.google.android.cameraview.AspectRatio
import com.google.android.cameraview.Size
import com.google.android.cameraview.SizeMap
import com.unistrong.luowei.cameralib.base.*
import com.unistrong.luowei.commlib.Log
import java.io.IOException


class AndroidCamera(val context: Context) : ICamera {


    private val cfg = Config(currentResolution = Point(640, 480))

    private var preview: AndroidPreview? = null

    private var device: CameraDevice? = null
    private var mCameraParameters: Camera.Parameters? = null
    override fun open(device: IDevice, view: ICameraView): Boolean {
        device as? CameraDevice ?: return false
        view as? AndroidPreview ?: return false
        this.preview = view
        this.device = device
        openCamera(device.cameraId)

        this.preview!!.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {

            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {

            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
                close()
                return false
            }

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
                startPreview(surface!!)

            }

        }
        if (preview!!.isAvailable) {
            startPreview(preview!!.surfaceTexture!!)
        }

        return true
    }

    private var mCamera: Camera? = null


    private val mPreviewSizes = SizeMap()

    private val mPictureSizes = SizeMap()
    private var mAspectRatio: AspectRatio? = null

    private fun openCamera(mCameraId: Int) {
        if (mCamera != null) {
            close()
        }
        mCamera = Camera.open(mCameraId)
        mCameraParameters = mCamera!!.getParameters()


        Camera.getCameraInfo(mCameraId, mCameraInfo)

        mCameraParameters!!.setPreviewFormat(ImageFormat.NV21); // setting preview format：YV12
        mCameraParameters!!.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        // Supported preview sizes
        mPreviewSizes.clear()
        var presize: Camera.Size? = null

        val required_area = cfg.currentResolution.x * cfg.currentResolution.y
        Log.d("require resolution=${cfg.currentResolution}")

        var error = Int.MAX_VALUE
        for (size in mCameraParameters!!.getSupportedPreviewSizes()) {
            mPreviewSizes.add(Size(size.width, size.height))
//            Log.d("preivew : width=${size.width}, height=${size.height}")

            val s_area = size.width * size.height

            val abs_error = Math.abs(s_area - required_area)
            if (abs_error < error) {
                presize = size
                error = abs_error
            }
        }
        mCameraParameters!!.setPreviewSize(presize!!.width, presize.height)
        cfg.currentResolution.x = presize.width
        cfg.currentResolution.y = presize.height
        Log.d("final resolution=${cfg.currentResolution}")

        // Supported picture sizes;
        mPictureSizes.clear()
        error = Int.MAX_VALUE
        for (size in mCameraParameters!!.getSupportedPictureSizes()) {
            mPictureSizes.add(Size(size.width, size.height))
            val s_area = size.width * size.height

            val abs_error = Math.abs(s_area - required_area)
            if (abs_error < error) {
                presize = size
                error = abs_error
            }
        }

        mCameraParameters!!.setPictureSize(presize!!.width, presize.height)


        val rotation = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY).rotation
        setDisplayOrientation(rotation)
    }

    fun getBestSize(require: Camera.Size, listSize: List<Camera.Size>) {

    }


    fun setDisplayOrientation(displayOrientation: Int) {
        val calcCameraRotation = calcCameraRotation(displayOrientation)
        Log.d("camera rotation = $calcCameraRotation")
        mCameraParameters!!.setRotation(calcCameraRotation)
        mCamera!!.setParameters(mCameraParameters)
        mCamera!!.setDisplayOrientation(calcDisplayOrientation(displayOrientation))
    }

    private fun calcDisplayOrientation(screenOrientationDegrees: Int): Int {
        return if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (360 - (mCameraInfo.orientation + screenOrientationDegrees) % 360) % 360
        } else {  // back-facing
            (mCameraInfo.orientation - screenOrientationDegrees + 360) % 360
        }
    }

    var fixDataByteArray: ByteArray? = null
    fun fixRawData(raw: ByteArray): ByteArray {
        if (fixDataByteArray?.size != raw.size) {
            fixDataByteArray = ByteArray(raw.size)
        }
        val rotate = fixDataByteArray!!
        val size = mCamera!!.getParameters().getPreviewSize();
        val height = size.height
        val width = size.width
//        Log.d("raw size = ${raw.size}, cfg=${cfg.currentResolution}")
        if (true) {    //竖屏
            val rwidth = height
            val rheight = width
            if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                //前置摄像头竖屏顺旋转90度,垂直翻转
                var k = 0
                for (w in 0 until width) {
                    for (h in 0 until height) {
                        rotate[k++] = raw.value(width - 1 - w, height - 1 - h, width, height)
                    }
                }
                var w = 0
                val yHeight = height
                val uvHeight = height shr 1
                while (w < width) { //原始数据的循环
                    for (h in 0 until uvHeight) {
                        rotate[k++] = raw.value(width - 2 - w, yHeight + uvHeight - 1 - h, width, uvHeight)
                        rotate[k++] = raw.value(width - 2 - w + 1, yHeight + uvHeight - 1 - h, width, uvHeight)
                    }
                    w += 2
                }
            } else {
//                Log.d("后置摄像头数据转换")
                //后置摄像头竖屏变换,顺旋转90度
                /*
                nv21 数据排列:
                yyyyyyyyyyyyyyyy
                yyyyyyyyyyyyyyyy
                uvuvuvuvuvuvuvuv
                */
                // 旋转Y
                var k = 0
                for (w in 0 until width) {
                    for (h in 0 until height) {
                        rotate[k++] = raw.value(w, height - 1 - h, width, height)
                    }
                }
                //uv 数据排列:
                //uvuvuvuvuv
                //高度只有y 的一半
                val yHeight = height
                val uvHeight = height shr 1
                //假设原始数据 width = 8, height = 6
                //则原始数据 uv: width = 8, height = 3
                //旋转后 width = 6, height = 8
                //旋转后uv: width = 6, height = 4
                var w = 0
                while (w < width) { //原始数据的循环
                    for (h in 0 until uvHeight) {
                        rotate[k++] = raw.value(w, yHeight + uvHeight - 1 - h, width, uvHeight)
                        rotate[k++] = raw.value(w + 1, yHeight + uvHeight - 1 - h, width, uvHeight)
                    }
                    w += 2
                }
            }
            cfg.currentResolution.x = rwidth
            cfg.currentResolution.y = rheight
        } else {
            if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                //前置横屏水平翻转<左右对换>
                //
            } else {
                System.arraycopy(raw, 0, rotate, 0, raw.size)
            }
        }
        return rotate
    }

    private fun ByteArray.value(x: Int, y: Int, width: Int, height: Int = 0): Byte = get(x + y * width)


    private val mPreViewCallback: Camera.PreviewCallback = Camera.PreviewCallback { bytes, camera ->
        val fixRawData = fixRawData(bytes)
        callback?.onPreviewFrame(fixRawData, this)
        /*val width = cfg.currentResolution.x
        val height = cfg.currentResolution.y
        try {
            val image = YuvImage(fixRawData, ImageFormat.NV21, width, height, null)

            val stream = ByteArrayOutputStream();
            image.compressToJpeg(Rect(0, 0, width, height), 80, stream);

            //            tackPictureCache?.recycle()
            //            tackPictureCache = null
            tackPictureCache = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
            stream.close();

        } catch (ex: Exception) {
            Log.e(ex)

        }
        synchronized(TACK_PIC) {
            try {
                TACK_PIC.notifyAll()
            } catch (e: Exception) {
            }
        }*/
    }

    fun startPreview(holder: SurfaceTexture) {
        if (mCamera != null) {
            try {
                Log.d("preview")
                mCamera?.setPreviewTexture(holder)
                mCamera?.setPreviewCallback(mPreViewCallback)
                mCamera?.startPreview()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    val DISPLAY_ORIENTATIONS = SparseIntArray()

    init {
        DISPLAY_ORIENTATIONS.put(Surface.ROTATION_0, 0)
        DISPLAY_ORIENTATIONS.put(Surface.ROTATION_90, 90)
        DISPLAY_ORIENTATIONS.put(Surface.ROTATION_180, 180)
        DISPLAY_ORIENTATIONS.put(Surface.ROTATION_270, 270)
    }


    private val mCameraInfo = Camera.CameraInfo()

    private fun calcCameraRotation(screenOrientationDegrees: Int): Int {
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (mCameraInfo.orientation + screenOrientationDegrees) % 360
        } else {  // back-facing
            val landscapeFlip = if (isLandscape(screenOrientationDegrees)) 180 else 0
            return (mCameraInfo.orientation + screenOrientationDegrees + landscapeFlip) % 360
        }
    }

    private fun isLandscape(orientationDegrees: Int): Boolean {
        return orientationDegrees == 90 || orientationDegrees == 270
    }

    override fun close() {
        mCamera?.stopPreview()
        mCamera?.setPreviewCallback(null)
        mCamera?.release()
        mCamera = null
    }

    override fun config(config: Config) {

    }

    override fun getConfig(): Config {
        return cfg
    }

    private var callback: IPreviewCallback? = null

    override fun setPreviewCallback(callback: IPreviewCallback) {
        this.callback = callback
    }

    override fun startPreview() {

    }

    override fun stopPreview() {

    }

    override fun isPreview(): Boolean {
        return false
    }

    private val TACK_PIC = Object()
    private var tackPictureCache: Bitmap? = null

    private val jpegPictureCallback: Camera.PictureCallback = Camera.PictureCallback { data, camera ->
        //            mCallback.onPictureTaken(data)
        tackPictureCache?.recycle()
        tackPictureCache = null
        tackPictureCache = BitmapFactory.decodeByteArray(data, 0, data.size)
        Log.d("tack ok $tackPictureCache")
        camera.cancelAutoFocus()
        camera.startPreview()
        synchronized(TACK_PIC) {
            try {
                TACK_PIC.notifyAll()
            } catch (e: Exception) {
            }
        }
    }


    override fun tackPicture(): Bitmap? {
        mCamera?.takePicture(null, null, null, jpegPictureCallback)
        synchronized(TACK_PIC) {
            try {
                TACK_PIC.wait()
            } catch (e: Exception) {
            }
        }
        Log.d("return bitmap $tackPictureCache ")
        return tackPictureCache
    }

    override fun tackPicture(callback: (bitmap: Bitmap) -> Unit) {
        mCamera?.takePicture(null, null, null, Camera.PictureCallback { data, camera ->
            //            mCallback.onPictureTaken(data)
            tackPictureCache?.recycle()
            tackPictureCache = null
            tackPictureCache = BitmapFactory.decodeByteArray(data, 0, data.size)
            Log.d("tack ok $tackPictureCache")
            camera.cancelAutoFocus()
            camera.startPreview()

        })
    }

    override fun getCameras(): Array<IDevice> {
        return (0 until Camera.getNumberOfCameras())
                .map { CameraDevice(it) }
                .toTypedArray()


    }


}