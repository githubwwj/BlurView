package com.appxy.blurview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import com.appxy.tinyscanner.R;

public class BlurRectView extends View {

    // 矩形边框
    private final RectF viewRect = new RectF();
    private final RectF mDrawRect = new RectF(0, 0, 0, 0); // 绘制时的相对坐标
    private final RectF parentViewRect;

    private BlurRect mBlurRect = null;

    // 触摸状态
    private static final int MODE_NONE = 0;
    private static final int MODE_MOVE = 1;
    private static final int MODE_ROTATE = 2;
    private static final int MODE_RESIZE = 5;
    private static final int MODE_DELETED = 6;
    private int touchMode = MODE_NONE;
    private float lastX, lastY;
    private final RectF dragRect = new RectF();

    // 尺寸转换
    private float rectMin;
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
    /**
     * 图层外边框间距
     */
    private float blurMargin;
    private float menuHeightSpace;

    // 操作按钮
    private Bitmap copyIcon;
    private Bitmap deleteIcon;
    private Bitmap leftRotateIcon;
    private Bitmap rightRotateIcon;
    private Paint menuPaint;
    private Paint selectionPaint; // 选中状态边框
    private Paint resizeDotPaint; // 调整大小手柄画笔

    private BlurController blurController;
    private RelativeLayout.LayoutParams layoutParams;
    private float initialTouchX, initialTouchY; // 初始触摸点的绝对坐标（屏幕坐标）
    private float initialLeft, initialTop; // 初始视图的left和top（父容器坐标）

    public BlurRectView(Context context, RectF initRect, RectF parentViewRect, BlurController blurController) {
        super(context);
        this.blurController = blurController;
        this.parentViewRect = parentViewRect;
        this.viewRect.set(initRect);
        this.mDrawRect.set(0, 0, initRect.width(), initRect.height());
        init();
    }

    public interface BlurRectListener {
        void onDelete(BlurRectView view);

        void onCopy(BlurRectView view);
    }

    private BlurRectListener blurRectListener;

    public void setBlurRectListener(BlurRectListener blurRectListener) {
        this.blurRectListener = blurRectListener;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (null != getLayoutParams()) {
            layoutParams = (RelativeLayout.LayoutParams) getLayoutParams();
        }
    }

