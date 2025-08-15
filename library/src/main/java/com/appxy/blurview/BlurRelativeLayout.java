package com.appxy.blurview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.RelativeLayout;

import java.util.ArrayList;

public class BlurRelativeLayout extends RelativeLayout {
    private final ArrayList<BlurRectView> blurRects = new ArrayList<>();
    private BlurRectView currentSelected;

    private boolean isInCreationMode = false;
    private final RectF creationRect = new RectF();
    private final Paint previewPaint = new Paint();

    private float SLOP_PX = 10f;
    private PointF dragStartPoint = new PointF();
    /**
     * 检查是否达到拖动阈值
     */
    private boolean isDragConfirmed = false;

    // 修复事件拦截问题
    private boolean interceptTouchEvent = false;

    public BlurRelativeLayout(Context context) {
        super(context);
        init();
    }

    public BlurRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        previewPaint.setColor(0x88FF0000);
        previewPaint.setStyle(Paint.Style.FILL);
        setWillNotDraw(false);
        // 添加触摸灵敏度设置
        ViewConfiguration vc = ViewConfiguration.get(getContext());
        SLOP_PX = vc.getScaledTouchSlop();
    }

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
            for (int i = blurRects.size() - 1; i >= 0; i--) {
                BlurRectView rect = blurRects.get(i);

                // 使用更高效的命中测试方法
                if (rect.isPointInside(x, y)) {
                    hitExisting = true;
                    deselectAll();
                    rect.setSelected(true);
                    currentSelected = rect;
                    break;
                }
            }

            if (!hitExisting) {
                isInCreationMode = true;
                interceptTouchEvent = true; // 标记需要拦截事件
                dragStartPoint.set(event.getX(), event.getY());
                creationRect.set(dragStartPoint.x, dragStartPoint.y,
                        dragStartPoint.x, dragStartPoint.y);
                isDragConfirmed = false; // 未确认拖动
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
                    } else {
                        return true; // 未达到阈值不创建预览
                    }
                }

                // 手动调整矩形坐标，确保left <= right，top <= bottom
                float left = Math.min(dragStartPoint.x, currentX);
                float top = Math.min(dragStartPoint.y, currentY);
                float right = Math.max(dragStartPoint.x, currentX);
                float bottom = Math.max(dragStartPoint.y, currentY);
                creationRect.set(left, top, right, bottom);

                // 只重绘受影响区域
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (isDragConfirmed) {
                    // 确保矩形大小有效
                    addBlurRect(new RectF(creationRect));
                }
                isInCreationMode = false;
                interceptTouchEvent = false; // 重置拦截标志
                creationRect.setEmpty();
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                isInCreationMode = false;
                interceptTouchEvent = false; // 重置拦截标志
                creationRect.setEmpty();
                invalidate();
                return true;
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 只绘制创建预览矩形（仅在有效拖动时）
        if (isInCreationMode && isDragConfirmed && !creationRect.isEmpty()) {
            canvas.drawRect(creationRect, previewPaint);
        }
    }

    public void addBlurRect(RectF rectF) {
        deselectAll();

        // 创建并添加模糊视图
        BlurRectView blurRect = new BlurRectView(getContext(), rectF);
        blurRect.setSelected(true);
        blurRect.setListener(new BlurRectView.BlurRectListener() {
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
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                (int) rectF.width(),
                (int) rectF.height()
        );
        params.leftMargin = (int) rectF.left;
        params.topMargin = (int) rectF.top;
        blurRect.setLayoutParams(params);

        blurRects.add(blurRect);
        addView(blurRect);
        currentSelected = blurRect;
    }

    private void removeBlurRect(BlurRectView view) {
        blurRects.remove(view);
        removeView(view);
        currentSelected = null;
    }

    private void duplicateBlurRect(BlurRectView original) {
        RectF newRect = new RectF(original.getViewRect());
        newRect.offset(40, 40); // 偏移复制

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
        for (BlurRectView rect : blurRects) {
            rect.setSelected(false);
        }
        currentSelected = null;
    }

    public ArrayList<BlurRectView> getBlurRects() {
        return new ArrayList<>(blurRects);
    }
}