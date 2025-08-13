package com.appxy.blurview;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 用于管理模糊效果的生命周期和操作
 */
public final class BlurRectController implements BlurController {

    /**
     * 定义了一个透明的颜色常量，用整数0表示（即完全透明）。
     */
    @ColorInt
    public static final int TRANSPARENT = 0;

    /**
     * 模糊半径，默认值在接口中定义
     */
    private float blurRadius = DEFAULT_BLUR_RADIUS;

    /**
     * 模糊算法实现
     */
    private final BlurAlgorithm blurAlgorithm;

    /**
     * 缩放因子，用于在模糊前缩小位图，以提高性能
     */
    private final float scaleFactor;

    /**
     * 是否添加噪点（提升视觉效果）
     */
    private final boolean applyNoise;

    /**
     * 用于在内部位图上绘制的画布
     */
    private BlurViewCanvas internalCanvas;

    /**
     * 用于模糊的内部位图,存储底层视图截图的位图
     */
    private Bitmap blurBitmap;

    /**
     * 应用模糊效果的视图
     */
    @SuppressWarnings("WeakerAccess")
    final View blurView;

    /**
     * 覆盖在模糊效果上的颜色
     */
    private int overlayColor;

    /**
     * 根视图，模糊内容从该视图开始绘制
     */
    private final ViewGroup rootView;

    /**
     * 存储根视图在屏幕上的位置
     */
    private final int[] rootLocation = new int[2];

    /**
     * 存储模糊视图在屏幕上的位置
     */
    private final int[] blurViewLocation = new int[2];

    private float denisity;
    public static final String TAG = "BlurView";


    /**
     * 模糊是否启用
     */
    private boolean blurEnabled = true;

    /**
     * 是否已初始化
     */
    private boolean initialized;

    /**
     * 可选的用于清除帧的Drawable
     */
    @Nullable
    private Drawable frameClearDrawable;
    private BlurOverlayView.BlurRect blurRect = null;

    /**
     * @param blurView    View which will draw it's blurred underlying content
     * @param rootView    Root View where blurView's underlying content starts drawing.
     *                    Can be Activity's root content layout (android.R.id.content)
     * @param algorithm   sets the blur algorithm
     * @param scaleFactor a scale factor to downscale the view snapshot before blurring.
     *                    Helps achieving stronger blur and potentially better performance at the expense of blur precision.
     * @param applyNoise  optional blue noise texture over the blurred content to make it look more natural. True by default.
     */
    public BlurRectController(@NonNull View blurView,
                              @NonNull ViewGroup rootView,
                              @ColorInt int overlayColor,
                              BlurAlgorithm algorithm,
                              float scaleFactor,
                              float blurRadius,
                              boolean applyNoise) {
        this.rootView = rootView;
        this.blurView = blurView;
        this.overlayColor = overlayColor;
        this.blurAlgorithm = algorithm;
        this.scaleFactor = scaleFactor;
        this.applyNoise = applyNoise;
        this.blurRadius = blurRadius;

        denisity = blurView.getResources().getDisplayMetrics().density;
    }

    public void addBlurRect(BlurOverlayView.BlurRect blurRect) {
        this.blurRect = blurRect;
        float rectWidth = blurRect.mRect.width();
        float rectHeight = blurRect.mRect.height();

        // 计算缩放后的位图尺寸（目标区域尺寸 / scaleFactor）
        SizeScaler sizeScaler = new SizeScaler(scaleFactor);
        if (sizeScaler.isZeroSized(rectWidth, rectHeight)) {
            return;
        }
        SizeScaler.Size newBitmapSize = sizeScaler.scale(rectWidth, rectHeight);

        // 创建缩放后的位图（用于模糊计算）
        blurBitmap = Bitmap.createBitmap(
                newBitmapSize.width,
                newBitmapSize.height,
                blurAlgorithm.getSupportedBitmapConfig()
        );
        internalCanvas = new BlurViewCanvas(blurBitmap);
        this.blurRect.blurBitmap = blurBitmap;
        initialized = true;
    }

    public void setBlurRect(BlurOverlayView.BlurRect blurRect) {
        this.blurRect = blurRect;
        blurBitmap = blurRect.blurBitmap;
    }

    @SuppressWarnings("WeakerAccess")
    void updateBlur() {
        if (!blurEnabled || !initialized) {
            return;
        }
//        if (frameClearDrawable == null) {
//            // 这个方法高效地将整个位图设置为指定的颜色（这里为透明）。它直接操作位图的像素，速度较快。
//            internalBitmap.eraseColor(Color.TRANSPARENT);
//        } else {
//            // 使用这个 Drawable绘制到内部画布（internalCanvas）上
//            frameClearDrawable.draw(internalCanvas);
//        }
        // 保存画布状态
        internalCanvas.save();
        // 设置画布变换矩阵，使得画布上的绘制从blurView的位置开始
        setupInternalCanvasMatrix();
        try {
            // 整个根视图及其子树的界面内容绘制到内部画布
            rootView.draw(internalCanvas);
        } catch (Exception e) {
            // Can potentially fail on rendering Hardware Bitmaps or something like that
            Log.e("BlurView", "Error during snapshot capturing", e);
        }
        // 恢复画布状态
        internalCanvas.restore();

        // 使用模糊算法对内部位图进行模糊
        blurBitmap = blurAlgorithm.blur(blurBitmap, blurRadius);
    }

