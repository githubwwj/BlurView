package eightbitlab.com.blurview;

import android.graphics.Canvas;

public interface BlurController extends BlurViewFacade {

    float DEFAULT_SCALE_FACTOR = 4f;
    float DEFAULT_BLUR_RADIUS = 16f;
    String TAG = "BlurView";

    /**
     * Draws blurred content on given canvas
     *
     * @return true if BlurView should proceed with drawing itself and its children
     */
    boolean draw(Canvas canvas);

    /**
     * Must be used to notify Controller when BlurView's size has changed
     */
    void updateBlurViewSize();

    default void addBlurRect(BlurOverlayView.BlurRect blurRect) {

    }

    default void setBlurRect(BlurOverlayView.BlurRect blurRect) {

    }

    /**
     * Frees allocated resources
     */
    void destroy();
}
