package com.unistrong.luowei.cameralib.impl.uvc
import com.google.gson.annotations.SerializedName


/**
 * Created by luowei on 2017/12/29.
 */

data class ResolutionRatio(
		@SerializedName("formats") val formats: List<Format>
)

data class Format(
		@SerializedName("index") val index: Int, //1
		@SerializedName("type") val type: Int, //6
		@SerializedName("default") val default: Int, //1
		@SerializedName("size") val size: List<String>
)