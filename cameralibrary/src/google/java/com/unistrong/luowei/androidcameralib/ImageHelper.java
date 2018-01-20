package com.unistrong.luowei.androidcameralib;

import android.graphics.Rect;

public class ImageHelper {

	public static byte[] rotateYUV420sp(byte[] yuv420sp, int width, int height,
			int angle) {
		switch (angle) {
		case 0:
			return yuv420sp;
		case 90:
			return rotate90YUV420SP(yuv420sp, width, height);
		case 180:
			return rotate180YUV420SP(yuv420sp, width, height);
		case 270:
			return rotate270YUV420SP(yuv420sp, width, height);
		}
		return yuv420sp;
	}

	/**
	 * 顺时针旋转270.
	 * 
	 * @param yuv420sp
	 * @param width
	 * @param height
	 * @return
	 */
	public static byte[] rotate270YUV420SP(byte[] yuv420sp, int width,
			int height) {
		byte[] des = new byte[yuv420sp.length];
		int wh = width * height;
		// 旋转Y
		// b[w-j-1,i] = a[i,j]
		// => b[i,j] = a[j,w - i - 1]
		// j*w+w-i-1
		int k = 0;
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				des[k] = yuv420sp[width * j + width - i - 1];
				k++;
			}
		}

		// b[w-j-1,i] = a[i,j]
		// => b[i,j] = a[j,w - i - 1]
		for (int i = 0; i < width; i += 2) {
			for (int j = 0; j < height / 2; j++) {
				des[k] = yuv420sp[wh + width * j + width - i - 2];
				des[k + 1] = yuv420sp[wh + width * j + width - i - 1];
				k += 2;
			}
		}
		return des;
	}

	/**
	 * 顺时针旋转90.
	 * 
	 * @param yuv420sp
	 * @param width
	 * @param height
	 * @return
	 */
	public static byte[] rotate90YUV420SP(byte[] yuv420sp, int width, int height) {
		byte[] des = new byte[yuv420sp.length];
		int wh = width * height;
		// 旋转Y
		// => b[i,j] = a[h - j - 1, i]
		// j*w+i
		int k = 0;
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				des[k] = yuv420sp[width * (height - j - 1) + i];
				k++;
			}
		}

		// b[w-j-1,i] = a[i,j]
		// => b[i,j] = a[j,i]
		//
		for (int i = 0; i < width; i += 2) {
			for (int j = 0; j < height / 2; j++) {
				des[k] = yuv420sp[wh + width * (height / 2 - j - 1) + i];
				des[k + 1] = yuv420sp[wh + width * (height / 2 - j - 1) + i + 1];
				k += 2;
			}
		}
		return des;
	}

	/**
	 * 顺时针旋转180.
	 * 
	 * @param yuv420sp
	 * @param width
	 * @param height
	 * @return
	 */
	public static byte[] rotate180YUV420SP(byte[] yuv420sp, int width,
			int height) {
		// 旋转Y
		int length = width * height;
		for (int i = 0; i < length / 2 - 1; ++i) {
			byte temp = yuv420sp[i];
			yuv420sp[i] = yuv420sp[length - 1 - i];
			yuv420sp[length - 1 - i] = temp;
		}
		int startIndex = width * height;
		int count = width * height / 4;
		// 旋转uv
		for (int i = 0; i < count / 2 - 1; ++i) {
			byte temp = yuv420sp[i * 2 + startIndex];
			yuv420sp[i * 2 + startIndex] = yuv420sp[(count - i - 1) * 2
					+ startIndex];
			yuv420sp[(count - i - 1) * 2 + startIndex] = temp;

			temp = yuv420sp[i * 2 + 1 + startIndex];
			yuv420sp[i * 2 + 1 + startIndex] = yuv420sp[(count - i - 1) * 2 + 1
					+ startIndex];
			yuv420sp[(count - i - 1) * 2 + 1 + startIndex] = temp;
		}
		return yuv420sp;
	}

	public static void cropYUV420SP(byte[] yuv420sp, byte[] croppedYUV420sp,
			Rect rectangle, Rect originalRect) {

		if (!originalRect.contains(rectangle)) {
			throw new IllegalArgumentException(
					"rectangle is not inside the image");
		}

		int width = rectangle.width();
		int height = rectangle.height();
		// Make sure left, top, width and height are all even.
		width &= ~1;
		height &= ~1;
		rectangle.left &= ~1;
		rectangle.top &= ~1;
		rectangle.right = rectangle.left + width;
		rectangle.bottom = rectangle.top + height;

		width = rectangle.width();
		height = rectangle.height();
		int top = rectangle.top;
		// int bottom = rectangle.bottom;
		int left = rectangle.left;
		// int right = rectangle.right;

		croppedYUV420sp = new byte[width * height * 3 / 2];

		// Crop Y
		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				croppedYUV420sp[i * width + j] = yuv420sp[(top + i) * width
						+ (left + j)];
			}
		}
		// Crop UV
		int widthCountOfUV = originalRect.width() / 2;
		int	LeftOffsetCountOfUV = left / 2;
		int originalOffSet = originalRect.width() * originalRect.height();
		int croppedOffSet = width*height;
		int k = 0;
		for (int i = 0; i <= height; i += 2) {
			for (int j = 0; j < width; j+= 2) {
				int orignalIndex = (top + i)*widthCountOfUV + LeftOffsetCountOfUV*2 + j + originalOffSet;
				croppedYUV420sp[croppedOffSet + k] = yuv420sp[orignalIndex];
				croppedYUV420sp[croppedOffSet + k + 1] = yuv420sp[orignalIndex + 1];
				k += 2;
			}
		}
	}

}
