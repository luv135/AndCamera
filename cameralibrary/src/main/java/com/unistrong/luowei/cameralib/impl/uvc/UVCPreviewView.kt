package com.unistrong.luowei.cameralib.impl.uvc

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView
import com.serenegiant.widget.UVCCameraTextureView
import com.unistrong.luowei.cameralib.base.ICameraView

/**
 * Created by luowei on 2017/9/23.
 */
open class UVCPreviewView : UVCCameraTextureView, ICameraView {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    override fun getOutputClass(): Class<*> = TextureView::class.java
}