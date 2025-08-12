package eightbitlab.com.blurview;

import static eightbitlab.com.blurview.BlurController.DEFAULT_SCALE_FACTOR;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.VectorDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.eightbitlab.blurview.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BlurOverlayView extends View {

    // 矩形边框
    private final RectF borderRect = new RectF();

    // 模糊图层管理
    private final List<BlurRect> blurRectList = new ArrayList<>();
    private BlurRect selectedRect = null;

    // 触摸状态
    private static final int MODE_NONE = 0;
    private static final int MODE_MOVE = 1;
    private static final int MODE_ROTATE = 2;
    private static final int MODE_ADD_BY_DRAG = 4;
    private static final int MODE_RESIZE = 5;
    private static final int MODE_DELETED = 6;
    private int touchMode = MODE_NONE;
    private float lastX, lastY;
    private final RectF dragRect = new RectF();

    // 尺寸转换
    private float defaultSize;
    private float rectMin;
    private float copyRectOffset;
    private float handleSize;
    private float buttonSize;
    private float selectionMargin; // 图层外边框间距
    private float density;

    // 操作按钮
    private Bitmap copyIcon;
    private Bitmap deleteIcon;
    private Bitmap rotateIcon;
    private final Paint previewPaint = new Paint();


    private BlurController blurController = new NoOpController();
    @ColorInt
    private int overlayColor;
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
        handleSize = 24 * density;
        buttonSize = 32 * density;
        selectionMargin = 12 * density; // 图层外边框间距

        // 创建操作图标
        copyIcon = createSvg(R.drawable.blurview_copy);
        deleteIcon = createSvg(R.drawable.blurview_delete);
        deleteIcon = createSvg(R.drawable.blurview_left);
        deleteIcon = createSvg(R.drawable.blurview_right);
        rotateIcon = createRotateIconBitmap();

        // 硬件加速开启
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }


    // ------------------------设置模糊图层的代码------------
    public BlurViewFacade setupWith(@NonNull BlurTarget target, BlurAlgorithm algorithm, float scaleFactor, boolean applyNoise) {
        blurController.destroy();
        if (BlurTarget.canUseHardwareRendering) {
            // Ignores the blur algorithm, always uses RenderEffect
            blurController = new RenderNodeBlurController(this, target, overlayColor, scaleFactor, applyNoise);
        } else {
            blurController = new BlurRectController(this, target, overlayColor, algorithm, scaleFactor, applyNoise);
        }

        return blurController;
    }

    public BlurViewFacade setupWith(@NonNull BlurTarget rootView, float scaleFactor, boolean applyNoise) {
        BlurAlgorithm algorithm;
        if (BlurTarget.canUseHardwareRendering) {
            // Ignores the blur algorithm, always uses RenderNodeBlurController and RenderEffect
            algorithm = null;
        } else {
            algorithm = new RenderScriptBlur(getContext());
        }
        return setupWith(rootView, algorithm, scaleFactor, applyNoise);
    }

    public BlurViewFacade setupWith(@NonNull BlurTarget rootView) {
        return setupWith(rootView, DEFAULT_SCALE_FACTOR, true);
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
                rect.draw(canvas, rect == selectedRect, showButtons);
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

        // 绘制尺寸提示
        previewPaint.setColor(Color.WHITE);
        previewPaint.setTextSize(28);

        String sizeText = String.format(Locale.getDefault(), "%.0f×%.0f", dragRect.width(), dragRect.height());
        float textWidth = previewPaint.measureText(sizeText);

        canvas.drawText(sizeText,
                dragRect.centerX() - textWidth / 2,
                dragRect.centerY() + previewPaint.getTextSize() / 2, previewPaint);
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
                selectedRect = null;
                invalidate(); // 确保重绘
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void handleTouchDown(float x, float y) {
        lastX = x;
        lastY = y;

        // 1. 检查是否点中操作按钮
        if (selectedRect != null && selectedRect.isVisible(borderRect)) {
            if (selectedRect.isInCopyButton(x, y)) {
                copySelectedRect();
                touchMode = MODE_NONE;
                return;
            }
            if (selectedRect.isInDeleteButton(x, y)) {
                deleteSelectedRect();
                touchMode = MODE_DELETED;
                return;
            }
        }

        // 2. 检查是否点中旋转手柄
        if (selectedRect != null && selectedRect.isVisible(borderRect) && selectedRect.isInRotateHandle(x, y)) {
            touchMode = MODE_ROTATE;
            // 记录初始旋转角度
            selectedRect.startRotation = selectedRect.rotation;
            // 计算初始角度（相对于矩形中心）
            float centerX = selectedRect.rect.centerX();
            float centerY = selectedRect.rect.centerY();
            selectedRect.startAngle = (float) Math.toDegrees(Math.atan2(y - centerY, x - centerX));
            return;
        }

        // 3. 检查是否点中调整手柄
        if (selectedRect != null && selectedRect.isVisible(borderRect) && selectedRect.isInResizeHandle(x, y)) {
            touchMode = MODE_RESIZE;
            selectedRect.resizeHandleType = selectedRect.getResizeHandleType(x, y);
            selectedRect.initResizeState(x, y); // 初始化调整状态
            return;
        }

        // 4. 检查是否点中矩形边框线（附近）
        if (selectedRect != null && selectedRect.isVisible(borderRect) && selectedRect.isOnBorder(x, y)) {
            touchMode = MODE_RESIZE;
            selectedRect.resizeHandleType = selectedRect.getBorderHandleType(x, y);
            selectedRect.initResizeState(x, y); // 初始化调整状态
            return;
        }

        // 5. 检查是否点中已有矩形
        BlurRect clickedRect = null;
        for (int i = blurRectList.size() - 1; i >= 0; i--) {
            BlurRect rect = blurRectList.get(i);
            if (rect.isVisible(borderRect) && rect.contains(x, y)) {
                clickedRect = rect;
                selectedRect = clickedRect;
                touchMode = MODE_MOVE;
                invalidate();
                return;
            }
        }

        // 6. 如果没有点中任何矩形，开始添加新矩形
        touchMode = MODE_ADD_BY_DRAG;
        dragRect.set(x, y, x, y);
    }

    private void handleTouchMove(float x, float y) {
        float dx = x - lastX;
        float dy = y - lastY;

        switch (touchMode) {
            case MODE_MOVE:
                if (selectedRect != null) {
                    // 允许移动到边界外
                    selectedRect.rect.offset(dx, dy);
                    invalidate();
                }
                break;

            case MODE_ROTATE:
                if (selectedRect != null && selectedRect.isVisible(borderRect)) {
                    float centerX = selectedRect.rect.centerX();
                    float centerY = selectedRect.rect.centerY();
                    // 计算当前角度（相对于矩形中心）
                    float currentAngle = (float) Math.toDegrees(Math.atan2(y - centerY, x - centerX));
                    // 计算旋转角度（相对于初始角度）
                    float rotationAngle = currentAngle - selectedRect.startAngle;
                    // 应用旋转（基于初始旋转角度）
                    selectedRect.setRotation(selectedRect.startRotation + rotationAngle);
                    invalidate();
                }
                break;

            case MODE_ADD_BY_DRAG:
                dragRect.right = x;
                dragRect.bottom = y;
                // 确保矩形不为负
                if (dragRect.left > dragRect.right) {
                    float temp = dragRect.left;
                    dragRect.left = dragRect.right;
                    dragRect.right = temp;
                }
                if (dragRect.top > dragRect.bottom) {
                    float temp = dragRect.top;
                    dragRect.top = dragRect.bottom;
                    dragRect.bottom = temp;
                }
                invalidate();
                break;

            case MODE_RESIZE:
                if (selectedRect != null && selectedRect.isVisible(borderRect)) {
                    selectedRect.resize(x, y, borderRect);
                    invalidate();
                }
                break;
        }

        lastX = x;
        lastY = y;
    }

    private void handleTouchUp(float x, float y) {
        if (touchMode == MODE_ADD_BY_DRAG) {
            // 添加拖动创建的矩形
            addBlurRect(dragRect.left, dragRect.top, dragRect.right, dragRect.bottom);
        } else if (touchMode == MODE_MOVE || touchMode == MODE_RESIZE) {
            // 检查矩形是否完全移出边界
            if (selectedRect != null && selectedRect.isOutside(borderRect)) {
                deleteSelectedRect();
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

    // 添加新模糊矩形
    public void addBlurRect(float left, float top, float right, float bottom) {
        BlurRect rect = new BlurRect(left, top, right, bottom);
        rect.constrainToBounds(borderRect, defaultSize); // 确保在边界内
        blurRectList.add(rect);
        selectedRect = rect;
        blurController.addBlurRect(rect);
    }

    // 复制选中矩形
    public void copySelectedRect() {
        if (selectedRect != null && selectedRect.isVisible(borderRect)) {
            BlurRect copy = new BlurRect(selectedRect);
            // 向右下偏移
            copy.rect.offset(selectedRect.rect.width() + copyRectOffset,
                    selectedRect.rect.height() + copyRectOffset);
            if (selectedRect.rect.width() >= defaultSize) {
                copy.constrainToBounds(borderRect, defaultSize); // 确保在边界内
            } else {
                copy.constrainToBounds(borderRect, rectMin); // 确保在边界内
            }
            blurRectList.add(copy);
            selectedRect = copy;
            invalidate();
        }
    }

    // 删除选中矩形
    public void deleteSelectedRect() {
        if (selectedRect != null) {
            blurRectList.remove(selectedRect);
            selectedRect = null;
            invalidate();
        }
    }

    // 创建操作图标
    private VectorDrawable createSvg(int id) {
        VectorDrawable svgDrawable = (VectorDrawable) getContext().getDrawable(id);
        int drawableWidth = svgDrawable.getIntrinsicWidth();
        int drawableHeight = svgDrawable.getIntrinsicHeight();
        svgDrawable.setBounds(0, 0, drawableWidth, drawableHeight);
        return svgDrawable;
    }

    // 创建旋转图标
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

    // 模糊矩形类
    public class BlurRect {
        RectF rect;
        float rotation = 0;
        Paint blurPaint;
        Paint selectionPaint; // 选中状态边框
        Paint resizeDotPaint; // 调整大小手柄画笔

        // 旋转操作状态
        float startRotation = 0; // 旋转开始时的角度
        float startAngle = 0;    // 旋转开始时的触摸点角度

        // 操作按钮位置
        RectF copyButtonRect = new RectF();
        RectF deleteButtonRect = new RectF();

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

        // 新建矩形
        BlurRect(float left, float top, float right, float bottom) {
            rect = new RectF(left, top, right, bottom);
            initPaint();
        }

        // 复制构造
        BlurRect(BlurRect source) {
            rect = new RectF(source.rect);
            rotation = source.rotation;
            initPaint();
        }

        private void initPaint() {
            // 模糊效果
            blurPaint = new Paint();
            blurPaint.setColor(Color.argb(55, 45, 55, 72)); // 深蓝色半透明
            blurPaint.setStyle(Paint.Style.FILL);
            blurPaint.setAntiAlias(true);

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
        }

        // 更新旋转矩阵
        private void updateRotationMatrix() {
            rotationMatrix.reset();
            rotationMatrix.setRotate(rotation, rect.centerX(), rect.centerY());
            rotationMatrix.invert(inverseRotationMatrix);
        }

        void updateButtonPositions() {
            // 计算选中状态边框矩形（比矩形大12dp）
            selectionRect.set(
                    rect.left - selectionMargin,
                    rect.top - selectionMargin,
                    rect.right + selectionMargin,
                    rect.bottom + selectionMargin
            );

            // 更新旋转矩阵
            updateRotationMatrix();

            // 菜单位置（根据模糊区域中心与页面中心的关系）
            float centerY = rect.centerY();
            float screenCenterY = getHeight() / 2f;

            // 按钮位置（在矩形上方或下方）
            float buttonsY;
            float buttonMargin = 12 * density; // 12dp
            if (centerY < screenCenterY) {
                // 在屏幕上半部分，菜单显示在下方（在矩形边框线下方）
                buttonsY = selectionRect.bottom + buttonMargin;
            } else {
                // 在屏幕下半部分，菜单显示在上方（在矩形边框线上方）
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

            // 旋转手柄（在矩形边框线外侧12dp处）
            float rotateOffset = selectionMargin; // 12dp
            // 右上角旋转手柄
            rotateHandleTopRight.set(
                    selectionRect.right + rotateOffset - handleSize / 2,
                    selectionRect.top - rotateOffset - handleSize / 2,
                    selectionRect.right + rotateOffset + handleSize / 2,
                    selectionRect.top - rotateOffset + handleSize / 2
            );

            // 左下角旋转手柄
            rotateHandleBottomLeft.set(
                    selectionRect.left - rotateOffset - handleSize / 2,
                    selectionRect.bottom + rotateOffset - handleSize / 2,
                    selectionRect.left - rotateOffset + handleSize / 2,
                    selectionRect.bottom + rotateOffset + handleSize / 2
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
            canvas.rotate(rotation, rect.centerX(), rect.centerY());

            // 绘制模糊矩形
            canvas.drawRect(rect, blurPaint);

            blurController.draw(canvas);

            // 绘制选中状态
            if (isSelected) {
                // 更新按钮位置
                updateButtonPositions();

                // 绘制选中状态边框（比矩形大12dp）
                selectionRect.set(
                        rect.left - selectionMargin,
                        rect.top - selectionMargin,
                        rect.right + selectionMargin,
                        rect.bottom + selectionMargin
                );
                canvas.drawRect(selectionRect, selectionPaint);

                // 绘制两个旋转手柄（在矩形边框线外侧）
                canvas.drawBitmap(rotateIcon,
                        rotateHandleTopRight.left,
                        rotateHandleTopRight.top, null);

                canvas.drawBitmap(rotateIcon,
                        rotateHandleBottomLeft.left,
                        rotateHandleBottomLeft.top, null);

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
                canvas.drawBitmap(copyIcon, copyButtonRect.left, copyButtonRect.top, null);
                canvas.drawBitmap(deleteIcon, deleteButtonRect.left, deleteButtonRect.top, null);
            }
        }

        boolean contains(float x, float y) {
            // 创建旋转矩阵
            matrix.reset();
            matrix.setRotate(-rotation, rect.centerX(), rect.centerY());

            // 反向旋转触摸点
            float[] point = new float[]{x, y};
            matrix.mapPoints(point);

            return rect.contains(point[0], point[1]);
        }

        boolean isInRotateHandle(float x, float y) {
            // 修复：旋转后仍能检测到旋转手柄
            matrix.reset();
            matrix.setRotate(-rotation, rect.centerX(), rect.centerY());
            float[] point = {x, y};
            matrix.mapPoints(point);

            return rotateHandleTopRight.contains(point[0], point[1]) ||
                    rotateHandleBottomLeft.contains(point[0], point[1]);
        }

        boolean isInResizeHandle(float x, float y) {
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

        // 在BlurRect类中修改isOnBorder方法
        boolean isOnBorder(float x, float y) {
            // 创建旋转矩阵
            matrix.reset();
            matrix.setRotate(-rotation, rect.centerX(), rect.centerY());
            float[] point = {x, y};
            matrix.mapPoints(point);
            float rotatedX = point[0];
            float rotatedY = point[1];

            // 计算矩形边框线（比矩形大12dp）
            selectionRect.set(
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

        int getBorderHandleType(float x, float y) {
            // 创建旋转矩阵
            matrix.reset();
            matrix.setRotate(-rotation, rect.centerX(), rect.centerY());
            float[] point = {x, y};
            matrix.mapPoints(point);
            float rotatedX = point[0];
            float rotatedY = point[1];

            // 计算矩形边框线（比矩形大12dp）
            selectionRect.set(
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

        int getResizeHandleType(float x, float y) {
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

        boolean isInCopyButton(float x, float y) {
            return copyButtonRect.contains(x, y);
        }

        boolean isInDeleteButton(float x, float y) {
            return deleteButtonRect.contains(x, y);
        }

        void move(float dx, float dy, RectF boundary) {
            // 移动矩形
            rect.offset(dx, dy);
        }

        // 初始化调整操作
        void initResizeState(float x, float y) {
            resizeStartX = x;
            resizeStartY = y;
            initialRect.set(rect);
            initialCenterX = rect.centerX();
            initialCenterY = rect.centerY();
        }

        // 重写的resize方法 - 修复旋转后只调整一边的问题
        void resize(float x, float y, RectF boundary) {
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
                        rect.top = newTop;
                    }
                    break;

                case RESIZE_BOTTOM:
                    float newBottom = initialRect.bottom + rotatedDy;
                    if (newBottom > initialRect.top + rectMin) {
                        rect.bottom = newBottom;
                    }
                    break;

                case RESIZE_LEFT:
                    float newLeft = initialRect.left + rotatedDx;
                    if (newLeft < initialRect.right - rectMin) {
                        rect.left = newLeft;
                    }
                    break;

                case RESIZE_RIGHT:
                    float newRight = initialRect.right + rotatedDx;
                    if (newRight > initialRect.left + rectMin) {
                        rect.right = newRight;
                    }
                    break;
            }

            // 确保矩形在边界内
            constrainToBounds(boundary, rectMin);
        }

        /**
         * @param boundary 边界
         * @param minSize  最小宽高
         */
        void constrainToBounds(RectF boundary, float minSize) {
            // 左边界
            if (rect.left < boundary.left) {
                rect.left = boundary.left;
            }
            // 右边界
            if (rect.right > boundary.right) {
                rect.right = boundary.right;
            }
            // 上边界
            if (rect.top < boundary.top) {
                rect.top = boundary.top;
            }
            // 下边界
            if (rect.bottom > boundary.bottom) {
                rect.bottom = boundary.bottom;
            }

            // 确保最小尺寸
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

        void setRotation(float angle) {
            rotation = angle;
        }

        // 检查矩形是否可见（在边界内）
        boolean isVisible(RectF boundary) {
            return RectF.intersects(rect, boundary) &&
                    rect.width() > 0 &&
                    rect.height() > 0;
        }

        // 检查矩形是否完全在边界外
        boolean isOutside(RectF boundary) {
            return rect.right < boundary.left ||
                    rect.left > boundary.right ||
                    rect.bottom < boundary.top ||
                    rect.top > boundary.bottom;
        }
    }
}