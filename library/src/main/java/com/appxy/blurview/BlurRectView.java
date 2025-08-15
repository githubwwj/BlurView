package com.appxy.blurview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

public class BlurRectView extends View {
    private final RectF viewRect = new RectF(); // 视图在父容器中的位置
    private final RectF drawRect = new RectF(0, 0, 0, 0); // 绘制时的相对坐标
    private boolean isSelected = false;

    private static final int NONE = 0;
    private static final int MOVE = 1;
    private static final int RESIZE_BR = 2;
    private static final int BUTTON_DELETE = 3;
    private static final int BUTTON_COPY = 4;

    private int currentAction = NONE;
    private final PointF lastTouchPoint = new PointF(); // 用于记录局部坐标的最后触摸点
    private float initialTouchX, initialTouchY; // 初始触摸点的绝对坐标（屏幕坐标）
    private float initialLeft, initialTop; // 初始视图的left和top（父容器坐标）

    private static final int BUTTON_SIZE = 60;
    private static final int HANDLE_SIZE = 30;
    private static final float BORDER_WIDTH = 5f;

    private static final int RED_COLOR = 0x66FF0000;
    private static final int BORDER_COLOR = Color.BLUE;
    private static final int BUTTON_COLOR = Color.RED;
    private static final int HANDLE_COLOR = Color.GREEN;

    private final RectF deleteButtonRect = new RectF();
    private final RectF copyButtonRect = new RectF();
    private final RectF resizeHandleRect = new RectF();

    // 新增成员变量（替代 onDraw 中临时创建的 Paint）
    private final Paint borderPaint = new Paint();
    private final Paint buttonPaint = new Paint();

    private final Paint blurPaint = new Paint();
    private RelativeLayout.LayoutParams layoutParams;

    public interface BlurRectListener {
        void onDelete(BlurRectView view);

        void onCopy(BlurRectView view);
    }

    private BlurRectListener listener;

    public BlurRectView(Context context, RectF initRect) {
        super(context);
        this.viewRect.set(initRect);
        this.drawRect.set(0, 0, initRect.width(), initRect.height());
        init();
    }

    public BlurRectView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        blurPaint.setColor(RED_COLOR);

        // 初始化固定 Paint 属性（仅需一次）
        borderPaint.setColor(BORDER_COLOR);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(BORDER_WIDTH);
        borderPaint.setAntiAlias(true); // 抗锯齿优化

        buttonPaint.setColor(BUTTON_COLOR);
        buttonPaint.setAntiAlias(true); // 抗锯齿优化
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // 更新视图尺寸
        Log.d("log", "--------BlurRectView-------onLayout");
        layoutParams = (RelativeLayout.LayoutParams) getLayoutParams();
        if (changed) {
            drawRect.set(0, 0, getWidth(), getHeight());
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 绘制模糊区域（使用相对坐标）
        canvas.drawRect(drawRect, blurPaint);

        if (isSelected) {
            canvas.drawRect(drawRect, borderPaint);

            calculateControlPoints();

            canvas.drawRect(deleteButtonRect, buttonPaint);
            canvas.drawRect(copyButtonRect, buttonPaint);
            canvas.drawRect(resizeHandleRect, buttonPaint);
        }
    }

    private void calculateControlPoints() {
        // 使用视图尺寸计算控制点
        final float viewWidth = drawRect.width();
        final float viewHeight = drawRect.height();

        deleteButtonRect.set(
                viewWidth - BUTTON_SIZE, 0,
                viewWidth, BUTTON_SIZE
        );

        copyButtonRect.set(
                viewWidth - 3 * BUTTON_SIZE, 0,
                viewWidth - 2 * BUTTON_SIZE, BUTTON_SIZE
        );

        resizeHandleRect.set(
                viewWidth - HANDLE_SIZE, viewHeight - HANDLE_SIZE,
                viewWidth, viewHeight
        );
    }

