package eightbitlab.com.blurview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.util.Log;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.io.IOException;
import java.io.InputStream;

public class SvgRenderer {
    private static final String TAG = "SvgRenderer";
    private SVG svg;
    private float targetWidth;  // 目标绘制宽度
    private float targetHeight; // 目标绘制高度

    /**
     * 加载 SVG 文件（从 assets 目录）
     * @param context 上下文
     * @param svgFileName SVG 文件名（如 "icon.svg"）
     * @param targetWidth 目标绘制宽度（像素）
     * @param targetHeight 目标绘制高度（像素）
     */
    public SvgRenderer(Context context, String svgFileName, float targetWidth, float targetHeight) {
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;
        loadSvgFromAssets(context, svgFileName);
    }

    /**
     * 从 assets 加载并解析 SVG
     */
    private void loadSvgFromAssets(Context context, String svgFileName) {
        try (InputStream inputStream = context.getAssets().open(svgFileName)) {
            svg = SVG.getFromInputStream(inputStream);
            // 可选：设置 SVG 的最大尺寸（避免过大）
            svg.setDocumentWidth(targetWidth);
            svg.setDocumentHeight(targetHeight);
        } catch (IOException | SVGParseException e) {
            Log.e(TAG, "加载或解析 SVG 失败: " + e.getMessage());
            svg = null;
        }
    }

    /**
     * 将 SVG 绘制到 Canvas 上
     * @param canvas 目标画布
     * @param x 绘制起始 X 坐标（像素）
     * @param y 绘制起始 Y 坐标（像素）
     * @param colorFilter 颜色过滤（可选，null 表示不修改颜色）
     */
    public void draw(Canvas canvas, float x, float y, ColorFilter colorFilter) {
        if (svg == null) {
            Log.w(TAG, "SVG 未加载，无法绘制");
            return;
        }

        // 创建绘制用的 Paint（可选：调整抗锯齿、颜色等）
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (colorFilter != null) {
            paint.setColorFilter(colorFilter);
        }

        // 计算缩放比例（如果 SVG 原始尺寸与目标尺寸不一致）
        float scaleX = targetWidth / svg.getDocumentWidth();
        float scaleY = targetHeight / svg.getDocumentHeight();
        float scale = Math.min(scaleX, scaleY); // 保持宽高比

        // 保存画布状态
        canvas.save();
        // 平移画布到目标位置
        canvas.translate(x, y);
        // 缩放画布以适配目标尺寸
        canvas.scale(scale, scale);
        // 恢复画布状态
        canvas.restore();
    }

    /**
     * 释放资源（可选）
     */
    public void release() {
        if (svg != null) {
            svg = null;
        }
    }
}
