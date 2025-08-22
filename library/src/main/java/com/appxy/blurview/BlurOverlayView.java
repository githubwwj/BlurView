package com.appxy.blurview;

import static com.appxy.blurview.BlurController.DEFAULT_BLUR_RADIUS;
import static com.appxy.blurview.BlurController.DEFAULT_SCALE_FACTOR;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.appxy.tinyscanner.R;

import java.util.ArrayList;
import java.util.List;

public class BlurOverlayView extends View {

    // 矩形边框
    private final RectF borderRect = new RectF();

    // 模糊图层管理
    private final List<BlurRect> blurRectList = new ArrayList<>();
    private BlurRect selectedBlurRect = null;

    // 触摸状态
    private static final int MODE_NONE = 0;
    private static final int MODE_MOVE = 1;
    private static final int MODE_ROTATE = 2;
    private static final int MODE_ADD_CLICK = 3;
    private static final int MODE_ADD_BY_DRAG = 4;
    private static final int MODE_RESIZE = 5;
    private static final int MODE_DELETED = 6;
    private int touchMode = MODE_NONE;
    private final PointF dragStartPoint = new PointF();
    private float lastX, lastY;
    private final RectF dragRect = new RectF();
    private float SLOP_PX = 10f;

    // 尺寸转换
    private float defaultSize;
    private float rectMin;
    private float copyRectOffset;
    /**
     * 左旋右旋手柄大小
     */
    private float handleSize;
    /**
     * 拷贝删除按钮大小
     */
    private float copyDeleteBtnSize;
    /**
     * 图层外边框间距
     */
    private float frameMargin;
    private float density;

    // 操作按钮
    private Bitmap copyIcon;
    private Bitmap deleteIcon;
    private Bitmap leftRotateIcon;
    private Bitmap rightRotateIcon;
    private final Paint previewPaint = new Paint();
    private Paint menuPaint;
    private Paint selectionPaint; // 选中状态边框
    private Paint resizeDotPaint; // 调整大小手柄画笔

    private BlurController blurController = new NoOpController();
    private int overlayColor = 0;
    private boolean blurAutoUpdate = true;

    public BlurOverlayView(Context context) {
        super(context);
        init();
    }

    public BlurOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 尺寸转换
        density = getResources().getDisplayMetrics().density;
        defaultSize = 88 * density;
        rectMin = 18 * density;
        copyRectOffset = 16 * density;
        frameMargin = 6 * density;

        // 创建操作图标
        copyIcon = getBitmapFromSvg(R.drawable.blurview_copy);
        deleteIcon = getBitmapFromSvg(R.drawable.blurview_delete);
        leftRotateIcon = getBitmapFromSvg(R.drawable.blurview_left);
        rightRotateIcon = getBitmapFromSvg(R.drawable.blurview_right);
        handleSize = leftRotateIcon.getWidth();
        copyDeleteBtnSize = copyIcon.getWidth();
        overlayColor = ContextCompat.getColor(getContext(), R.color.overlay);

        // 模糊效果
        menuPaint = new Paint();
        menuPaint.setAntiAlias(true);

        // 选中状态边框
        selectionPaint = new Paint();
        selectionPaint.setStyle(Paint.Style.STROKE);
        // 蓝色
        selectionPaint.setColor(getResources().getColor(R.color.blurview_stroke));
        selectionPaint.setStrokeWidth(2f * density);
        selectionPaint.setAntiAlias(true);

        // 调整大小手柄画笔
        resizeDotPaint = new Paint();
        resizeDotPaint.setColor(getResources().getColor(R.color.blurview_stroke));
        resizeDotPaint.setStyle(Paint.Style.FILL);
        resizeDotPaint.setAntiAlias(true);

        // 添加触摸灵敏度设置
        ViewConfiguration vc = ViewConfiguration.get(getContext());
        SLOP_PX = vc.getScaledTouchSlop();

        // 硬件加速开启
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    // ------------------------设置模糊图层的代码------------
    public BlurViewFacade setupWith(@NonNull BlurTarget target, BlurAlgorithm algorithm, float scaleFactor,
                                    float blurRadius, boolean applyNoise) {
        blurController.destroy();
        if (BlurTarget.canUseHardwareRendering) {
            // Ignores the blur algorithm, always uses RenderEffect
            blurController = new RenderNodeBlurRectController(this, target, overlayColor, scaleFactor, blurRadius, applyNoise);
        } else {
            blurController = new BlurRectController(this, target, overlayColor, algorithm, scaleFactor, blurRadius, applyNoise);
        }
        return blurController;
    }

