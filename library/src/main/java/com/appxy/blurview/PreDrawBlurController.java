package com.appxy.blurview;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 用于管理模糊效果的生命周期和操作
 */
public final class PreDrawBlurController implements BlurController {

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
     *  是否添加噪点（提升视觉效果）
     */
    private final boolean applyNoise;

    /**
     * 用于在内部位图上绘制的画布
     */
    private BlurViewCanvas internalCanvas;

    /**
     * 用于模糊的内部位图,存储底层视图截图的位图
     */
    private Bitmap internalBitmap;

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
     *  预绘制监听器，用于在视图树绘制前更新模糊
     */
    private final ViewTreeObserver.OnPreDrawListener drawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            // Not invalidating a View here, just updating the Bitmap.
            // This relies on the HW accelerated bitmap drawing behavior in Android
            // If the bitmap was drawn on HW accelerated canvas, it holds a reference to it and on next
            // drawing pass the updated content of the bitmap will be rendered on the screen
            // 在绘制前更新模糊
            updateBlur();
            return true;
        }
    };

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

    /**
     * @param blurView    View which will draw it's blurred underlying content
     * @param rootView    Root View where blurView's underlying content starts drawing.
     *                    Can be Activity's root content layout (android.R.id.content)
     * @param algorithm   sets the blur algorithm
     * @param scaleFactor a scale factor to downscale the view snapshot before blurring.
     *                    Helps achieving stronger blur and potentially better performance at the expense of blur precision.
     * @param applyNoise  optional blue noise texture over the blurred content to make it look more natural. True by default.
     */
    public PreDrawBlurController(@NonNull View blurView,
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

        int measuredWidth = blurView.getMeasuredWidth();
        int measuredHeight = blurView.getMeasuredHeight();
        denisity = blurView.getResources().getDisplayMetrics().density;
        init(measuredWidth, measuredHeight);
    }

    @SuppressWarnings("WeakerAccess")
    void init(int measuredWidth, int measuredHeight) {
        setBlurAutoUpdate(true);
        SizeScaler sizeScaler = new SizeScaler(scaleFactor);
        if (sizeScaler.isZeroSized(measuredWidth, measuredHeight)) {
            // 如果尺寸为0，则先不绘制，等尺寸变化时再初始化
            blurView.setWillNotDraw(true);
            return;
        }
        if (blurAlgorithm == null) {
            Log.d(TAG, "------没有模糊算法,参数传递错误");
            return;
        }

        blurView.setWillNotDraw(false); // 需要绘制
        // 计算缩放后的位图尺寸（降低分辨率提升性能）
        SizeScaler.Size newBitmapSize = sizeScaler.scale(measuredWidth, measuredHeight);
        // 检查是否需要重新创建位图
        if (internalBitmap == null || internalBitmap.getWidth() != newBitmapSize.width
                || internalBitmap.getHeight() != newBitmapSize.height) {

            // 回收旧位图
            if (internalBitmap != null && !internalBitmap.isRecycled()) {
                internalBitmap.recycle();
            }

            // 创建新位图（使用模糊算法支持的配置）
            internalBitmap = Bitmap.createBitmap(newBitmapSize.width, newBitmapSize.height, blurAlgorithm.getSupportedBitmapConfig());
            // 创建画布
            internalCanvas = new BlurViewCanvas(internalBitmap);
            // 更新画布使用的位图
            internalCanvas.setBitmap(internalBitmap);
        }
        Log.d(TAG, "-----imageWidth=" + internalBitmap.getWidth() + ",imageHeight=" + internalBitmap.getHeight());

        initialized = true; // 标记已初始化
        // 初始更新模糊
        updateBlur();
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
        internalBitmap = blurAlgorithm.blur(internalBitmap, blurRadius);
        Log.d(TAG, "-------blurRadius=" + blurRadius);
    }

    /**
     * 将根视图（rootView）中与模糊视图（blurView）重叠的部分内容绘制到内部位图（internalBitmap）上
     */
    private void setupInternalCanvasMatrix() {
        // 获取根视图和模糊视图在屏幕上的位置
        rootView.getLocationOnScreen(rootLocation);
        blurView.getLocationOnScreen(blurViewLocation);

        // 模糊视图相对于根视图的左侧偏移量（水平方向）
        int left = blurViewLocation[0] - rootLocation[0];
        // 模糊视图相对于根视图的顶部偏移量（垂直方向）
        int top = blurViewLocation[1] - rootLocation[1];

        // 计算缩放因子（因为内部位图可能被缩小了）
        float scaleFactorH = (float) blurView.getHeight() / internalBitmap.getHeight();
        float scaleFactorW = (float) blurView.getWidth() / internalBitmap.getWidth();

        // 调整画布位置，使绘制从模糊视图的位置开始
        float scaledLeftPosition = -left / scaleFactorW;
        float scaledTopPosition = -top / scaleFactorH;

        internalCanvas.translate(scaledLeftPosition, scaledTopPosition);
        internalCanvas.scale(1 / scaleFactorW, 1 / scaleFactorH);
    }

    @Override
    public boolean draw(Canvas canvas) {
        if (!blurEnabled || !initialized) {
            return true;
        }
        // 避免递归：如果画布是BlurViewCanvas类型（即自己绘制的），则跳过
        if (canvas instanceof BlurViewCanvas) {
            return false;
        }

        // 计算缩放因子（因为内部位图是被缩放过）
        float scaleFactorH = (float) blurView.getHeight() / internalBitmap.getHeight();
        float scaleFactorW = (float) blurView.getWidth() / internalBitmap.getWidth();

        canvas.save();
        // 缩放画布，与内部位图的缩放比例一致
        canvas.scale(scaleFactorW, scaleFactorH);
        // 将模糊后的位图绘制到视图的画布上（这里由具体的模糊算法完成）
        blurAlgorithm.render(canvas, internalBitmap);
        canvas.restore();

        // 如果需要，应用噪声效果
//        if (applyNoise) {
//            Noise.apply(canvas, blurView.getContext(), blurView.getWidth(), blurView.getHeight());
//        }
//
//        // 绘制覆盖颜色（比如半透明遮罩）
//        if (overlayColor != TRANSPARENT) {
//            canvas.drawColor(overlayColor);
//        }

        canvas.drawBitmap(internalBitmap, 0, 0, null);

        return true;
    }

    /**
     * 当视图尺寸变化时重新初始化。
     */
    @Override
    public void updateBlurViewSize() {
        int measuredWidth = blurView.getMeasuredWidth();
        int measuredHeight = blurView.getMeasuredHeight();
        init(measuredWidth, measuredHeight);
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

    public BlurViewFacade setBlurAutoUpdate(final boolean enabled) {
        rootView.getViewTreeObserver().removeOnPreDrawListener(drawListener);
        blurView.getViewTreeObserver().removeOnPreDrawListener(drawListener);
        if (enabled) {
            rootView.getViewTreeObserver().addOnPreDrawListener(drawListener);
            // 处理跨窗口情况（如对话框中的模糊视图）
            if (rootView.getWindowId() != blurView.getWindowId()) {
                blurView.getViewTreeObserver().addOnPreDrawListener(drawListener);
            }
        }
        return this;
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