    private void init() {
        // 尺寸转换
        density = getResources().getDisplayMetrics().density;
        rectMin = 18 * density;
        frameMargin = 6 * density;
        blurMargin = 23 * density; // 从模糊位图距离上下左右之间的距离
        menuHeightSpace = 67 * density; // 从模糊图层最下边到菜单最下边

        // 创建操作图标
        copyIcon = getBitmapFromSvg(R.drawable.blurview_copy);
        deleteIcon = getBitmapFromSvg(R.drawable.blurview_delete);
        leftRotateIcon = getBitmapFromSvg(R.drawable.blurview_left);
        rightRotateIcon = getBitmapFromSvg(R.drawable.blurview_right);
//        handleSize = leftRotateIcon.getWidth();
        copyDeleteBtnSize = copyIcon.getWidth();

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

        // 菜单位置（根据模糊区域中心与页面中心的关系）
        float screenCenterY = parentViewRect.height() / 2f;
        float centerY = viewRect.centerY();
        float blurRectTop;
        float blurRectBottom;
        if (centerY < screenCenterY) {
            // 在屏幕上半部分，菜单显示在下方（在矩形边框线下方）
            blurRectTop = blurMargin;
            blurRectBottom = mDrawRect.height() - menuHeightSpace;
        } else {
            // 在屏幕下半部分，菜单显示在上方（在矩形边框线上方）
            blurRectTop = menuHeightSpace;
            blurRectBottom = mDrawRect.height() - blurMargin;
        }
        addBlurRect(blurMargin, blurRectTop, viewRect.width() - blurMargin, blurRectBottom);

        // 硬件加速开启
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    // 高效命中测试方法
    public boolean isPointInside(float parentX, float parentY) {
        // 转换为视图局部坐标
        float localX = parentX - getLeft();
        float localY = parentY - getTop();
        return mDrawRect.contains(localX, localY);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mBlurRect = null;
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
        viewRect.set(0, 0, w, h);
        blurController.updateBlurViewSize();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        BlurRect blurRect = mBlurRect;
        // 根据当前操作模式决定是否显示操作按钮
        boolean showButtons = touchMode != MODE_MOVE &&
                touchMode != MODE_ROTATE &&
                touchMode != MODE_RESIZE;
        blurRect.draw(canvas, isSelected(), showButtons);
        canvas.drawRect(mDrawRect, selectionPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 记录初始触摸点的绝对坐标（屏幕坐标）
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                // 记录视图的初始布局位置（父容器坐标）
                initialLeft = getLeft();
                initialTop = getTop();

                handleTouchDown(x, y);
                return true;

            case MotionEvent.ACTION_MOVE:
                // 计算当前触摸点的绝对坐标和局部坐标
                float currentRawX = event.getRawX();
                float currentRawY = event.getRawY();
                handleTouchMove(x, y, currentRawX, currentRawY);
                return true;
            case MotionEvent.ACTION_UP:
                handleTouchUp(x, y);
                return true;

            case MotionEvent.ACTION_CANCEL:
                touchMode = MODE_NONE;
                invalidate(); // 确保重绘
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void handleTouchDown(float x, float y) {
        lastX = x;
        lastY = y;

        // 1. 检查是否点中操作按钮
        if (mBlurRect != null && !isOffScreen(initialTouchX, initialTouchY)) {
            if (mBlurRect.isInCopyButton(x, y)) {
                copySelectedRect();
                touchMode = MODE_NONE;
                return;
            }
            if (mBlurRect.isInDeleteButton(x, y)) {
                deleteSelectedRect();
                touchMode = MODE_DELETED;
                return;
            }
        }

        // 2. 检查是否点中旋转手柄
        if (mBlurRect != null && !isOffScreen(initialTouchX, initialTouchY) && mBlurRect.isInRotateHandle(x, y)) {
            touchMode = MODE_ROTATE;
            // 记录初始旋转角度
            mBlurRect.startRotation = mBlurRect.rotation;
            // 计算初始角度（相对于矩形中心）
            float centerX = mBlurRect.mRect.centerX();
            float centerY = mBlurRect.mRect.centerY();
            mBlurRect.startAngle = (float) Math.toDegrees(Math.atan2(y - centerY, x - centerX));
            return;
        }

        // 3. 检查是否点中调整手柄
        if (mBlurRect != null && !isOffScreen(initialTouchX, initialTouchY) && mBlurRect.isInResizeHandle(x, y)) {
            touchMode = MODE_RESIZE;
            mBlurRect.resizeHandleType = mBlurRect.getResizeHandleType(x, y);
            mBlurRect.initResizeState(x, y); // 初始化调整状态
            return;
        }

        // 4. 检查是否点中矩形边框线（附近）
        if (mBlurRect != null && !isOffScreen(initialTouchX, initialTouchY) && mBlurRect.isOnBorder(x, y)) {
            touchMode = MODE_RESIZE;
            mBlurRect.resizeHandleType = mBlurRect.getBorderHandleType(x, y);
            mBlurRect.initResizeState(x, y); // 初始化调整状态
            return;
        }

        touchMode = MODE_MOVE;
    }

    private void handleTouchMove(float x, float y, float currentRawX, float currentRawY) {
        switch (touchMode) {
            case MODE_MOVE:
                // 计算绝对移动量（总偏移量）
                float totalDx = currentRawX - initialTouchX;
                float totalDy = currentRawY - initialTouchY;
                updateViewPosition(totalDx, totalDy);
                break;
            case MODE_ROTATE:
                if (mBlurRect != null && !isOffScreen(currentRawX, currentRawY)) {
                    float centerX = mBlurRect.mRect.centerX();
                    float centerY = mBlurRect.mRect.centerY();
                    // 计算当前角度（相对于矩形中心）
                    float currentAngle = (float) Math.toDegrees(Math.atan2(y - centerY, x - centerX));
                    // 计算旋转角度（相对于初始角度）
                    float rotationAngle = currentAngle - mBlurRect.startAngle;
                    // 应用旋转（基于初始旋转角度）
                    mBlurRect.setRotation(mBlurRect.startRotation + rotationAngle);
                    invalidate();
                }
                break;
            case MODE_RESIZE:
                if (mBlurRect != null && !isOffScreen(currentRawX, currentRawY)) {
                    mBlurRect.resize2(x, y, parentViewRect);
                    invalidate();
                }
                break;
        }

        lastX = x;
        lastY = y;
    }

    private void handleTouchUp(float x, float y) {
        if (touchMode == MODE_MOVE || touchMode == MODE_RESIZE) {
            // 检查矩形是否完全移出边界
            if (mBlurRect != null && mBlurRect.isOutside(viewRect)) {
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

    /**
     * 判断视图是否完全移出屏幕（不可见或超出屏幕边界）
     * @return true：已移出屏幕；false：在屏幕内可见
     */
    private boolean isOffScreen(float viewX, float viewY) {
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        int parentViewWidth = (int) parentViewRect.width();
        int parentViewHeight = (int) parentViewRect.height();
        // 5. 判断是否完全超出屏幕边界（四边任意一边超出即移出）
        return (viewX + viewWidth < 0) ||    // 右边缘 < 屏幕左边缘（完全左移出）
                (viewX > parentViewWidth) ||      // 左边缘 > 屏幕右边缘（完全右移出）
                (viewY + viewHeight < 0) ||   // 下边缘 < 屏幕上边缘（完全上移出）
                (viewY > parentViewHeight);       // 上边缘 > 屏幕下边缘（完全下移出）
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

        viewRect.left = newLeft;
        viewRect.top = newTop;
        viewRect.right = newLeft + viewRect.width();
        viewRect.bottom = newTop + viewRect.height();
    }


    // 添加新模糊矩形
    private void addBlurRect(float left, float top, float right, float bottom) {
        mBlurRect = new BlurRect(left, top, right, bottom);
        blurController.addBlurRect(mBlurRect);
    }

    // 复制选中矩形
    public void copySelectedRect() {
        if (mBlurRect != null && blurRectListener != null) {
            blurRectListener.onCopy(this);
        }
    }

    // 删除选中矩形
    public void deleteSelectedRect() {
        if (mBlurRect != null && blurRectListener != null) {
            mBlurRect.recycle();
            blurRectListener.onDelete(this);
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

        public Bitmap getBlurBitmap() {
            return blurBitmap;
        }

        public RectF getRectF() {
            return mRect;
        }

        // 新建矩形
        BlurRect(float left, float top, float right, float bottom) {
            mRect = new RectF(left, top, right, bottom);
        }

        // 复制构造
        BlurRect(BlurRect source) {
            mRect = new RectF(source.mRect);
            rotation = source.rotation;
        }

        // 更新旋转矩阵
        private void updateRotationMatrix() {
            rotationMatrix.reset();
            rotationMatrix.setRotate(rotation, mRect.centerX(), mRect.centerY());
            rotationMatrix.invert(inverseRotationMatrix);
        }

        void updateButtonPositions() {
            // 计算选中状态边框矩形（比矩形大12dp）
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
                buttonsY = selectionRect.bottom + buttonMargin;
            } else {
                // 在屏幕下半部分，菜单显示在上方（在矩形边框线上方）
                buttonsY = selectionRect.top - buttonMargin - copyDeleteBtnSize;
            }


            float halfMenuWidth = 104 * density / 2;
            menuRect.set(mRect.centerX() - halfMenuWidth,
                    buttonsY,
                    mRect.centerX() + halfMenuWidth,
                    buttonsY + 40 * density);


            // 复制按钮（左侧）
            deleteButtonRect.set(
                    mRect.centerX() - copyDeleteBtnSize - 11 * density,
                    buttonsY + 8 * density,
                    mRect.centerX() - 11 * density,
                    buttonsY + copyDeleteBtnSize + 8 * density
            );

            // 删除按钮（右侧）
            copyButtonRect.set(
                    mRect.centerX() + 11 * density,
                    buttonsY + 8 * density,
                    mRect.centerX() + copyDeleteBtnSize + 11 * density,
                    buttonsY + copyDeleteBtnSize + 8 * density
            );

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
            menuPaint.setColor(Color.argb(100, 200, 0, 0));
            menuPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(mRect, menuPaint);
            blurController.setBlurRect(this);
            blurController.draw(canvas);

            // 绘制选中状态
            if (isSelected) {
                // 更新按钮位置
                updateButtonPositions();

                // 绘制选中状态边框（比矩形大12dp）
                selectionRect.set(
                        mRect.left - frameMargin,
                        mRect.top - frameMargin,
                        mRect.right + frameMargin,
                        mRect.bottom + frameMargin
                );
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

            // 计算矩形边框线（比矩形大12dp）
            selectionRect.set(
                    mRect.left - frameMargin,
                    mRect.top - frameMargin,
                    mRect.right + frameMargin,
                    mRect.bottom + frameMargin
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

        void move(float dx, float dy, RectF boundary) {
            // 移动矩形
            mRect.offset(dx, dy);
        }

        // 初始化调整操作
        void initResizeState(float x, float y) {
            resizeStartX = x;
            resizeStartY = y;
            initialRect.set(viewRect);
            initialCenterX = mRect.centerX();
            initialCenterY = mRect.centerY();
        }

        // 重写的resize方法 - 修复旋转后只调整一边的问题
        void resize2(float x, float y, RectF boundary) {
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
                    RelativeLayout.LayoutParams params = layoutParams;
                    if (params == null) {
                        return;
                    }
                    // 计算新的 left/top
                    int newTop = (int) (initialRect.top + rotatedDy);
                    if (newTop < initialRect.bottom - rectMin) {
                        mRect.top = newTop;
                    }
                    params.topMargin = newTop;
                    setLayoutParams(params);
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

            // 确保矩形在边界内
            constrainToBounds(boundary, rectMin);
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

            // 确保矩形在边界内
            constrainToBounds(boundary, rectMin);
        }

        /**
         * @param boundary 边界
         * @param minSize  最小宽高
         */
        void constrainToBounds(RectF boundary, float minSize) {
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

            // 确保最小尺寸
            if (mRect.width() < minSize) {
                if (resizeHandleType == RESIZE_LEFT) {
                    mRect.left = mRect.right - minSize;
                } else {
                    mRect.right = mRect.left + minSize;
                }
            }
            if (mRect.height() < minSize) {
                if (resizeHandleType == RESIZE_TOP) {
                    mRect.top = mRect.bottom - minSize;
                } else {
                    mRect.bottom = mRect.top + minSize;
                }
            }
        }

        void setRotation(float angle) {
            rotation = angle;
        }

        // 检查矩形是否完全在边界外
        boolean isOutside(RectF boundary) {
            return mRect.right < boundary.left ||
                    mRect.left > boundary.right ||
                    mRect.bottom < boundary.top ||
                    mRect.top > boundary.bottom;
        }

        public void recycle() {
            if (null != blurBitmap && !blurBitmap.isRecycled()) {
                blurBitmap.recycle();
                blurBitmap = null;
            }
        }

        public float getRotate() {
            return rotation;
        }
    }

}