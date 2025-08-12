package eightbitlab.com.blurview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.ColorInt;

public class BlurRectView extends View {

    // 模糊控制器
    private BlurController blurController = new NoOpController();
    private int overlayColor;
    private boolean blurAutoUpdate = true;

    // 矩形位置和大小
    private final RectF rect = new RectF();
    private float rotation = 0;

    // 操作按钮
    private Bitmap copyIcon;
    private Bitmap deleteIcon;
    private Bitmap rotateIcon;
    private float density;
    private float buttonSize;
    private float handleSize;

    // 选择状态
    private boolean isSelected = false;
    private final Paint selectionPaint = new Paint();
    private final Paint resizeDotPaint = new Paint();
    private float selectionMargin;

    // 手柄和按钮位置
    private final RectF copyButtonRect = new RectF();
    private final RectF deleteButtonRect = new RectF();
    private final RectF rotateHandleTopRight = new RectF();
    private final RectF rotateHandleBottomLeft = new RectF();
    private final RectF resizeHandleTop = new RectF();
    private final RectF resizeHandleBottom = new RectF();
    private final RectF resizeHandleLeft = new RectF();
    private final RectF resizeHandleRight = new RectF();

    // 触摸状态
    private int touchMode = BlurOverlayFrameLayout.MODE_NONE;
    private float startRotation = 0;
    private float startAngle = 0;
    private int resizeHandleType = -1;
    private float resizeStartX, resizeStartY;
    private final RectF initialRect = new RectF();
    private final Matrix matrix = new Matrix();

    // 调整手柄类型常量
    public static final int RESIZE_TOP = 0;
    public static final int RESIZE_BOTTOM = 1;
    public static final int RESIZE_LEFT = 2;
    public static final int RESIZE_RIGHT = 3;
    private float lastX, lastY;
    private float defaultSize;
    private final Paint blurPaint = new Paint();


    public BlurRectView(Context context) {
        super(context);
        init(context);
    }

    public BlurRectView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        buttonSize = 32 * density;
        handleSize = 24 * density;
        selectionMargin = 12 * density;
        defaultSize = 88 * density;

        // 创建操作图标
        copyIcon = createIconBitmap(Color.parseColor("#48BB78"), "C");
        deleteIcon = createIconBitmap(Color.parseColor("#E53E3E"), "D");
        rotateIcon = createRotateIconBitmap();

        // 初始化画笔
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setColor(Color.RED);
        selectionPaint.setStrokeWidth(1.2f * density);
        selectionPaint.setAntiAlias(true);

        resizeDotPaint.setColor(Color.parseColor("#ED8936"));
        resizeDotPaint.setStyle(Paint.Style.FILL);
        resizeDotPaint.setAntiAlias(true);

        blurPaint.setColor(Color.argb(55, 45, 55, 72));