    public BlurViewFacade setupWith(@NonNull BlurTarget rootView, float scaleFactor, float blurRadius, boolean applyNoise) {
        BlurAlgorithm algorithm = new RenderScriptBlur(getContext());
        return setupWith(rootView, algorithm, scaleFactor, blurRadius, applyNoise);
    }

    public BlurViewFacade setupWith(@NonNull BlurTarget rootView, float blurRadius) {
        return setupWith(rootView, DEFAULT_SCALE_FACTOR, blurRadius, false);
    }

    public BlurViewFacade setupWith(@NonNull BlurTarget rootView) {
        return setupWith(rootView, DEFAULT_BLUR_RADIUS);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        blurController.setBlurAutoUpdate(false);
        for (BlurRect blurRect : blurRectList) {
            blurRect.recycle();
        }
        blurRectList.clear();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isHardwareAccelerated()) {
            Log.e("BlurView", "BlurView can't be used in not hardware-accelerated window!");
        } else {
            blurController.setBlurAutoUpdate(this.blurAutoUpdate);
        }
    }

    public BlurViewFacade setBlurRadius(float radius) {
        return blurController.setBlurRadius(radius);
    }

    /**
     * @see BlurViewFacade#setOverlayColor(int)
     */
    public BlurViewFacade setOverlayColor(@ColorInt int overlayColor) {
        this.overlayColor = overlayColor;
        return blurController.setOverlayColor(overlayColor);
    }

    /**
     * @see BlurViewFacade#setBlurAutoUpdate(boolean)
     */
    public BlurViewFacade setBlurAutoUpdate(boolean enabled) {
        this.blurAutoUpdate = enabled;
        return blurController.setBlurAutoUpdate(enabled);
    }

    public BlurViewFacade setBlurEnabled(boolean enabled) {
        return blurController.setBlurEnabled(enabled);
    }

    @Override
    public void setRotation(float rotation) {
        super.setRotation(rotation);
        notifyRotationChanged(rotation);
    }

    @SuppressLint("NewApi")
    public void notifyRotationChanged(float rotation) {
        if (usingRenderNode()) {
            ((RenderNodeBlurController) blurController).updateRotation(rotation);
        }
    }

    @SuppressLint("NewApi")
    public void notifyScaleXChanged(float scaleX) {
        if (usingRenderNode()) {
            ((RenderNodeBlurController) blurController).updateScaleX(scaleX);
        }
    }

    @SuppressLint("NewApi")
    public void notifyScaleYChanged(float scaleY) {
        if (usingRenderNode()) {
            ((RenderNodeBlurController) blurController).updateScaleY(scaleY);
        }
    }

    private boolean usingRenderNode() {
        return blurController instanceof RenderNodeBlurController;
    }
    // ------------------------设置模糊图层的代码------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // 设置边框矩形（留出边距）
        borderRect.set(0, 0, w, h);
        blurController.updateBlurViewSize();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 2. 绘制所有模糊矩形
        for (BlurRect rect : blurRectList) {
            if (rect.isVisible(borderRect)) {
                // 根据当前操作模式决定是否显示操作按钮
                boolean showButtons = touchMode != MODE_MOVE &&
                        touchMode != MODE_ROTATE &&
                        touchMode != MODE_RESIZE;
                rect.draw(canvas, rect == selectedBlurRect, showButtons);
            }
        }

        // 3. 绘制拖动预览
        if (touchMode == MODE_ADD_BY_DRAG) {
            drawDragPreview(canvas);
        }
    }

    private void drawDragPreview(Canvas canvas) {
        // 黑色透明度25%
        previewPaint.setColor(Color.argb(63, 0, 0, 0));
        previewPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(dragRect, previewPaint);

        previewPaint.setStyle(Paint.Style.STROKE);
        previewPaint.setColor(Color.WHITE);
        previewPaint.setStrokeWidth(1 * density);
        canvas.drawRect(dragRect, previewPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                handleTouchDown(x, y);
                return true;

            case MotionEvent.ACTION_MOVE:
                handleTouchMove(x, y);
                return true;

            case MotionEvent.ACTION_UP:
                handleTouchUp(x, y);
                return true;

            case MotionEvent.ACTION_CANCEL:
                touchMode = MODE_NONE;
                selectedBlurRect = null;
                invalidate(); // 确保重绘
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void handleTouchDown(float x, float y) {
        lastX = x;
        lastY = y;
        dragStartPoint.set(x, y);

        // 1. 检查是否点中操作按钮
        if (selectedBlurRect != null && selectedBlurRect.isVisible(borderRect)) {
            if (selectedBlurRect.isInCopyButton(x, y)) {
                copySelectedRect();
                touchMode = MODE_NONE;
                return;
            }
            if (selectedBlurRect.isInDeleteButton(x, y)) {
                deleteSelectedRect();
                touchMode = MODE_DELETED;
                return;
            }
        }

        // 2. 检查是否点中旋转手柄
        if (selectedBlurRect != null && selectedBlurRect.isVisible(borderRect) && selectedBlurRect.isInRotateHandle(x, y)) {
            touchMode = MODE_ROTATE;
            // 记录初始旋转角度
            selectedBlurRect.startRotation = selectedBlurRect.rotation;
            // 计算初始角度（相对于矩形中心）
            float centerX = selectedBlurRect.mRect.centerX();
            float centerY = selectedBlurRect.mRect.centerY();
            selectedBlurRect.startAngle = (float) Math.toDegrees(Math.atan2(y - centerY, x - centerX));
            return;
        }

        // 3. 检查是否点中调整手柄
        if (selectedBlurRect != null && selectedBlurRect.isVisible(borderRect) && selectedBlurRect.isInResizeHandle(x, y)) {
            touchMode = MODE_RESIZE;
            selectedBlurRect.resizeHandleType = selectedBlurRect.getResizeHandleType(x, y);
            selectedBlurRect.initResizeState(x, y); // 初始化调整状态
            return;
        }

        // 4. 检查是否点中矩形边框线（附近）
        if (selectedBlurRect != null && selectedBlurRect.isVisible(borderRect) && selectedBlurRect.isOnBorder(x, y)) {
            touchMode = MODE_RESIZE;
            selectedBlurRect.resizeHandleType = selectedBlurRect.getBorderHandleType(x, y);
            selectedBlurRect.initResizeState(x, y); // 初始化调整状态
            return;
        }

        // 5. 检查是否点中已有矩形
        boolean isRemove = false;
        for (int i = blurRectList.size() - 1; i >= 0; i--) {
            BlurRect rect = blurRectList.get(i);
            if (rect.isVisible(borderRect) && rect.contains(x, y)) {
                if (selectedBlurRect != null && selectedBlurRect != rect) {
                    isRemove = true;
                    blurRectList.remove(i);
                }
                selectedBlurRect = rect;
                blurController.setBlurRect(rect);
                touchMode = MODE_MOVE;
                if (!isRemove) {
                    invalidate();
                    return;
                } else {
                    break;
                }
            }
        }

        if (isRemove) {
            blurRectList.add(selectedBlurRect);
            invalidate();
            return;
        }

        // 6. 如果没有点中任何矩形，开始添加新矩形
        touchMode = MODE_ADD_CLICK;
        dragRect.set(x, y, x, y);
    }

    private void handleTouchMove(float x, float y) {
        switch (touchMode) {
            case MODE_MOVE:
                if (selectedBlurRect != null) {
                    float dx = x - lastX;
                    float dy = y - lastY;
                    selectedBlurRect.mRect.offset(dx, dy);
                    invalidate();
                }
                break;

            case MODE_ROTATE:
                if (selectedBlurRect != null && selectedBlurRect.isVisible(borderRect)) {
                    float centerX = selectedBlurRect.mRect.centerX();
                    float centerY = selectedBlurRect.mRect.centerY();

                    // 计算当前角度（相对于矩形中心，范围：[-180°, 180°]）
                    float currentAngle = (float) Math.toDegrees(Math.atan2(y - centerY, x - centerX));

                    // 计算旋转角度（相对于初始角度）
                    float rotationAngle = currentAngle - selectedBlurRect.startAngle;

                    // 计算最终旋转角度（可能为负数）
                    float finalRotation = selectedBlurRect.startRotation + rotationAngle;

                    // 归一化到 0°~360°（去除负数）
                    finalRotation = (finalRotation % 360 + 360) % 360;

                    // 应用旋转
                    selectedBlurRect.setRotation(finalRotation);

                    invalidate();
                }
                break;

            case MODE_ADD_CLICK:
                float dx = Math.abs(x - dragStartPoint.x);
                float dy = Math.abs(y - dragStartPoint.y);
                if (dx > SLOP_PX || dy > SLOP_PX) {
                    touchMode = MODE_ADD_BY_DRAG;
                }
                float left = Math.min(dragStartPoint.x, x);
                float top = Math.min(dragStartPoint.y, y);
                float right = Math.max(dragStartPoint.x, x);
                float bottom = Math.max(dragStartPoint.y, y);
                dragRect.set(left, top, right, bottom);
                invalidate();
                break;
            case MODE_ADD_BY_DRAG:
                left = Math.min(dragStartPoint.x, x);
                top = Math.min(dragStartPoint.y, y);
                right = Math.max(dragStartPoint.x, x);
                bottom = Math.max(dragStartPoint.y, y);
                dragRect.set(left, top, right, bottom);
                invalidate();
                break;
            case MODE_RESIZE:
                if (selectedBlurRect != null && selectedBlurRect.isVisible(borderRect)) {
                    selectedBlurRect.resize(x, y);
                    invalidate();
                }
                break;
        }

        lastX = x;
        lastY = y;
    }

    private void handleTouchUp(float x, float y) {
        if (touchMode == MODE_ADD_BY_DRAG) {
            RectF selectionRect = new RectF(dragRect);
            addBlurRect(selectionRect, 0, null);
        } else if (touchMode == MODE_ADD_CLICK) {
            RectF selectionRect = new RectF();
            dragRect.set(x, y, x, y);
            // 菜单位置（根据模糊区域中心与页面中心的关系）
            selectionRect.top = dragRect.top - defaultSize / 2;
            selectionRect.bottom = dragRect.bottom + defaultSize / 2;
            selectionRect.left = dragRect.left - defaultSize / 2;
            selectionRect.right = dragRect.right + defaultSize / 2;
            addBlurRect(selectionRect, 0, null);
        } else if (touchMode == MODE_MOVE || touchMode == MODE_RESIZE) {
//            if (MODE_RESIZE == touchMode) {
//                Log.d("log", "------touchMode=调整大小");
//            } else if (MODE_MOVE == touchMode) {
//                Log.d("log", "------touchMode=移动");
//            }
            // 检查矩形是否完全移出边界
            if (selectedBlurRect != null && selectedBlurRect.isOutside(borderRect)) {
                deleteSelectedRect();
                Log.d("log", "------deleteSelectedRect");
            }
        }

        // 重置触摸模式（如果是删除状态则跳过添加）
        if (touchMode != MODE_DELETED) {
            touchMode = MODE_NONE;
        }

        // 确保在操作结束后重绘视图
        invalidate();
    }

    private boolean isPointInAnyRect(float x, float y) {
        for (BlurRect rect : blurRectList) {
            if (rect.isVisible(borderRect) && rect.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    public void addBlurRect(RectF selectionRect, float angle, String signData) {
        BlurRect rect = new BlurRect(selectionRect);
        rect.setRotation(angle);
        rect.setSignData(signData);
        blurRectList.add(rect);
        selectedBlurRect = rect;
    }

    // 复制选中矩形
    public void copySelectedRect() {
        if (selectedBlurRect != null && selectedBlurRect.isVisible(borderRect)) {
            BlurRect copy = new BlurRect(selectedBlurRect);
            // 向右下偏移
            copy.mRect.offset(copyRectOffset, copyRectOffset);
            blurRectList.add(copy);
            blurController.setBlurRect(copy);
            selectedBlurRect = copy;
            invalidate();
        }
    }

    // 删除选中矩形
    public void deleteSelectedRect() {
        if (selectedBlurRect != null) {
            selectedBlurRect.recycle();
            blurRectList.remove(selectedBlurRect);
            selectedBlurRect = null;
            invalidate();
        }
    }

    public Bitmap getBitmapFromSvg(@DrawableRes int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(getContext(), drawableId);
        if (drawable == null) {
            return null;
        }

        // 创建一个Bitmap，大小与Drawable相同
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public void cancelSelect() {
        if (null != selectedBlurRect) {
            selectedBlurRect = null;
            invalidate();
        }
    }

    public List<BlurRect> getBlurRectList() {
        return blurRectList;
    }

    // 模糊矩形类
    public class BlurRect {
        /**
         * 模糊位图坐标
         */
        RectF mRect;
        float rotation = 0;

        // 旋转操作状态
        float startRotation = 0; // 旋转开始时的角度
        float startAngle = 0;    // 旋转开始时的触摸点角度

        // 操作按钮位置
        RectF copyButtonRect = new RectF();
        RectF deleteButtonRect = new RectF();

        // 菜单背景
        RectF menuRect = new RectF();

        // 旋转手柄（右上角和左下角）
        RectF rotateHandleTopRight = new RectF();
        RectF rotateHandleBottomLeft = new RectF();

        // 调整大小手柄（小圆点）
        RectF resizeHandleTop = new RectF();
        RectF resizeHandleBottom = new RectF();
        RectF resizeHandleLeft = new RectF();
        RectF resizeHandleRight = new RectF();
        Matrix matrix = new Matrix();


        // 当前调整的手柄类型
        int resizeHandleType = -1;
        static final int RESIZE_TOP = 0;
        static final int RESIZE_BOTTOM = 1;
        static final int RESIZE_LEFT = 2;
        static final int RESIZE_RIGHT = 3;

        // 添加调整操作初始状态
        float resizeStartX, resizeStartY;
        final RectF initialRect = new RectF();
        float initialCenterX, initialCenterY;

        // 新增矩阵用于坐标转换
        private final Matrix rotationMatrix = new Matrix();
        private final Matrix inverseRotationMatrix = new Matrix();
        private final RectF selectionRect = new RectF();
        Bitmap blurBitmap;
        private String signData;


        BlurRect(RectF source) {
            mRect = new RectF(source);
            init();
        }

        // 复制构造
        BlurRect(BlurRect source) {
            mRect = new RectF(source.mRect);
            rotation = source.rotation;
            init();
        }

        void init() {
            selectionRect.set(
                    mRect.left - frameMargin,
                    mRect.top - frameMargin,
                    mRect.right + frameMargin,
                    mRect.bottom + frameMargin
            );
        }

        // 更新旋转矩阵
        private void updateRotationMatrix() {
            rotationMatrix.reset();
            rotationMatrix.setRotate(rotation, mRect.centerX(), mRect.centerY());
            rotationMatrix.invert(inverseRotationMatrix);
        }

        void updateButtonPositions() {
            // 计算选中状态边框矩形 6dp
            selectionRect.set(
                    mRect.left - frameMargin,
                    mRect.top - frameMargin,
                    mRect.right + frameMargin,
                    mRect.bottom + frameMargin
            );

            // 更新旋转矩阵
            updateRotationMatrix();

            // 菜单位置（根据模糊区域中心与页面中心的关系）
            float centerY = mRect.centerY();
            float screenCenterY = getHeight() / 2f;

            // 按钮位置（在矩形上方或下方）
            float buttonsY;
            float buttonMargin = 22 * density; // 21dp
            if (centerY < screenCenterY) {
                // 在屏幕上半部分，菜单显示在下方（在矩形边框线下方）
                float menuBottom = selectionRect.bottom + buttonMargin + copyDeleteBtnSize;
                // 检查是否超出父容器底部
                if (menuBottom + (40 * density - copyDeleteBtnSize) <= getHeight()) {
                    buttonsY = selectionRect.bottom + buttonMargin; // 显示在下方
                } else {
                    buttonsY = getHeight(); // 超出边界，隐藏菜单
                }
            } else {
                // 在屏幕下半部分，菜单显示在上方（在矩形边框线上方）
                buttonsY = selectionRect.top - buttonMargin - copyDeleteBtnSize;
                if (buttonsY < 0) {
                    buttonsY = getHeight(); // 超出边界，隐藏菜单
                }
            }

            float halfMenuWidth = 104 * density / 2;
            if (selectionRect.centerX() - halfMenuWidth <= 0) {
                menuRect.set(0, buttonsY, halfMenuWidth * 2, buttonsY + 40 * density);
                // 删除按钮（左侧）
                deleteButtonRect.set(
                        halfMenuWidth - copyDeleteBtnSize - 11 * density,
                        buttonsY + 8 * density,
                        halfMenuWidth - 11 * density,
                        buttonsY + copyDeleteBtnSize + 8 * density
                );
                // 复制按钮（右侧）
                copyButtonRect.set(
                        halfMenuWidth + 11 * density,
                        buttonsY + 8 * density,
                        halfMenuWidth + copyDeleteBtnSize + 11 * density,
                        buttonsY + copyDeleteBtnSize + 8 * density
                );
            } else if (borderRect.width() - selectionRect.centerX() <= halfMenuWidth) {
                menuRect.set(getWidth() - halfMenuWidth * 2, buttonsY, getWidth(), buttonsY + 40 * density);
                deleteButtonRect.set(
                        getWidth() - 11 * density - copyDeleteBtnSize - halfMenuWidth,
                        buttonsY + 8 * density,
                        getWidth() - 11 * density - halfMenuWidth,
                        buttonsY + copyDeleteBtnSize + 8 * density
                );
                copyButtonRect.set(
                        getWidth() - 11 * density - copyDeleteBtnSize,
                        buttonsY + 8 * density,
                        getWidth() - 11 * density,
                        buttonsY + copyDeleteBtnSize + 8 * density
                );
            } else {
                menuRect.set(mRect.centerX() - halfMenuWidth,
                        buttonsY,
                        mRect.centerX() + halfMenuWidth,
                        buttonsY + 40 * density);

                // 删除按钮（左侧）
                deleteButtonRect.set(
                        mRect.centerX() - copyDeleteBtnSize - 11 * density,
                        buttonsY + 8 * density,
                        mRect.centerX() - 11 * density,
                        buttonsY + copyDeleteBtnSize + 8 * density
                );
                // 复制按钮（右侧）
                copyButtonRect.set(
                        mRect.centerX() + 11 * density,
                        buttonsY + 8 * density,
                        mRect.centerX() + copyDeleteBtnSize + 11 * density,
                        buttonsY + copyDeleteBtnSize + 8 * density
                );
            }


            // 右上角旋转手柄
            rotateHandleTopRight.set(
                    selectionRect.right,
                    selectionRect.top - handleSize,
                    selectionRect.right + handleSize,
                    selectionRect.top
            );

            // 左下角旋转手柄
            rotateHandleBottomLeft.set(
                    selectionRect.left - handleSize,
                    selectionRect.bottom,
                    selectionRect.left,
                    selectionRect.bottom + handleSize
            );

            // 调整手柄（小圆点，在矩形边框线中点）
            float dotRadius = 3 * density; // 3dp
            // 上边中点
            resizeHandleTop.set(
                    selectionRect.centerX() - dotRadius,
                    selectionRect.top - dotRadius,
                    selectionRect.centerX() + dotRadius,
                    selectionRect.top + dotRadius
            );
            // 下边中点
            resizeHandleBottom.set(
                    selectionRect.centerX() - dotRadius,
                    selectionRect.bottom - dotRadius,
                    selectionRect.centerX() + dotRadius,
                    selectionRect.bottom + dotRadius
            );
            // 左边中点
            resizeHandleLeft.set(
                    selectionRect.left - dotRadius,
                    selectionRect.centerY() - dotRadius,
                    selectionRect.left + dotRadius,
                    selectionRect.centerY() + dotRadius
            );
            // 右边中点
            resizeHandleRight.set(
                    selectionRect.right - dotRadius,
                    selectionRect.centerY() - dotRadius,
                    selectionRect.right + dotRadius,
                    selectionRect.centerY() + dotRadius
            );
        }

        // 修改draw方法，增加showButtons参数
        void draw(Canvas canvas, boolean isSelected, boolean showButtons) {
            // 更新旋转矩阵
            updateRotationMatrix();

            canvas.save();

            // 旋转画布
            canvas.rotate(rotation, mRect.centerX(), mRect.centerY());

//            // 绘制模糊矩形
//            menuPaint.setColor(Color.argb(100, 200, 0, 0));
//            menuPaint.setStyle(Paint.Style.FILL);
//            canvas.drawRect(mRect, menuPaint);
            blurController.setBlurRect(this);
            blurController.draw(canvas);

            // 绘制选中状态
            if (isSelected) {
                // 更新按钮位置
                updateButtonPositions();

                // 绘制选中状态边框（比矩形大12dp）
                canvas.drawRect(selectionRect, selectionPaint);

                // 绘制两个旋转手柄（在矩形边框线外侧）
                if (touchMode == MODE_ROTATE) {
                    canvas.drawBitmap(leftRotateIcon,
                            rotateHandleTopRight.left,
                            rotateHandleTopRight.top, null);

                    canvas.drawBitmap(rightRotateIcon,
                            rotateHandleBottomLeft.left,
                            rotateHandleBottomLeft.top, null);
                }
                // 绘制四个调整手柄（小圆点）
                float dotRadius = 4f * density; // 4dp
                canvas.drawCircle(resizeHandleTop.centerX(), resizeHandleTop.centerY(), dotRadius, resizeDotPaint);
                canvas.drawCircle(resizeHandleBottom.centerX(), resizeHandleBottom.centerY(), dotRadius, resizeDotPaint);
                canvas.drawCircle(resizeHandleLeft.centerX(), resizeHandleLeft.centerY(), dotRadius, resizeDotPaint);
                canvas.drawCircle(resizeHandleRight.centerX(), resizeHandleRight.centerY(), dotRadius, resizeDotPaint);
            }

            canvas.restore();

            // 在原始坐标系绘制按钮（不受旋转影响）
            if (isSelected && showButtons) {
                canvas.save();
                // 旋转画布
                canvas.rotate(rotation, mRect.centerX(), mRect.centerY());
                canvas.drawBitmap(leftRotateIcon,
                        rotateHandleTopRight.left,
                        rotateHandleTopRight.top, null);
                canvas.drawBitmap(rightRotateIcon,
                        rotateHandleBottomLeft.left,
                        rotateHandleBottomLeft.top, null);
                canvas.restore();

                // 黑色透明度60%
                menuPaint.setColor(Color.argb(99, 0, 0, 0));
                menuPaint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(menuRect, 32 * density, 32 * density, menuPaint);

                menuPaint.setColor(getResources().getColor(R.color.blurview_menu_divider));
                menuPaint.setStyle(Paint.Style.STROKE);
                menuPaint.setStrokeWidth(0.5f * density);
                canvas.drawLine(menuRect.centerX(), menuRect.top + 8 * density,
                        menuRect.centerX(), menuRect.bottom - 8 * density, menuPaint);

                canvas.drawBitmap(copyIcon, copyButtonRect.left, copyButtonRect.top, null);
                canvas.drawBitmap(deleteIcon, deleteButtonRect.left, deleteButtonRect.top, null);
            }
        }

        boolean contains(float x, float y) {
            // 创建旋转矩阵
            matrix.reset();
            matrix.setRotate(-rotation, mRect.centerX(), mRect.centerY());

            // 反向旋转触摸点
            float[] point = new float[]{x, y};
            matrix.mapPoints(point);

            return mRect.contains(point[0], point[1]);
        }

        boolean isInRotateHandle(float x, float y) {
            // 修复：旋转后仍能检测到旋转手柄
            matrix.reset();
            matrix.setRotate(-rotation, mRect.centerX(), mRect.centerY());
            float[] point = {x, y};
            matrix.mapPoints(point);

            return rotateHandleTopRight.contains(point[0], point[1]) ||
                    rotateHandleBottomLeft.contains(point[0], point[1]);
        }

        boolean isInResizeHandle(float x, float y) {
            // 修复：旋转后仍能检测到调整手柄
            matrix.reset();
            matrix.setRotate(-rotation, mRect.centerX(), mRect.centerY());
            float[] point = {x, y};
            matrix.mapPoints(point);

            return resizeHandleTop.contains(point[0], point[1]) ||
                    resizeHandleBottom.contains(point[0], point[1]) ||
                    resizeHandleLeft.contains(point[0], point[1]) ||
                    resizeHandleRight.contains(point[0], point[1]);
        }

        // 在BlurRect类中修改isOnBorder方法
        boolean isOnBorder(float x, float y) {
            // 创建旋转矩阵
            matrix.reset();
            matrix.setRotate(-rotation, mRect.centerX(), mRect.centerY());
            float[] point = {x, y};
            matrix.mapPoints(point);
            float rotatedX = point[0];
            float rotatedY = point[1];

            // 减小检测区域16
            float touchThreshold = 16 * density; // 8dp
            if (mRect.width() < 64 * density) {
                touchThreshold = 4 * density;
            }

            // 检查点是否在矩形边框线附近
            return (Math.abs(rotatedX - selectionRect.left) < touchThreshold &&
                    rotatedY >= selectionRect.top && rotatedY <= selectionRect.bottom) ||
                    (Math.abs(rotatedX - selectionRect.right) < touchThreshold &&
                            rotatedY >= selectionRect.top && rotatedY <= selectionRect.bottom) ||
                    (Math.abs(rotatedY - selectionRect.top) < touchThreshold &&
                            rotatedX >= selectionRect.left && rotatedX <= selectionRect.right) ||
                    (Math.abs(rotatedY - selectionRect.bottom) < touchThreshold &&
                            rotatedX >= selectionRect.left && rotatedX <= selectionRect.right);
        }

        int getBorderHandleType(float x, float y) {
            // 创建旋转矩阵
            matrix.reset();
            matrix.setRotate(-rotation, mRect.centerX(), mRect.centerY());
            float[] point = {x, y};
            matrix.mapPoints(point);
            float rotatedX = point[0];
            float rotatedY = point[1];

            // 计算矩形边框线（比矩形大12dp）
            selectionRect.set(
                    mRect.left - frameMargin,
                    mRect.top - frameMargin,
                    mRect.right + frameMargin,
                    mRect.bottom + frameMargin
            );

            // 确定手柄类型
            float touchThreshold = 16 * density; // 16dp
            if (Math.abs(rotatedY - selectionRect.top) < touchThreshold) {
                return RESIZE_TOP;
            } else if (Math.abs(rotatedY - selectionRect.bottom) < touchThreshold) {
                return RESIZE_BOTTOM;
            } else if (Math.abs(rotatedX - selectionRect.left) < touchThreshold) {
                return RESIZE_LEFT;
            } else if (Math.abs(rotatedX - selectionRect.right) < touchThreshold) {
                return RESIZE_RIGHT;
            }
            return -1;
        }

        int getResizeHandleType(float x, float y) {
            // 修复：旋转后仍能检测到调整手柄类型
            matrix.reset();
            matrix.setRotate(-rotation, mRect.centerX(), mRect.centerY());
            float[] point = {x, y};
            matrix.mapPoints(point);

            if (resizeHandleTop.contains(point[0], point[1])) return RESIZE_TOP;
            if (resizeHandleBottom.contains(point[0], point[1])) return RESIZE_BOTTOM;
            if (resizeHandleLeft.contains(point[0], point[1])) return RESIZE_LEFT;
            if (resizeHandleRight.contains(point[0], point[1])) return RESIZE_RIGHT;
            return -1;
        }

        boolean isInCopyButton(float x, float y) {
            return copyButtonRect.contains(x, y);
        }

        boolean isInDeleteButton(float x, float y) {
            return deleteButtonRect.contains(x, y);
        }

        // 初始化调整操作
        void initResizeState(float x, float y) {
            resizeStartX = x;
            resizeStartY = y;
            initialRect.set(mRect);
            initialCenterX = mRect.centerX();
            initialCenterY = mRect.centerY();
        }

        // 重写的resize方法 - 修复旋转后只调整一边的问题
        void resize(float x, float y) {
            // 计算在本地坐标系中的增量
            matrix.reset();
            matrix.setRotate(-rotation);

            // 计算全局位移
            float dx = x - resizeStartX;
            float dy = y - resizeStartY;

            // 反向旋转位移量
            float[] rotatedDelta = {dx, dy};
            matrix.mapPoints(rotatedDelta);
            float rotatedDx = rotatedDelta[0];
            float rotatedDy = rotatedDelta[1];

            // 应用调整
            switch (resizeHandleType) {
                case RESIZE_TOP:
                    float newTop = initialRect.top + rotatedDy;
                    if (newTop < initialRect.bottom - rectMin) {
                        mRect.top = newTop;
                    }
                    break;

                case RESIZE_BOTTOM:
                    float newBottom = initialRect.bottom + rotatedDy;
                    if (newBottom > initialRect.top + rectMin) {
                        mRect.bottom = newBottom;
                    }
                    break;

                case RESIZE_LEFT:
                    float newLeft = initialRect.left + rotatedDx;
                    if (newLeft < initialRect.right - rectMin) {
                        mRect.left = newLeft;
                    }
                    break;

                case RESIZE_RIGHT:
                    float newRight = initialRect.right + rotatedDx;
                    if (newRight > initialRect.left + rectMin) {
                        mRect.right = newRight;
                    }
                    break;
            }
        }

        /**
         * @param boundary 边界
         */
        void constrainToBounds(RectF boundary) {
            // 左边界
            if (mRect.left < boundary.left) {
                mRect.left = boundary.left;
            }
            // 右边界
            if (mRect.right > boundary.right) {
                mRect.right = boundary.right;
            }
            // 上边界
            if (mRect.top < boundary.top) {
                mRect.top = boundary.top;
            }
            // 下边界
            if (mRect.bottom > boundary.bottom) {
                mRect.bottom = boundary.bottom;
            }
        }

        void setRotation(float angle) {
            rotation = (float) (Math.round(angle * 100) / 100.0);
        }

        // 检查矩形是否可见（在边界内）
        boolean isVisible(RectF boundary) {
            return RectF.intersects(selectionRect, boundary) &&
                    selectionRect.width() > 0 &&
                    selectionRect.height() > 0;
        }

        // 检查矩形是否完全在边界外
        boolean isOutside(RectF boundary) {
            return mRect.right <= boundary.left ||
                    mRect.left >= boundary.right ||
                    mRect.bottom <= boundary.top ||
                    mRect.top >= boundary.bottom;
        }

        public void recycle() {
            if (null != blurBitmap && !blurBitmap.isRecycled()) {
                blurBitmap.recycle();
                blurBitmap = null;
            }
        }

        /**
         * 模糊位图 类型 为6
         */
        public int getSignType() {
            return 6;
        }

        public Bitmap getBlurBitmap() {
            return blurBitmap;
        }

        public RectF getRect() {
            return new RectF(mRect);
        }

        public float getRotate() {
            return rotation;
        }

        public void setSignData(String signData) {
            this.signData = signData;
        }

        public String getSignData() {
            return signData;
        }
    }

    public Bitmap createBitmap(Bitmap originalBitmap, RectF regionInView) {
        if (regionInView.left < 0) {
            regionInView.left = 0;
        }
        if (regionInView.top < 0) {
            regionInView.top = 0;
        }
        if (regionInView.right > originalBitmap.getWidth()) {
            regionInView.right = originalBitmap.getWidth();
        }
        if (regionInView.bottom > originalBitmap.getHeight()) {
            regionInView.bottom = originalBitmap.getHeight();
        }
        if (regionInView.width() <= 0) {
            return null;
        }
        if (regionInView.height() <= 0) {
            return null;
        }
        return Bitmap.createBitmap(
                originalBitmap,
                (int) regionInView.left, (int) regionInView.top,
                (int) regionInView.width(), (int) regionInView.height(), null, true
        );
    }

}