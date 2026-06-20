package smartisanos.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.SparseIntArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import com.smartisanos.music.R;
import java.lang.ref.WeakReference;

public class SwitchEx extends CheckBox {
    private static final int CLICK_TIMEOUT = 300;
    private static final int HANDLER_MESSAGE_SET_CHECKED = 1;
    private static final int MAX_ALPHA = 255;
    private static final int MIN_ALPHA = 191;
    private static final int SHADOW_ALPHA_ANIM_DURATION = 200;
    public static final int STYLE_DARK = 2;
    public static final int STYLE_LIGHT = 1;
    public static final int SWITCH_BOTTOM = 0;
    public static final int SWITCH_FRAME = 2;
    public static final int SWITCH_FRAME_PRESSED = 5;
    public static final int SWITCH_MASK = 3;
    public static final int SWITCH_PRESSED = 4;
    public static final int SWITCH_UNPRESSED = 1;
    private static final int[] DRAWABLE_IDS = {
        R.drawable.switch_ex_bottom,
        R.drawable.switch_ex_unpressed,
        R.drawable.switch_ex_frame,
        R.drawable.switch_ex_mask,
        R.drawable.switch_ex_pressed,
        R.drawable.switch_ex_frame_pressed,
    };
    private static final int[] DARK_DRAWABLE_IDS = {
        R.drawable.switch_ex_bottom_dark,
        R.drawable.switch_ex_unpressed_dark,
        R.drawable.switch_ex_frame_dark,
        R.drawable.switch_ex_mask_dark,
        R.drawable.switch_ex_pressed_dark,
        R.drawable.switch_ex_frame_pressed_dark,
    };
    private static int[] drawableIds = DRAWABLE_IDS;
    private static int style = STYLE_LIGHT;
    private static boolean isClean;
    private static final Pair<Float, Float> BOTTOM_DEF_SCALE = new Pair<>(286.0f, 144.0f);
    private static final Pair<Float, Float> UNPRESSED_DEF_SCALE = new Pair<>(286.0f, 144.0f);
    private static final Pair<Float, Float> FRAME_DEF_SCALE = new Pair<>(198.0f, 144.0f);
    private static final Pair<Float, Float> MASK_DEF_SCALE = new Pair<>(198.0f, 144.0f);
    private static final Pair<Float, Float> PRESSED_DEF_SCALE = new Pair<>(286.0f, 144.0f);
    private static final Pair<Float, Float> FRAME_PRESSED_DEF_SCALE = new Pair<>(198.0f, 144.0f);
    private static final RectF MASK_RECT = new RectF();
    private static final RectF BOTTOM_RECT = new RectF();
    private static final RectF FRAME_RECT = new RectF();
    private static final RectF FRAME_PRESSED_RECT = new RectF();
    private static final RectF PRESSED_RECT = new RectF();
    private static final PorterDuffXfermode XFERMODE = new PorterDuffXfermode(PorterDuff.Mode.SRC_IN);
    private static final Canvas SHARED_CANVAS = new Canvas();
    private static final Paint SHARED_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static Bitmap bottom;
    private static Bitmap buttonNormal;
    private static Bitmap buttonPressed;
    private static Bitmap frame;
    private static Bitmap framePressed;
    private static Bitmap mask;
    private static Bitmap currentButtonBitmap;
    private static Bitmap alphaOnBitmap;
    private static Bitmap alphaOffBitmap;
    private static Bitmap onBitmap;
    private static Bitmap onBitmapPressed;
    private static Bitmap offBitmap;
    private static Bitmap offBitmapPressed;
    private static Pair<Float, Float> bottomScale;
    private static Pair<Float, Float> unpressedScale;
    private static Pair<Float, Float> frameScale;
    private static Pair<Float, Float> maskScale;
    private static Pair<Float, Float> pressedScale;
    private static Pair<Float, Float> framePressedScale;
    private static float buttonWidth;
    private static float maskWidth;
    private static float maskHeight;
    private static float buttonOnPosition;
    private static float buttonOffPosition;
    private static int cachedBitmapDensityDpi;

