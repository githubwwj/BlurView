package com.appxy.blurview;

import java.util.Objects;

/**
 * 按比例因子缩放宽高
 */
public class SizeScaler {

    // Bitmap size should be divisible by ROUNDING_VALUE to meet stride requirement.
    // This will help avoiding an extra bitmap allocation when passing the bitmap to RenderScript for blur.
    // Usually it's 16, but on Samsung devices it's 64 for some reason.
    /**
     * 位图尺寸应能被ROUNDING_VALUE整除以满足步长(stride)要求，这样可以避免在将位图传递给RenderScript进行模糊处理时额外分配位图。
     * 通常这个值是16，但在三星设备上是64（原因不明）。
     */
    private static final int ROUNDING_VALUE = 64;
    /**
     * 缩放因子。
     */
    private final float scaleFactor;
    /**
     * 是否不需要步长对齐的标志。
     */
    private final boolean noStrideAlignment;

    public SizeScaler(float scaleFactor) {
        this(scaleFactor, false);
    }

    public SizeScaler(float scaleFactor, boolean noStrideAlignment) {
        this.scaleFactor = scaleFactor;
        this.noStrideAlignment = noStrideAlignment;
    }

    Size scale(float width, float height) {
        // 计算未四舍五入的缩放后宽度
        int nonRoundedScaledWidth = downscaleSize(width);

        // 对宽度进行四舍五入（对齐到 ROUNDING_VALUE）
        int scaledWidth = roundSize(nonRoundedScaledWidth);

        // 计算实际使用的缩放因子（因为宽度可能被调整了）
        float roundingScaleFactor = width / scaledWidth;

        // 根据实际缩放因子计算高度（向上取整）
        int scaledHeight = (int) Math.ceil(height / roundingScaleFactor);
        return new Size(scaledWidth, scaledHeight);
    }

    Size scale(Size size) {
        return scale(size.width, size.height);
    }

    /**
     * 检查缩放后尺寸是否为零,避免创建无效位图
     */
    boolean isZeroSized(float measuredWidth, float measuredHeight) {
        return downscaleSize(measuredHeight) == 0 || downscaleSize(measuredWidth) == 0;
    }

    /**
     * Rounds a value to the nearest divisible by {@link #ROUNDING_VALUE} to meet stride requirement
     * 如果禁用步长对齐，直接返回原值
     * 如果已经是倍数，直接返回
     * 否则调整到比原值大的最近倍数
     */
    private int roundSize(int value) {
        if (noStrideAlignment) {
            return value;
        }
        if (value % ROUNDING_VALUE == 0) {
            return value;
        }
        return value - (value % ROUNDING_VALUE) + ROUNDING_VALUE;
    }

    private int downscaleSize(float value) {
        // 向上取整函数：返回大于或等于参数的最小整数
        return (int) Math.ceil(value / scaleFactor);
    }

    static class Size {

        final int width;
        final int height;

        Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Size size = (Size) o;
            return width == size.width && height == size.height;
        }

        @Override
        public int hashCode() {
            return Objects.hash(width, height);
        }

        @Override
        public String toString() {
            return "Size{" +
                    "width=" + width +
                    ", height=" + height +
                    '}';
        }
    }
}
