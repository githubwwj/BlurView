package eightbitlab.com.blurview;

import static java.lang.Math.min;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Blur using RenderScript, processed on GPU when device drivers support it.
 * Requires API 17+
 *
 * @deprecated because RenderScript is deprecated and its hardware acceleration is not guaranteed.
 * On API 31+ an alternative hardware accelerated blur implementation is automatically used.
 */
@Deprecated
public class RenderScriptBlur implements BlurAlgorithm {

    /**
     * 当位图被缩放时，系统会计算目标像素周围4个源像素的加权平均值
     * 当位图被拉伸、缩小或旋转时，这个标志确保位图边缘平滑，避免出现锯齿状像素块。
     */
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

    /**
     * 它负责管理RenderScript的资源、内存分配和脚本执行。
     * 在模糊处理中，我们需要一个RenderScript实例来创建内存分配（Allocation）和加载模糊脚本（ScriptIntrinsicBlur）。
     */
    private final RenderScript renderScript;

    /**
     * 专门用于实现高斯模糊。它封装了高斯模糊的算法，并且经过高度优化以在多种设备上高效运行。
     */
    private final ScriptIntrinsicBlur blurScript;

    /**
     * 在模糊过程中，输入数据（位图）被转换为输入Allocation（inAllocation），
     * 然后模糊脚本将处理结果写入输出Allocation（outAllocation）。
     * 最后，我们将输出Allocation的内容复制回位图。
     * 使用Allocation的好处是，数据可以在本地内存中高效处理，避免了在Java层进行大量数据拷贝。
     */
    private Allocation outAllocation;

    private int lastBitmapWidth = -1;
    private int lastBitmapHeight = -1;
    private Bitmap.Config lastBitmapConfig;

    /**
     * @param context Context to create the {@link RenderScript}
     */
    public RenderScriptBlur(@NonNull Context context) {
        renderScript = RenderScript.create(context);
        blurScript = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
    }

    private boolean canReuseAllocation(Bitmap bitmap) {
        return bitmap != null &&
                outAllocation != null &&
                bitmap.getWidth() == lastBitmapWidth &&
                bitmap.getHeight() == lastBitmapHeight &&
                bitmap.getConfig() == lastBitmapConfig;
    }

    /**
     * @param bitmap     bitmap to blur
     * @param blurRadius blur radius (1..25)
     * @return blurred bitmap
     */
    @Override
    public Bitmap blur(@NonNull Bitmap bitmap, float blurRadius) {
        try {
            // 1. Allocation是RenderScript中表示内存分配的对象,
            // 输入的Bitmap创建Allocation，这样RenderScript可以访问位图像素数据
            // 使用USAGE_SHARED标志（默认），允许复用位图内存
            Allocation inAllocation = Allocation.createFromBitmap(renderScript, bitmap);

            // 2. 检查是否可以重用输出内存分配
            if (!canReuseAllocation(bitmap)) {
                // 3. 如果尺寸变化，销毁旧分配，创建新分配，类型与输入分配相同
                if (outAllocation != null) {
                    outAllocation.destroy();
                }
                // 创建新分配，类型与输入分配相同，记录当前位图尺寸以便下次检查
                outAllocation = Allocation.createTyped(renderScript, inAllocation.getType());
                lastBitmapWidth = bitmap.getWidth();
                lastBitmapHeight = bitmap.getHeight();
                lastBitmapConfig = bitmap.getConfig();
            }
            // 4. 设置高斯模糊半径,限制最大半径为25（性能考虑）
            blurScript.setRadius(min(blurRadius, 25f));
            // 5. 将输入分配设置到模糊脚本
            blurScript.setInput(inAllocation);
            // 6. 对每个像素执行模糊计算,结果写入输出分配
            blurScript.forEach(outAllocation);
            // 7. 将计算结果从输出分配复制回原始位图，这样原始位图就包含模糊后的图像
            outAllocation.copyTo(bitmap);
            // 释放输入分配资源
            inAllocation.destroy();
        } catch (Exception e) {
            // Can potentially crash because RenderScript context was released by someone else via RenderScript.releaseAllContexts()
            // Some Glide transformations can cause this.
            Log.e("BlurView", "RenderScript blur failed. Rendering unblurred snapshot", e);
        }
        return bitmap;
    }

    @Override
    public final void destroy() {
        if (blurScript != null) {
            blurScript.destroy();
        }
        if (renderScript != null) {
            renderScript.destroy();
        }
        if (outAllocation != null) {
            outAllocation.destroy();
        }
    }

    @Override
    public boolean canModifyBitmap() {
        return true;
    }

    @NonNull
    @Override
    public Bitmap.Config getSupportedBitmapConfig() {
        return Bitmap.Config.ARGB_8888;
    }

    @Override
    public void render(@NonNull Canvas canvas, @NonNull Bitmap bitmap) {
        paint.setAntiAlias(true);
        canvas.drawBitmap(bitmap, 0f, 0f, paint);
    }
}