    private final Handler handler;
    private final Resources resources;
    private int alpha = MAX_ALPHA;
    private int pressedShadowAlpha;
    private boolean checked;
    private boolean restoring;
    private boolean broadcasting;
    private boolean touching;
    private boolean animating;
    private boolean vibrateFeedback;
    private int touchSlop;
    private int bottomResId;
    private float buttonPosition;
    private float realPosition;
    private float firstDownX;
    private float firstDownY;
    private float buttonInitialPosition;
    private float velocity;
    private float animatedVelocity;
    private float animationPosition;
    private float extendedOffsetY;
    private ValueAnimator shadowAlphaAnimator;
    private ViewParent parent;
    private Runnable performClickRunnable;
    private CompoundButton.OnCheckedChangeListener onCheckedChangeListener;
    private CompoundButton.OnCheckedChangeListener onCheckedChangeWidgetListener;
    private View.OnClickListener onClickListener;

    private static final class CheckHandler extends Handler {
        private final WeakReference<SwitchEx> switchRef;

        CheckHandler(SwitchEx switchEx) {
            super(Looper.getMainLooper());
            switchRef = new WeakReference<>(switchEx);
        }

        @Override
        public void dispatchMessage(Message message) {
            SwitchEx switchEx = switchRef.get();
            if (message.what == HANDLER_MESSAGE_SET_CHECKED && switchEx != null) {
                switchEx.setChecked((Boolean) message.obj, false, true);
            }
        }
    }

    public SwitchEx(Context context) {
        this(context, null);
    }

