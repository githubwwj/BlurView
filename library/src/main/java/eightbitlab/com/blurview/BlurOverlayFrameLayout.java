package eightbitlab.com.blurview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BlurOverlayFrameLayout extends FrameLayout {

    // 触摸状态常量
    public static final int MODE_NONE = 0;
    public static final int MODE_MOVE = 1;
    public static final int MODE_ROTATE = 2;
    public static final int MODE_ADD_BY_DRAG = 4;
    public static final int MODE_RESIZE = 5;
    public static final int MODE_DELETED = 6;

    // 视图状态
    private final List<BlurRectView> blurViews = new ArrayList<>();
    private BlurRectView selectedView = null;
    private int touchMode = MODE_NONE;
    private float lastX, lastY;
    private final RectF dragRect = new RectF();

    // 尺寸参数
    private float defaultSize;
    private float density;
    private Paint previewPaint = new Paint();

    // 边界矩形
    private RectF borderRect = new RectF();
    private float screenCenterY;
    private float selectionMargin = 0; // 图层外边框间距

    // 模糊控制器相关
    private BlurTarget blurTarget;
    private BlurAlgorithm algorithm;
    private static final float DEFAULT_SCALE_FACTOR = BlurController.DEFAULT_SCALE_FACTOR;

    public BlurOverlayFrameLayout(Context context) {
        super(context);
        init();
    }

    public BlurOverlayFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        defaultSize = 88 * density;
        previewPaint.setStrokeWidth(1 * density);
        selectionMargin = 12 * density;
        defaultSize += selectionMargin;

        // 设置不裁剪子视图，以便显示操作按钮等
        setClipChildren(false);
        if (!BlurTarget.canUseHardwareRendering) {
            algorithm = new RenderScriptBlur(getContext());
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 先尝试让子视图处理事件
        boolean handled = super.dispatchTouchEvent(ev);

        // 如果子视图没有处理，再由容器处理
        if (!handled) {
            handled = onTouchEvent(ev);
        }

        return handled;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        borderRect.set(0, 0, w, h);
        screenCenterY = h / 2f;
    }

    public float getScreenCenterY() {
        return screenCenterY;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.d(BlurController.TAG,"-------BlurFrameLayout down");
                lastX = x;
                lastY = y;
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
                if (selectedView != null) {
                    selectedView.setSelected(false);
                    selectedView = null;
                }
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void handleTouchDown(float x, float y) {
        // 1. 如果当前有选中的视图，先让它处理事件
        if (selectedView != null) {
            if (selectedView.handleTouchDown(x, y)) {
                // 如果视图处理了事件，则直接返回
                return;
            }
        }

        // 2. 检查是否点中其他视图
        for (int i = blurViews.size() - 1; i >= 0; i--) {
            BlurRectView view = blurViews.get(i);
            if (view.contains(x, y)) {
                // 选中新视图
                if (selectedView != null) {
                    selectedView.setSelected(false);
                }
                view.setSelected(true);
                selectedView = view;
                touchMode = MODE_MOVE;
                invalidate();
                return;
            }
        }

        // 3. 如果没有点中任何视图，开始拖拽创建新视图
        touchMode = MODE_ADD_BY_DRAG;
        dragRect.set(x, y, x, y);
        invalidate();
    }

    private void handleTouchMove(float x, float y) {
        float dx = x - lastX;
        float dy = y - lastY;

        switch (touchMode) {
            case MODE_MOVE:
                if (selectedView != null) {
                    // 移动选中的视图
                    selectedView.handleTouchMove(x, y);
                }
                break;

            case MODE_ADD_BY_DRAG:
                // 更新拖拽矩形
                dragRect.right = x;
                dragRect.bottom = y;
                ensureRectPositive(dragRect);
                invalidate();
                break;
        }

        lastX = x;
        lastY = y;
    }

    private void handleTouchUp(float x, float y) {
        switch (touchMode) {
            case MODE_ADD_BY_DRAG:
                addNewBlurView(dragRect);
                break;

            case MODE_DELETED:
                // 视图已删除，不执行其他操作
                break;

            default:
                if (selectedView != null) {
                    selectedView.handleTouchUp(x, y);
                }
        }

        touchMode = MODE_NONE;
        invalidate();
    }

    private void ensureRectPositive(RectF rect) {
        if (rect.left > rect.right) {
            float temp = rect.left;
            rect.left = rect.right;
            rect.right = temp;
        }
        if (rect.top > rect.bottom) {
            float temp = rect.top;
            rect.top = rect.bottom;
            rect.bottom = temp;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        // 绘制拖拽预览
        if (touchMode == MODE_ADD_BY_DRAG) {
            drawDragPreview(canvas);
        }
    }

    private void drawDragPreview(Canvas canvas) {
        previewPaint.setColor(Color.argb(100, 66, 153, 225));
        previewPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(dragRect, previewPaint);

        previewPaint.setStyle(Paint.Style.STROKE);
        previewPaint.setColor(Color.parseColor("#4299E1"));
        canvas.drawRect(dragRect, previewPaint);

        // 绘制尺寸提示
        previewPaint.setColor(Color.WHITE);
        previewPaint.setTextSize(28);
        String sizeText = String.format(Locale.getDefault(), "%.0f×%.0f",
                dragRect.width(), dragRect.height());
        float textWidth = previewPaint.measureText(sizeText);
        canvas.drawText(sizeText,
                dragRect.centerX() - textWidth / 2,
                dragRect.centerY() + previewPaint.getTextSize() / 2, previewPaint);
    }

    public void addNewBlurView(RectF rect) {
        BlurRectView view = new BlurRectView(getContext());

        // 设置模糊控制器
        if (blurTarget != null) {
            view.setupWith(blurTarget, algorithm, DEFAULT_SCALE_FACTOR, true);
        }

        // 计算外边框间距
        float margin = selectionMargin;

        // 确保内容区域不小于最小尺寸
        float minContentSize = defaultSize - 2 * margin; // 最小内容区域大小
        float contentWidth = rect.width();
        float contentHeight = rect.height();

        if (contentWidth < minContentSize) {
            contentWidth = minContentSize;
        }
        if (contentHeight < minContentSize) {
            contentHeight = minContentSize;
        }

        // 计算带间距的视图大小
        int viewWidth = (int) (contentWidth + 2 * margin);
        int viewHeight = (int) (contentHeight + 2 * margin);

        // 计算带间距的视图位置
        int leftMargin = (int) (rect.left - margin);
        int topMargin = (int) (rect.top - margin);

        // 获取父容器尺寸
        int parentWidth = getWidth();
        int parentHeight = getHeight();

        // 确保视图不超出父容器边界
        if (leftMargin < 0) leftMargin = 0;
        if (topMargin < 0) topMargin = 0;
        if (leftMargin + viewWidth > parentWidth) {
            // 如果宽度超出，尝试调整位置或大小
            if (viewWidth > parentWidth) {
                // 视图宽度大于父容器，调整大小
                viewWidth = parentWidth;
            }
            leftMargin = parentWidth - viewWidth;
        }
        if (topMargin + viewHeight > parentHeight) {
            // 如果高度超出，尝试调整位置或大小
            if (viewHeight > parentHeight) {
                // 视图高度大于父容器，调整大小
                viewHeight = parentHeight;
            }
            topMargin = parentHeight - viewHeight;
        }

        // 设置位置和大小
        LayoutParams params = new LayoutParams(viewWidth, viewHeight);
        params.leftMargin = leftMargin;
        params.topMargin = topMargin;

        addView(view, params);
        blurViews.add(view);

        // 选中新视图
        if (selectedView != null) {
            selectedView.setSelected(false);
        }
        view.setSelected(true);
        selectedView = view;
    }

    public void removeSelectedView() {
        if (selectedView != null) {
            removeView(selectedView);
            blurViews.remove(selectedView);
            selectedView = null;
            touchMode = MODE_DELETED;
            invalidate();
        }
    }

    public void duplicateSelectedView() {
        if (selectedView != null) {
            BlurRectView copy = new BlurRectView(getContext());
            // 复制模糊控制器设置
            if (blurTarget != null) {
                copy.setupWith(blurTarget, algorithm, DEFAULT_SCALE_FACTOR, true);
            }

            // 获取原始视图的布局参数
            FrameLayout.LayoutParams originalParams = (FrameLayout.LayoutParams) selectedView.getLayoutParams();
            int width = originalParams.width;
            int height = originalParams.height;
            int leftMargin = originalParams.leftMargin + (int) (width * 0.2f);
            int topMargin = originalParams.topMargin + (int) (height * 0.2f);

            // 创建新视图的布局参数
            LayoutParams params = new LayoutParams(width, height);
            params.leftMargin = leftMargin;
            params.topMargin = topMargin;

            // 确保新视图不超出父容器边界
            int parentWidth = getWidth();
            int parentHeight = getHeight();
            if (params.leftMargin < 0) params.leftMargin = 0;
            if (params.topMargin < 0) params.topMargin = 0;
            if (params.leftMargin + width > parentWidth) {
                params.leftMargin = parentWidth - width;
                if (params.leftMargin < 0) params.leftMargin = 0;
            }
            if (params.topMargin + height > parentHeight) {
                params.topMargin = parentHeight - height;
                if (params.topMargin < 0) params.topMargin = 0;
            }

            addView(copy, params);
            blurViews.add(copy);


            // 选中新副本
            if (selectedView != null) {
                selectedView.setSelected(false);
            }
            copy.setSelected(true);
            selectedView = copy;
        }
    }

    // 设置模糊目标
    public void setBlurTarget(BlurTarget target) {
        this.blurTarget = target;
    }

    // 设置模糊算法
    public void setBlurAlgorithm(BlurAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    // 更新所有模糊视图
    public void updateAllBlurViews() {
        for (BlurRectView view : blurViews) {
            if (blurTarget != null) {
                view.setupWith(blurTarget, algorithm, DEFAULT_SCALE_FACTOR, true);
            }
        }
    }
}