        // 启用硬件加速
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0, 0, w, h);
        blurController.updateBlurViewSize();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 应用旋转
        canvas.save();
        canvas.rotate(rotation, rect.centerX(), rect.centerY());

        // 绘制模糊内容
        canvas.translate(selectionMargin, selectionMargin);
        blurController.draw(canvas);
        canvas.translate(-selectionMargin, -selectionMargin);

        // 绘制模糊效果覆盖
        blurPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(rect, blurPaint);

        canvas.restore();

        // 绘制选择和操作元素
        drawSelectionElements(canvas);
    }

    private void drawSelectionElements(Canvas canvas) {
        if (!isSelected) return;
        float dotRadius = 4.2f * density;
        RectF selectionRect = new RectF(
                rect.left + selectionPaint.getStrokeWidth() + dotRadius,
                rect.top + selectionPaint.getStrokeWidth() + dotRadius,
                rect.right - selectionPaint.getStrokeWidth() - dotRadius,
                rect.bottom - selectionPaint.getStrokeWidth() - dotRadius
        );

        // 绘制选择边框
        canvas.drawRect(selectionRect, selectionPaint);

        // 更新按钮和手柄位置
        updateButtonPositions(selectionRect);

        // 绘制操作按钮
        canvas.drawBitmap(copyIcon, copyButtonRect.left, copyButtonRect.top, null);
        canvas.drawBitmap(deleteIcon, deleteButtonRect.left, deleteButtonRect.top, null);

        // 绘制旋转手柄
        canvas.drawBitmap(rotateIcon, rotateHandleTopRight.left, rotateHandleTopRight.top, null);
        canvas.drawBitmap(rotateIcon, rotateHandleBottomLeft.left, rotateHandleBottomLeft.top, null);

        // 绘制调整手柄（小圆点）

        canvas.drawCircle(resizeHandleTop.centerX(), resizeHandleTop.centerY(), dotRadius, resizeDotPaint);
        canvas.drawCircle(resizeHandleBottom.centerX(), resizeHandleBottom.centerY(), dotRadius, resizeDotPaint);
        canvas.drawCircle(resizeHandleLeft.centerX(), resizeHandleLeft.centerY(), dotRadius, resizeDotPaint);
        canvas.drawCircle(resizeHandleRight.centerX(), resizeHandleRight.centerY(), dotRadius, resizeDotPaint);
    }

    private void updateButtonPositions(RectF selectionRect) {
        float buttonMargin = 12 * density;
        float centerY = rect.centerY();

        // 决定按钮位置（上方或下方）
        float buttonsY;
        if (centerY < ((BlurOverlayFrameLayout) getParent()).getScreenCenterY()) {
            buttonsY = selectionRect.bottom + buttonMargin;
        } else {
            buttonsY = selectionRect.top - buttonMargin - buttonSize;
        }

        // 复制按钮（左侧）
        copyButtonRect.set(
                rect.centerX() - buttonSize - 20,
                buttonsY,
                rect.centerX() - 20,
                buttonsY + buttonSize
        );

        // 删除按钮（右侧）
        deleteButtonRect.set(
                rect.centerX() + 20,
                buttonsY,
                rect.centerX() + buttonSize + 20,
                buttonsY + buttonSize
        );

        // 旋转手柄位置
        float rotateOffset = selectionMargin;
        rotateHandleTopRight.set(
                selectionRect.right + rotateOffset - handleSize / 2,
                selectionRect.top - rotateOffset - handleSize / 2,
                selectionRect.right + rotateOffset + handleSize / 2,
                selectionRect.top - rotateOffset + handleSize / 2
        );

        rotateHandleBottomLeft.set(
                selectionRect.left - rotateOffset - handleSize / 2,
                selectionRect.bottom + rotateOffset - handleSize / 2,
                selectionRect.left - rotateOffset + handleSize / 2,
                selectionRect.bottom + rotateOffset + handleSize / 2
        );

        // 调整手柄位置
        float dotRadius = 3 * density;
        resizeHandleTop.set(
                selectionRect.centerX() - dotRadius,
                selectionRect.top - dotRadius,
                selectionRect.centerX() + dotRadius,
                selectionRect.top + dotRadius
        );
        resizeHandleBottom.set(
                selectionRect.centerX() - dotRadius,
                selectionRect.bottom - dotRadius,
                selectionRect.centerX() + dotRadius,
                selectionRect.bottom + dotRadius
        );
        resizeHandleLeft.set(
                selectionRect.left - dotRadius,
                selectionRect.centerY() - dotRadius,
                selectionRect.left + dotRadius,
                selectionRect.centerY() + dotRadius
        );
        resizeHandleRight.set(
                selectionRect.right - dotRadius,
                selectionRect.centerY() - dotRadius,
                selectionRect.right + dotRadius,
                selectionRect.centerY() + dotRadius
        );
    }

    // 创建图标的辅助方法
    private Bitmap createIconBitmap(int color, String text) {
        Bitmap bitmap = Bitmap.createBitmap((int) buttonSize, (int) buttonSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(color);
        paint.setAntiAlias(true);

        // 绘制圆形背景
        canvas.drawCircle(buttonSize / 2, buttonSize / 2, buttonSize / 2, paint);

        // 绘制文字
        paint.setColor(Color.WHITE);
        paint.setTextSize(buttonSize * 0.5f);
        paint.setTextAlign(Paint.Align.CENTER);

        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = buttonSize / 2 - (fm.ascent + fm.descent) / 2;
        canvas.drawText(text, buttonSize / 2, textY, paint);

        return bitmap;
    }

    private Bitmap createRotateIconBitmap() {
        Bitmap bitmap = Bitmap.createBitmap((int) handleSize, (int) handleSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(Color.parseColor("#4299E1"));
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);

        // 绘制旋转箭头
        Path path = new Path();
        path.moveTo(handleSize * 0.3f, handleSize * 0.7f);
        path.lineTo(handleSize * 0.5f, handleSize * 0.3f);
        path.lineTo(handleSize * 0.7f, handleSize * 0.7f);
        path.close();

        canvas.drawPath(path, paint);

        // 绘制小圆点
        paint.setColor(Color.WHITE);
        canvas.drawCircle(handleSize / 2, handleSize / 2, handleSize * 0.1f, paint);

        return bitmap;
    }

    // 模糊控制器设置方法
    public BlurViewFacade setupWith(BlurTarget target, BlurAlgorithm algorithm,
                                    float scaleFactor, boolean applyNoise) {
        blurController.destroy();
        if (BlurTarget.canUseHardwareRendering) {
            // Ignores the blur algorithm, always uses RenderEffect
            blurController = new RenderNodeBlurController(this, target, overlayColor, scaleFactor, applyNoise);
        } else {
            blurController = new PreDrawBlurController(this, target, overlayColor, algorithm, scaleFactor, applyNoise);
        }

        return blurController;
    }

    public BlurViewFacade setupWith(BlurTarget rootView, float scaleFactor, boolean applyNoise) {
        BlurAlgorithm algorithm;
        if (BlurTarget.canUseHardwareRendering) {
            // Ignores the blur algorithm, always uses RenderNodeBlurController and RenderEffect
            algorithm = null;
        } else {
            algorithm = new RenderScriptBlur(getContext());
        }
        return setupWith(rootView, algorithm, scaleFactor, applyNoise);
    }

    public BlurViewFacade setupWith(BlurTarget rootView) {
        return setupWith(rootView, BlurController.DEFAULT_SCALE_FACTOR, true);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        blurController.setBlurAutoUpdate(false);
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

    // 视图操作和方法
    public void setSelected(boolean selected) {
        this.isSelected = selected;
        invalidate();
    }

    public boolean isSelected() {
        return isSelected;
    }

    public RectF getRect() {
        return new RectF(rect);
    }

    public void setRect(float left, float top, float right, float bottom) {
        rect.set(left, top, right, bottom);
        updateLayout();
    }

    private void updateLayout() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        params.width = (int) rect.width();
        params.height = (int) rect.height();
        params.leftMargin = (int) rect.left;
        params.topMargin = (int) rect.top;
        requestLayout();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 处理视图自身的事件
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.d(BlurController.TAG, "-------BlurRectView down");
                return handleTouchDown(x, y);

            case MotionEvent.ACTION_MOVE:
                handleTouchMove(x, y);
                return true;

            case MotionEvent.ACTION_UP:
                handleTouchUp(x, y);
                return true;
        }
        return super.onTouchEvent(event);
    }

    // 触摸事件处理（由父容器调用）
    public boolean handleTouchDown(float x, float y) {
        // 1. 检查是否点中操作按钮
        if (isSelected) {
            if (isInCopyButton(x, y)) {
                ((BlurOverlayFrameLayout) getParent()).duplicateSelectedView();
                return true;
            }
            if (isInDeleteButton(x, y)) {
                ((BlurOverlayFrameLayout) getParent()).removeSelectedView();
                return true;
            }
        }

        // 2. 检查是否点中旋转手柄
        if (isSelected && isInRotateHandle(x, y)) {
            touchMode = BlurOverlayFrameLayout.MODE_ROTATE;
            // 记录初始旋转角度
            startRotation = rotation;
            // 计算初始角度（相对于矩形中心）
            float centerX = rect.centerX();
            float centerY = rect.centerY();
            startAngle = (float) Math.toDegrees(Math.atan2(y - centerY, x - centerX));
            return true;
        }

        // 3. 检查是否点中调整手柄
        if (isSelected && isInResizeHandle(x, y)) {
            touchMode = BlurOverlayFrameLayout.MODE_RESIZE;
            resizeHandleType = getResizeHandleType(x, y);
            initResizeState(x, y); // 初始化调整状态
            return true;
        }

        // 4. 检查是否点中矩形边框线（附近）
        if (isSelected && isOnBorder(x, y)) {
            touchMode = BlurOverlayFrameLayout.MODE_RESIZE;
            resizeHandleType = getBorderHandleType(x, y);
            initResizeState(x, y); // 初始化调整状态
            return true;
        }

        // 5. 如果点中自己，则进入移动模式
        if (contains(x, y)) {
            touchMode = BlurOverlayFrameLayout.MODE_MOVE;
            return true;
        }

        return false;
    }

    public void handleTouchMove(float x, float y) {
        float dx = x - lastX;
        float dy = y - lastY;

        switch (touchMode) {
            case BlurOverlayFrameLayout.MODE_MOVE:
                // 移动视图
                setRect(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy);
                break;

            case BlurOverlayFrameLayout.MODE_ROTATE:
                float centerX = rect.centerX();
                float centerY = rect.centerY();
                // 计算当前角度（相对于矩形中心）
                float currentAngle = (float) Math.toDegrees(Math.atan2(y - centerY, x - centerX));
                // 计算旋转角度（相对于初始角度）
                float rotationAngle = currentAngle - startAngle;
                // 应用旋转（基于初始旋转角度）
                setRotation(startRotation + rotationAngle);
                break;

            case BlurOverlayFrameLayout.MODE_RESIZE:
                resize(x, y);
                break;
        }

        lastX = x;
        lastY = y;
    }

    public void handleTouchUp(float x, float y) {
        // 重置触摸模式
        touchMode = BlurOverlayFrameLayout.MODE_NONE;
    }

    public boolean contains(float x, float y) {
        // 创建旋转矩阵
        matrix.reset();
        matrix.setRotate(-rotation, rect.centerX(), rect.centerY());

        // 反向旋转触摸点
        float[] point = new float[]{x, y};
        matrix.mapPoints(point);

        return rect.contains(point[0], point[1]);
    }

    private boolean isInRotateHandle(float x, float y) {
        // 修复：旋转后仍能检测到旋转手柄
        matrix.reset();
        matrix.setRotate(-rotation, rect.centerX(), rect.centerY());
        float[] point = {x, y};
        matrix.mapPoints(point);

        return rotateHandleTopRight.contains(point[0], point[1]) ||
                rotateHandleBottomLeft.contains(point[0], point[1]);
    }

    private boolean isInResizeHandle(float x, float y) {
        // 修复：旋转后仍能检测到调整手柄
        matrix.reset();
        matrix.setRotate(-rotation, rect.centerX(), rect.centerY());
        float[] point = {x, y};
        matrix.mapPoints(point);

        return resizeHandleTop.contains(point[0], point[1]) ||
                resizeHandleBottom.contains(point[0], point[1]) ||
                resizeHandleLeft.contains(point[0], point[1]) ||
                resizeHandleRight.contains(point[0], point[1]);
    }

    // 在BlurRectView类中修改isOnBorder方法
    private boolean isOnBorder(float x, float y) {
        // 创建旋转矩阵
        matrix.reset();
        matrix.setRotate(-rotation, rect.centerX(), rect.centerY());
        float[] point = {x, y};
        matrix.mapPoints(point);
        float rotatedX = point[0];
        float rotatedY = point[1];

        // 计算矩形边框线（比矩形大12dp）
        RectF selectionRect = new RectF(
                rect.left - selectionMargin,
                rect.top - selectionMargin,
                rect.right + selectionMargin,
                rect.bottom + selectionMargin
        );

        // 减小检测区域16
        float touchThreshold = 16 * density; // 8dp

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

    private int getBorderHandleType(float x, float y) {
        // 创建旋转矩阵
        matrix.reset();
        matrix.setRotate(-rotation, rect.centerX(), rect.centerY());
        float[] point = {x, y};
        matrix.mapPoints(point);
        float rotatedX = point[0];
        float rotatedY = point[1];

        // 计算矩形边框线（比矩形大12dp）
        RectF selectionRect = new RectF(
                rect.left - selectionMargin,
                rect.top - selectionMargin,
                rect.right + selectionMargin,
                rect.bottom + selectionMargin
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

    private int getResizeHandleType(float x, float y) {
        // 修复：旋转后仍能检测到调整手柄类型
        matrix.reset();
        matrix.setRotate(-rotation, rect.centerX(), rect.centerY());
        float[] point = {x, y};
        matrix.mapPoints(point);

        if (resizeHandleTop.contains(point[0], point[1])) return RESIZE_TOP;
        if (resizeHandleBottom.contains(point[0], point[1])) return RESIZE_BOTTOM;
        if (resizeHandleLeft.contains(point[0], point[1])) return RESIZE_LEFT;
        if (resizeHandleRight.contains(point[0], point[1])) return RESIZE_RIGHT;
        return -1;
    }

    private boolean isInCopyButton(float x, float y) {
        return copyButtonRect.contains(x, y);
    }

    private boolean isInDeleteButton(float x, float y) {
        return deleteButtonRect.contains(x, y);
    }

    // 初始化调整操作
    private void initResizeState(float x, float y) {
        resizeStartX = x;
        resizeStartY = y;
        initialRect.set(rect);
    }

    // 调整大小
    private void resize(float x, float y) {
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
                if (newTop < initialRect.bottom - defaultSize) {
                    rect.top = newTop;
                }
                break;

            case RESIZE_BOTTOM:
                float newBottom = initialRect.bottom + rotatedDy;
                if (newBottom > initialRect.top + defaultSize) {
                    rect.bottom = newBottom;
                }
                break;

            case RESIZE_LEFT:
                float newLeft = initialRect.left + rotatedDx;
                if (newLeft < initialRect.right - defaultSize) {
                    rect.left = newLeft;
                }
                break;

            case RESIZE_RIGHT:
                float newRight = initialRect.right + rotatedDx;
                if (newRight > initialRect.left + defaultSize) {
                    rect.right = newRight;
                }
                break;
        }

        // 确保矩形在边界内
        constrainToBounds();
        updateLayout();
        invalidate();
    }

    private void constrainToBounds() {
        View parent = (View) getParent();
        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();

        // 左边界
        if (rect.left < 0) {
            rect.left = 0;
        }
        // 右边界
        if (rect.right > parentWidth) {
            rect.right = parentWidth;
        }
        // 上边界
        if (rect.top < 0) {
            rect.top = 0;
        }
        // 下边界
        if (rect.bottom > parentHeight) {
            rect.bottom = parentHeight;
        }

        // 确保最小尺寸
        float minSize = 88 * density;
        if (rect.width() < minSize) {
            if (resizeHandleType == RESIZE_LEFT) {
                rect.left = rect.right - minSize;
            } else {
                rect.right = rect.left + minSize;
            }
        }
        if (rect.height() < minSize) {
            if (resizeHandleType == RESIZE_TOP) {
                rect.top = rect.bottom - minSize;
            } else {
                rect.bottom = rect.top + minSize;
            }
        }
    }
}