package com.appxy.blurview;

import static com.appxy.blurview.BlurController.DEFAULT_BLUR_RADIUS;
import static com.appxy.blurview.BlurController.DEFAULT_SCALE_FACTOR;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.RelativeLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BlurRelativeLayout extends RelativeLayout {
    private BlurRectView currentSelected;

    private boolean isInCreationMode = false;
    private final RectF dragRect = new RectF();
    private final Paint previewPaint = new Paint();
    // 尺寸转换
    private float defaultSize;
    private float copyRectOffset;
    /**
     * 图层外边框间距
     */
    private float blurMargin;
    /**
     * 从模糊图层最下边到菜单最下边
     */
    private float menuHeightSpace;
    private float density;
    private float SLOP_PX = 10f;
    private final PointF dragStartPoint = new PointF();
    /**
     * 检查是否达到拖动阈值
     */
    private boolean isDragConfirmed = false;
    private boolean isClickCreateRect = false;

    // 修复事件拦截问题
    private boolean interceptTouchEvent = false;
    private BlurController blurController = new NoOpController();
    @ColorInt
    private int overlayColor;
    private boolean blurAutoUpdate = true;
    private final RectF blurViewRect = new RectF();

    public BlurRelativeLayout(Context context) {
        super(context);
        init();
    }

    public BlurRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 添加触摸灵敏度设置
        ViewConfiguration vc = ViewConfiguration.get(getContext());
        SLOP_PX = vc.getScaledTouchSlop();

        // 尺寸转换
        density = getResources().getDisplayMetrics().density;
        defaultSize = 88 * density;
        copyRectOffset = 16 * density;
        blurMargin = 23 * density; // 从模糊位图距离上下左右之间的距离,下边距还要少4dp
        menuHeightSpace = 67 * density; // 从模糊图层最下边到菜单最下边

        setWillNotDraw(false);
        // 硬件加速开启
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    // ------------------------设置模糊图层的代码------------
    public BlurViewFacade setupWith(@NonNull BlurTarget target, BlurAlgorithm algorithm, float scaleFactor,
                                    float blurRadius, boolean applyNoise) {
        blurController.destroy();
        if (BlurTarget.canUseHardwareRendering) {
            // Ignores the blur algorithm, always uses RenderEffect
            blurController = new RenderNodeBlurController(this, target, overlayColor, scaleFactor, blurRadius, applyNoise);
        } else {
            blurController = new BlurRectController(this, target, overlayColor,
                    algorithm, scaleFactor, blurRadius, applyNoise);
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
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        blurController.setBlurAutoUpdate(this.blurAutoUpdate);
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
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // 修复事件拦截问题：只在创建模式下拦截事件
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            interceptTouchEvent = false;

            // 检查是否点击在现有矩形上
            boolean hitExisting = false;
            float x = event.getX();
            float y = event.getY();

            // 逆序检查确保最上层元素优先
            for (int i = getChildCount() - 1; i >= 0; i--) {
                View child = getChildAt(i);
                if (child instanceof BlurRectView) {
                    BlurRectView rect = (BlurRectView) child;
                    if (rect.isPointInside(x, y)) {
                        hitExisting = true;
                        if (rect != currentSelected) {
                            deselect();
                            rect.setSelected(true);
                            currentSelected = rect;
                        }
                        break;
                    }
                }
            }

            if (!hitExisting) {
                isInCreationMode = true;
                interceptTouchEvent = true; // 标记需要拦截事件
                dragStartPoint.set(event.getX(), event.getY());
                dragRect.set(dragStartPoint.x, dragStartPoint.y,
                        dragStartPoint.x, dragStartPoint.y);
                isDragConfirmed = false; // 未确认拖动
                isClickCreateRect = true;
            }
        }

        // 只在创建模式下拦截事件
        return interceptTouchEvent;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 只在创建模式下处理事件
        if (!isInCreationMode) return false;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                return true;

            case MotionEvent.ACTION_MOVE:
                float currentX = event.getX();
                float currentY = event.getY();
                float dx = Math.abs(currentX - dragStartPoint.x);
                float dy = Math.abs(currentY - dragStartPoint.y);

                // 检查是否达到拖动阈值
                if (!isDragConfirmed) {
                    if (dx > SLOP_PX || dy > SLOP_PX) {
                        isDragConfirmed = true;
                        isClickCreateRect = false;
                    } else {
                        return true; // 未达到阈值不创建预览
                    }
                }

                // 手动调整矩形坐标，确保left <= right，top <= bottom
                float left = Math.min(dragStartPoint.x, currentX);
                float top = Math.min(dragStartPoint.y, currentY);
                float right = Math.max(dragStartPoint.x, currentX);
                float bottom = Math.max(dragStartPoint.y, currentY);
                dragRect.set(left, top, right, bottom);

                // 只重绘受影响区域
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (isDragConfirmed) {
                    RectF selectionRect = new RectF(dragRect);
                    // 菜单位置（根据模糊区域中心与页面中心的关系）
                    float centerY = selectionRect.centerY();
                    float screenCenterY = getHeight() / 2f;
                    if (centerY < screenCenterY) {
                        // 在屏幕上半部分，菜单显示在下方（在矩形边框线下方）
                        selectionRect.bottom = selectionRect.bottom + menuHeightSpace;
                        selectionRect.top = selectionRect.top - blurMargin;
                    } else {
                        // 在屏幕下半部分，菜单显示在上方（在矩形边框线上方）
                        selectionRect.top = selectionRect.top - menuHeightSpace;
                        selectionRect.bottom = selectionRect.bottom + blurMargin;
                    }
                    selectionRect.left = dragRect.left - blurMargin;
                    selectionRect.right = dragRect.right + blurMargin;
                    // 确保矩形大小有效
                    addBlurRect(selectionRect);
                } else if (isClickCreateRect) {
                    RectF selectionRect = new RectF();
                    dragRect.set(event.getX(), event.getY(), event.getX(), event.getY());
                    // 菜单位置（根据模糊区域中心与页面中心的关系）
                    float screenCenterY = getHeight() / 2f;
                    selectionRect.top = dragRect.top - defaultSize / 2;
                    selectionRect.bottom = dragRect.bottom + defaultSize / 2;
                    selectionRect.left = dragRect.left - defaultSize / 2 - blurMargin;
                    selectionRect.right = dragRect.right + defaultSize / 2 + blurMargin;
                    float centerY = selectionRect.centerY();
                    if (centerY < screenCenterY) {
                        // 在屏幕上半部分，菜单显示在下方（在矩形边框线下方）
                        selectionRect.bottom = selectionRect.bottom + menuHeightSpace;
                        selectionRect.top = selectionRect.top - blurMargin;
                    } else {
                        // 在屏幕下半部分，菜单显示在上方（在矩形边框线上方）
                        selectionRect.top = selectionRect.top - menuHeightSpace;
                        selectionRect.bottom = selectionRect.bottom + blurMargin;
                    }
                    addBlurRect(selectionRect);
                }
                isInCreationMode = false;
                interceptTouchEvent = false; // 重置拦截标志
                dragRect.setEmpty();
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                isInCreationMode = false;
                interceptTouchEvent = false; // 重置拦截标志
                dragRect.setEmpty();
                invalidate();
                return true;
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 只绘制创建预览矩形（仅在有效拖动时）
        if (isInCreationMode && isDragConfirmed && !dragRect.isEmpty()) {
            canvas.drawRect(dragRect, previewPaint);
            // 黑色透明度25%
            previewPaint.setColor(Color.argb(63, 0, 0, 0));
            previewPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(dragRect, previewPaint);

            previewPaint.setStyle(Paint.Style.STROKE);
            previewPaint.setColor(Color.WHITE);
            previewPaint.setStrokeWidth(1 * density);
            canvas.drawRect(dragRect, previewPaint);

            // 绘制尺寸提示
            previewPaint.setColor(Color.WHITE);
            previewPaint.setTextSize(28);

            String sizeText = String.format(Locale.getDefault(), "%.0f×%.0f", dragRect.width(), dragRect.height());
            float textWidth = previewPaint.measureText(sizeText);

            canvas.drawText(sizeText,
                    dragRect.centerX() - textWidth / 2,
                    dragRect.centerY() + previewPaint.getTextSize() / 2, previewPaint);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        blurViewRect.set(l, t, r, b);
    }

    public void addBlurRect(RectF viewRectF) {
        deselect();
        // 创建并添加模糊视图
        BlurRectView blurRect = new BlurRectView(getContext(), viewRectF, blurViewRect, blurController);
        blurRect.setSelected(true);
        blurRect.setBlurRectListener(new BlurRectView.BlurRectListener() {
            @Override
            public void onDelete(BlurRectView view) {
                removeBlurRect(view);
            }

            @Override
            public void onCopy(BlurRectView view) {
                duplicateBlurRect(view);
            }
        });

        // 设置布局参数
        LayoutParams params = new LayoutParams(
                (int) viewRectF.width(),
                (int) viewRectF.height()
        );
        params.leftMargin = (int) viewRectF.left;
        params.topMargin = (int) viewRectF.top;
        blurRect.setLayoutParams(params);

        addView(blurRect);
        currentSelected = blurRect;
    }

    private void removeBlurRect(BlurRectView view) {
        removeView(view);
        currentSelected = null;
    }

    private void duplicateBlurRect(BlurRectView original) {
        MarginLayoutParams layoutParams = (MarginLayoutParams) original.getLayoutParams();
        RectF newRect = new RectF(layoutParams.leftMargin, layoutParams.topMargin,
                layoutParams.leftMargin + layoutParams.width,
                layoutParams.topMargin + layoutParams.height);
        newRect.offset(copyRectOffset, copyRectOffset); // 偏移复制
        // 添加位置边界检查
        if (newRect.right > getWidth()) {
            newRect.offsetTo(getWidth() - newRect.width(), newRect.top);
        }
        if (newRect.bottom > getHeight()) {
            newRect.offsetTo(newRect.left, getHeight() - newRect.height());
        }
        addBlurRect(newRect);
    }

    private void deselectAll() {
        // 遍历所有子视图取消选择
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof BlurRectView) {
                child.setSelected(false);
            }
        }
        currentSelected = null;
    }

    // 通过子视图收集所有 BlurRectView（替代原 blurRects）
    public List<BlurRectView> getBlurRectViewList() {
        List<BlurRectView> list = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof BlurRectView) {
                list.add((BlurRectView) child);
            }
        }
        return list;
    }

    private void deselect() {
        if (currentSelected != null) {
            currentSelected.setSelected(false);
            currentSelected = null;
        }
    }

}