    /**
     * 将根视图（rootView）中与模糊视图（blurView）重叠的部分内容绘制到内部位图（internalBitmap）上
     */
    private void setupInternalCanvasMatrix() {
        if (blurRect == null) {
            return;
        }
        // 获取根视图和模糊图层在屏幕上的位置
        rootView.getLocationOnScreen(rootLocation);
        blurView.getLocationOnScreen(blurViewLocation);
        blurViewLocation[0] = (int) blurRect.mRect.left;
        blurViewLocation[1] = (int) blurRect.mRect.top + blurViewLocation[1];

        // 模糊图层相对于根视图的左侧偏移量（水平方向）
        float left = blurViewLocation[0] - rootLocation[0];

        // 模糊图层相对于根视图的顶部偏移量（垂直方向）
        float top = blurViewLocation[1] - rootLocation[1];

        // 计算缩放因子（因为内部位图可能被缩小了）
        float scaleFactorH = blurRect.mRect.height() / blurBitmap.getHeight();
        float scaleFactorW = blurRect.mRect.width() / blurBitmap.getWidth();

        // 调整画布位置，使绘制从模糊视图的位置开始
        float scaledLeftPosition = -left / scaleFactorW;
        float scaledTopPosition = -top / scaleFactorH;

        internalCanvas.translate(scaledLeftPosition, scaledTopPosition);
        internalCanvas.scale(1 / scaleFactorW, 1 / scaleFactorH);

    }

    @Override
    public boolean draw(Canvas canvas) {
        if (canvas instanceof BlurViewCanvas || blurBitmap == null) return false;
        if (!blurEnabled || !initialized) return true;
        updateBlur();
        // 目标区域的尺寸和位置（来自BlurRect）
        float rectLeft = blurRect.mRect.left;
        float rectTop = blurRect.mRect.top;
        float rectWidth = blurRect.mRect.width();
        float rectHeight = blurRect.mRect.height();

        // 计算缩放因子（缩放后的位图需要放大 scaleFactor 倍才能覆盖目标区域）
        float scaleFactorW = rectWidth / blurBitmap.getWidth();
        float scaleFactorH = rectHeight / blurBitmap.getHeight();

        canvas.save();
        // 先平移画布到目标区域的左上角，再缩放
        canvas.translate(rectLeft, rectTop); // 1. 平移到目标区域起点
        canvas.scale(scaleFactorW, scaleFactorH); // 2. 放大到目标区域尺寸
        blurAlgorithm.render(canvas, blurBitmap);
        if (overlayColor != TRANSPARENT) {
            canvas.drawColor(overlayColor);
        }
        if (applyNoise) {
            Noise.apply(canvas, blurView.getContext(), (int) blurRect.mRect.width(), (int) blurRect.mRect.height());
        }
        // 3. 渲染模糊后的位图（缩放后的位图会被放大到原尺寸）
        canvas.restore();
        canvas.drawBitmap(blurBitmap, 0, 0, null);
        return true;
    }

    /**
     * 当视图尺寸变化时重新初始化。
     */
    @Override
    public void updateBlurViewSize() {
    }

    /**
     * 销毁资源，移除监听器，销毁模糊算法。
     */
    @Override
    public void destroy() {
        setBlurAutoUpdate(false);
        blurAlgorithm.destroy();
        initialized = false;
    }

    @Override
    public BlurViewFacade setBlurRadius(float radius) {
        this.blurRadius = radius;
        return this;
    }

    /**
     * 设置自定义背景清除方式
     */
    @Override
    public BlurViewFacade setFrameClearDrawable(@Nullable Drawable frameClearDrawable) {
        this.frameClearDrawable = frameClearDrawable;
        return this;
    }

    @Override
    public BlurViewFacade setBlurEnabled(boolean enabled) {
        this.blurEnabled = enabled;
        setBlurAutoUpdate(enabled);
        blurView.invalidate();
        return this;
    }

    @Override
    public BlurViewFacade setBlurAutoUpdate(boolean enabled) {
        return null;
    }

    @Override
    public BlurViewFacade setOverlayColor(int overlayColor) {
        if (this.overlayColor != overlayColor) {
            this.overlayColor = overlayColor;
            blurView.invalidate();
        }
        return this;
    }
}