    // 高效命中测试方法
    public boolean isPointInside(float parentX, float parentY) {
        // 转换为视图局部坐标
        float localX = parentX - getLeft();
        float localY = parentY - getTop();

        return drawRect.contains(localX, localY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isSelected) {
            // 未选中时只消费DOWN事件
            return event.getAction() == MotionEvent.ACTION_DOWN;
        }
        // 获取触摸点坐标（视图局部坐标）
        float x = event.getX();
        float y = event.getY();

        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // 记录初始触摸点的绝对坐标（屏幕坐标）
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                // 记录视图的初始布局位置（父容器坐标）
                initialLeft = getLeft();
                initialTop = getTop();
                // 记录局部坐标的最后触摸点，用于判断操作类型和计算局部增量
                lastTouchPoint.set(x, y);
                currentAction = determineAction(x, y);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (currentAction == NONE) return false;

                // 计算当前触摸点的绝对坐标和局部坐标
                float currentRawX = event.getRawX();
                float currentRawY = event.getRawY();
                float currentLocalX = event.getX();
                float currentLocalY = event.getY();

                switch (currentAction) {
                    case MOVE:
                        // 计算绝对移动量（总偏移量）
                        float totalDx = currentRawX - initialTouchX;
                        float totalDy = currentRawY - initialTouchY;
                        // 更新布局参数
                        updateViewPosition(totalDx, totalDy);
                        break;
                    case RESIZE_BR:
                        // 计算局部移动量（本次与上次的差值）
                        float localDx = currentLocalX - lastTouchPoint.x;
                        float localDy = currentLocalY - lastTouchPoint.y;
                        // 更新绘制区域和布局参数
                        handleResize(localDx, localDy);
                        break;
                }

                // 更新最后触摸点的局部坐标
                lastTouchPoint.set(currentLocalX, currentLocalY);
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (currentAction == BUTTON_DELETE && listener != null) {
                    listener.onDelete(this);
                } else if (currentAction == BUTTON_COPY && listener != null) {
                    listener.onCopy(this);
                }

                currentAction = NONE;
                return true;
        }
        return false;
    }

    private int determineAction(float x, float y) {
        // 使用局部坐标进行检查
        if (deleteButtonRect.contains(x, y)) return BUTTON_DELETE;
        if (copyButtonRect.contains(x, y)) return BUTTON_COPY;
        if (resizeHandleRect.contains(x, y)) return RESIZE_BR;
        if (drawRect.contains(x, y)) return MOVE;

        return NONE;
    }

    private void updateViewPosition(float totalDx, float totalDy) {
        RelativeLayout.LayoutParams params = layoutParams;
        if (params == null) {
            return;
        }
        // 计算新的 left/top
        int newLeft = (int) (initialLeft + totalDx);
        int newTop = (int) (initialTop + totalDy);
        params.leftMargin = newLeft;
        params.topMargin = newTop;
        setLayoutParams(params);

        // 同步更新 viewRect 的位置（关键优化）
        viewRect.left = newLeft;
        viewRect.top = newTop;
        // 宽高不变（移动不改变尺寸）
        viewRect.right = newLeft + viewRect.width();
        viewRect.bottom = newTop + viewRect.height();
    }

    // 在 handleResize 中同步更新 viewRect
    private void handleResize(float localDx, float localDy) {
        // 调整绘制区域的右下角（局部坐标增量直接影响宽高）
        drawRect.right += localDx;
        drawRect.bottom += localDy;

        // 更新布局参数的宽高（必须，否则视图不会实际改变大小）
        RelativeLayout.LayoutParams params = layoutParams;
        if (params == null) return;
        params.width = (int) drawRect.width();
        params.height = (int) drawRect.height();
        setLayoutParams(params);

        // 同步更新 viewRect 的实际尺寸（关键优化：保持与布局一致）
        // 注意：viewRect 的 left/top 未变（仅调整右下角），因此只需更新 right/bottom
        viewRect.right = viewRect.left + params.width;
        viewRect.bottom = viewRect.top + params.height;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
        invalidate();
    }

    public void setListener(BlurRectListener listener) {
        this.listener = listener;
    }

    public RectF getViewRect() {
        return new RectF(viewRect);
    }

    // 更新视图位置和尺寸
    public void updateLayout(RectF rect) {
        this.viewRect.set(rect);
        RelativeLayout.LayoutParams params = layoutParams;
        if (params == null) return;
        params.leftMargin = (int) rect.left;
        params.topMargin = (int) rect.top;
        params.width = (int) rect.width();
        params.height = (int) rect.height();
        setLayoutParams(params);
    }

}