    public SwitchEx(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.checkboxStyle);
    }

    public SwitchEx(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        resources = context.getResources();
        handler = new CheckHandler(this);
        setVibrateFeedbackEnabled(true);
        applySwitchStyle();
        initSwitchBitmap(resources);
        initView(context);
    }

    public static void clearSwitchBitmap() {
        clearSwitchBitmap(true);
    }

    public static void clearSwitchBitmap(boolean resetStyle) {
        bottom = null;
        buttonNormal = null;
        buttonPressed = null;
        frame = null;
        framePressed = null;
        mask = null;
        alphaOnBitmap = null;
        alphaOffBitmap = null;
        onBitmap = null;
        onBitmapPressed = null;
        offBitmap = null;
        offBitmapPressed = null;
        isClean = true;
        if (resetStyle) {
            style = STYLE_LIGHT;
            applySwitchStyle();
        }
    }

    public static void initSwitchBitmap(Resources resources) {
        if (isDensityDpiChanged(resources)) {
            clearSwitchBitmap(false);
        }
        if (bottom == null || buttonNormal == null || frame == null || mask == null
            || buttonPressed == null || framePressed == null) {
            bottom = bitmap(resources, drawableIds[SWITCH_BOTTOM]);
            buttonNormal = bitmap(resources, drawableIds[SWITCH_UNPRESSED]);
            frame = bitmap(resources, drawableIds[SWITCH_FRAME]);
            mask = bitmap(resources, drawableIds[SWITCH_MASK]);
            buttonPressed = bitmap(resources, drawableIds[SWITCH_PRESSED]);
            framePressed = bitmap(resources, drawableIds[SWITCH_FRAME_PRESSED]);
            currentButtonBitmap = buttonNormal;
            initDrawableScaleThroughDpi(resources);
        }
        SHARED_PAINT.setColor(0xffffffff);
        buttonWidth = unpressedScale.first;
        maskWidth = maskScale.first;
        maskHeight = maskScale.second;
        buttonOnPosition = buttonWidth / 2.0f;
        buttonOffPosition = maskWidth - (buttonWidth / 2.0f);
        initBitmap();
    }

    private static Bitmap bitmap(Resources resources, int resId) {
        return ((BitmapDrawable) resources.getDrawable(resId, null)).getBitmap();
    }

    private static void applySwitchStyle() {
        drawableIds = style == STYLE_DARK ? DARK_DRAWABLE_IDS : DRAWABLE_IDS;
    }

    private static Pair<Float, Float> changeScaleThroughDpi(Pair<Float, Float> scale, int densityDpi) {
        if (densityDpi == 480) {
            return scale;
        }
        return Pair.create(
            (densityDpi * scale.first) / 480.0f,
            (densityDpi * scale.second) / 480.0f
        );
    }

    private static void initDrawableScaleThroughDpi(Resources resources) {
        int densityDpi = resources.getConfiguration().densityDpi;
        bottomScale = changeScaleThroughDpi(BOTTOM_DEF_SCALE, densityDpi);
        unpressedScale = changeScaleThroughDpi(UNPRESSED_DEF_SCALE, densityDpi);
        frameScale = changeScaleThroughDpi(FRAME_DEF_SCALE, densityDpi);
        maskScale = changeScaleThroughDpi(MASK_DEF_SCALE, densityDpi);
        pressedScale = changeScaleThroughDpi(PRESSED_DEF_SCALE, densityDpi);
        framePressedScale = changeScaleThroughDpi(FRAME_PRESSED_DEF_SCALE, densityDpi);
        cachedBitmapDensityDpi = densityDpi;
    }

    private static boolean isDensityDpiChanged(Resources resources) {
        return cachedBitmapDensityDpi != resources.getConfiguration().densityDpi;
    }

    private static void setAccessibilityChecked(AccessibilityNodeInfo info, boolean checked) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            info.setChecked(checked
                ? AccessibilityNodeInfo.CHECKED_STATE_TRUE
                : AccessibilityNodeInfo.CHECKED_STATE_FALSE);
        } else {
            setAccessibilityCheckedLegacy(info, checked);
        }
    }

    @SuppressWarnings("deprecation")
    private static void setAccessibilityCheckedLegacy(AccessibilityNodeInfo info, boolean checked) {
        info.setChecked(checked);
    }

    private static void initBitmap() {
        isClean = false;
        if (alphaOnBitmap == null || alphaOffBitmap == null || onBitmap == null || offBitmap == null
            || onBitmapPressed == null || offBitmapPressed == null) {
            alphaOnBitmap = createBitmap(MIN_ALPHA, getRealPosition(buttonOnPosition), 0);
            alphaOffBitmap = createBitmap(MIN_ALPHA, getRealPosition(buttonOffPosition), 0);
            onBitmap = createBitmap(MAX_ALPHA, getRealPosition(buttonOnPosition), 0);
            onBitmapPressed = createBitmap(MAX_ALPHA, getRealPosition(buttonOnPosition), MAX_ALPHA);
            offBitmap = createBitmap(MAX_ALPHA, getRealPosition(buttonOffPosition), 0);
            offBitmapPressed = createBitmap(MAX_ALPHA, getRealPosition(buttonOffPosition), MAX_ALPHA);
        }
    }

    private static Bitmap createBitmap(int alpha, float realPosition, int shadowAlpha) {
        Bitmap bitmap = Bitmap.createBitmap(
            Math.round(maskScale.first),
            Math.round(maskScale.second),
            Bitmap.Config.ARGB_8888
        );
        SHARED_CANVAS.setBitmap(bitmap);
        SHARED_PAINT.setAlpha(alpha);
        MASK_RECT.set(0.0f, 0.0f, maskScale.first, maskScale.second);
        SHARED_CANVAS.drawBitmap(mask, null, MASK_RECT, SHARED_PAINT);
        SHARED_PAINT.setXfermode(XFERMODE);
        BOTTOM_RECT.set(realPosition, 0.0f, bottomScale.first + realPosition, bottomScale.second);
        SHARED_CANVAS.drawBitmap(bottom, null, BOTTOM_RECT, SHARED_PAINT);
        SHARED_PAINT.setXfermode(null);
        FRAME_RECT.set(0.0f, 0.0f, frameScale.first, frameScale.second);
        SHARED_CANVAS.drawBitmap(frame, null, FRAME_RECT, SHARED_PAINT);
        if (shadowAlpha > 0) {
            int oldAlpha = SHARED_PAINT.getAlpha();
            SHARED_PAINT.setAlpha(shadowAlpha);
            FRAME_PRESSED_RECT.set(0.0f, 0.0f, framePressedScale.first, framePressedScale.second);
            SHARED_CANVAS.drawBitmap(framePressed, null, FRAME_PRESSED_RECT, SHARED_PAINT);
            SHARED_PAINT.setAlpha(oldAlpha);
        }
        PRESSED_RECT.set(realPosition, 0.0f, pressedScale.first + realPosition, pressedScale.second);
        SHARED_CANVAS.drawBitmap(
            shadowAlpha == MAX_ALPHA ? buttonPressed : currentButtonBitmap,
            null,
            PRESSED_RECT,
            SHARED_PAINT
        );
        return bitmap;
    }

    private static float getRealPosition(float buttonPosition) {
        return buttonPosition - (buttonWidth / 2.0f);
    }

    private void initView(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        buttonPosition = checked ? buttonOnPosition : buttonOffPosition;
        realPosition = getRealPosition(buttonPosition);
        float density = resources.getDisplayMetrics().density;
        velocity = (350.0f * density) + 0.5f;
        extendedOffsetY = (2.0f * density) + 0.5f;
    }

    private void attemptClaimDrag() {
        parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
    }

    private void moveView(float position) {
        buttonPosition = position;
        realPosition = getRealPosition(position);
        invalidate();
    }

    private void startAnimation(boolean targetChecked) {
        animatedVelocity = targetChecked ? velocity : -velocity;
        animationPosition = buttonPosition;
        animating = true;
        doAnimation();
    }

    private void doAnimation() {
        animationPosition += (animatedVelocity * 16.0f) / 1000.0f;
        if (animationPosition <= buttonOffPosition) {
            stopAnimation();
            animationPosition = buttonOffPosition;
        } else if (animationPosition >= buttonOnPosition) {
            stopAnimation();
            animationPosition = buttonOnPosition;
        }
        moveView(animationPosition);
    }

    private void stopAnimation() {
        animating = false;
    }

    private void setCheckedFromUser(boolean checked) {
        handler.removeMessages(HANDLER_MESSAGE_SET_CHECKED);
        handler.sendMessageDelayed(
            handler.obtainMessage(HANDLER_MESSAGE_SET_CHECKED, checked),
            20L
        );
    }

    private void cancelShadowAlphaAnim() {
        if (shadowAlphaAnimator != null && shadowAlphaAnimator.isRunning()) {
            shadowAlphaAnimator.cancel();
        }
    }

    private boolean isShadowAlphaAnimRunning() {
        return shadowAlphaAnimator != null && shadowAlphaAnimator.isRunning();
    }

    private void startShadowAlphaAnimation() {
        if (shadowAlphaAnimator == null) {
            shadowAlphaAnimator = ValueAnimator.ofInt(MAX_ALPHA, 0);
            shadowAlphaAnimator.addUpdateListener(animation -> {
                pressedShadowAlpha = (Integer) animation.getAnimatedValue();
                invalidate();
            });
            shadowAlphaAnimator.setDuration(SHADOW_ALPHA_ANIM_DURATION);
        }
        shadowAlphaAnimator.start();
    }

    private void resetBitmaps() {
        alphaOnBitmap = null;
        alphaOffBitmap = null;
        onBitmap = null;
        onBitmapPressed = null;
        offBitmap = null;
        offBitmapPressed = null;
        initBitmap();
        invalidate();
    }

    private void setChecked(boolean checked, boolean moveImmediately, boolean fromUser) {
        if (this.checked == checked) {
            return;
        }
        this.checked = checked;
        if (moveImmediately) {
            buttonPosition = checked ? buttonOnPosition : buttonOffPosition;
            realPosition = getRealPosition(buttonPosition);
            invalidate();
        }
        if (broadcasting || restoring) {
            return;
        }
        broadcasting = true;
        if (onCheckedChangeListener != null) {
            onCheckedChangeListener.onCheckedChanged(this, this.checked);
        }
        if (onCheckedChangeWidgetListener != null) {
            onCheckedChangeWidgetListener.onCheckedChanged(this, this.checked);
        }
        if (fromUser && vibrateFeedback) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        broadcasting = false;
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void setChecked(boolean checked) {
        setChecked(checked, true, false);
    }

    @Override
    public void toggle() {
        setChecked(!checked);
    }

    @Override
    public boolean performClick() {
        if (onClickListener != null) {
            onClickListener.onClick(this);
        }
        startAnimation(!checked);
        setCheckedFromUser(!checked);
        return true;
    }

    @Override
    public void setOnClickListener(View.OnClickListener listener) {
        if (!isClickable()) {
            setClickable(true);
        }
        onClickListener = listener;
    }

    @Override
    public void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener listener) {
        onCheckedChangeListener = listener;
    }

    void setOnCheckedChangeWidgetListener(CompoundButton.OnCheckedChangeListener listener) {
        onCheckedChangeWidgetListener = listener;
    }

    public void setVibrateFeedbackEnabled(boolean enabled) {
        vibrateFeedback = enabled;
    }

    public void setSwitchDrawableStyle(int newStyle) {
        if (style == newStyle) {
            return;
        }
        style = newStyle;
        clearSwitchBitmap(false);
        applySwitchStyle();
        initSwitchBitmap(resources);
        invalidate();
    }

    public void setSwitchDrawables(SparseIntArray drawables) {
        drawableIds = new int[] {
            drawables.get(SWITCH_BOTTOM),
            drawables.get(SWITCH_UNPRESSED),
            drawables.get(SWITCH_FRAME),
            drawables.get(SWITCH_MASK),
            drawables.get(SWITCH_PRESSED),
            drawables.get(SWITCH_FRAME_PRESSED),
        };
        clearSwitchBitmap(false);
        initSwitchBitmap(resources);
        invalidate();
    }

    @Deprecated
    public void setSwitchDrawable(int style, Bitmap bitmap) {
        setSwitchDrawableStyle(STYLE_DARK);
    }

    public void setBottomDrawable(int resId) {
        if (bottomResId == resId) {
            return;
        }
        bottomResId = resId;
        bottom = bitmap(resources, resId);
        resetBitmaps();
    }

    public void setFrameDrawable(int resId) {
        frame = bitmap(resources, resId == -1 ? R.drawable.switch_ex_frame : resId);
        resetBitmaps();
    }

    @Override
    public void setEnabled(boolean enabled) {
        alpha = enabled ? MAX_ALPHA : MIN_ALPHA;
        super.setEnabled(enabled);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelShadowAlphaAnim();
        handler.removeMessages(HANDLER_MESSAGE_SET_CHECKED);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isClean || isDensityDpiChanged(resources)) {
            initSwitchBitmap(resources);
        }
        canvas.save();
        float onRealPosition = getRealPosition(buttonOnPosition);
        float offRealPosition = getRealPosition(buttonOffPosition);
        Bitmap bitmap;
        if (alpha == MAX_ALPHA) {
            if (realPosition == offRealPosition && !isShadowAlphaAnimRunning()) {
                bitmap = touching ? offBitmapPressed : offBitmap;
            } else if (realPosition == onRealPosition && !isShadowAlphaAnimRunning()) {
                bitmap = touching ? onBitmapPressed : onBitmap;
            } else {
                bitmap = createBitmap(alpha, realPosition, pressedShadowAlpha);
            }
        } else if (realPosition == offRealPosition) {
            bitmap = alphaOffBitmap;
        } else if (realPosition == onRealPosition) {
            bitmap = alphaOnBitmap;
        } else {
            bitmap = createBitmap(alpha, realPosition, pressedShadowAlpha);
        }
        SHARED_PAINT.setAlpha(alpha);
        canvas.drawBitmap(bitmap, 0.0f, extendedOffsetY, SHARED_PAINT);
        canvas.restore();
        if (realPosition <= offRealPosition) {
            stopAnimation();
            animationPosition = buttonOffPosition;
        } else if (realPosition >= onRealPosition) {
            stopAnimation();
            animationPosition = buttonOnPosition;
        } else if (animating) {
            doAnimation();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension((int) maskWidth, (int) (maskHeight + (extendedOffsetY * 2.0f)));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (animating || !isEnabled()) {
            return true;
        }
        int action = event.getAction();
        float x = event.getX();
        float y = event.getY();
        float deltaX = Math.abs(x - firstDownX);
        float deltaY = Math.abs(y - firstDownY);
        if (action == MotionEvent.ACTION_DOWN) {
            attemptClaimDrag();
            touching = true;
            cancelShadowAlphaAnim();
            pressedShadowAlpha = MAX_ALPHA;
            firstDownX = x;
            firstDownY = y;
            buttonInitialPosition = checked ? buttonOnPosition : buttonOffPosition;
        } else if (action == MotionEvent.ACTION_UP
            || action == MotionEvent.ACTION_MOVE
            || action == MotionEvent.ACTION_CANCEL) {
            buttonPosition = (buttonInitialPosition + event.getX()) - firstDownX;
            if (buttonPosition >= buttonOnPosition) {
                buttonPosition = buttonOnPosition;
            }
            if (buttonPosition <= buttonOffPosition) {
                buttonPosition = buttonOffPosition;
            }
            realPosition = getRealPosition(buttonPosition);
            if (action != MotionEvent.ACTION_MOVE) {
                touching = false;
                startShadowAlphaAnimation();
                float eventTime = event.getEventTime() - event.getDownTime();
                if (deltaY >= touchSlop || deltaX >= touchSlop || eventTime >= CLICK_TIMEOUT) {
                    boolean targetChecked = buttonPosition > ((buttonOnPosition - buttonOffPosition) / 2.0f) + buttonOffPosition;
                    startAnimation(targetChecked);
                    setCheckedFromUser(targetChecked);
                } else {
                    if (performClickRunnable == null) {
                        performClickRunnable = this::performClick;
                    }
                    if (!post(performClickRunnable)) {
                        performClick();
                    }
                }
            }
        }
        invalidate();
        return true;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        restoring = true;
        super.onRestoreInstanceState(state);
        restoring = false;
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(SwitchEx.class.getName());
        event.setChecked(checked);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(SwitchEx.class.getName());
        info.setCheckable(true);
        setAccessibilityChecked(info, checked);
    }
}
