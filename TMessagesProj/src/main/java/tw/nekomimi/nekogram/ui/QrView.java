package tw.nekomimi.nekogram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.text.TextUtils;
import android.view.View;

import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.TelegramQRCodeWriter;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RLottieDrawable;

import java.util.HashMap;

public class QrView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AnimatedFloat contentBitmapAlpha = new AnimatedFloat(1f, this, 0, 2000, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final Paint crossfadeFromPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{
        setShader(new LinearGradient(0, 0, 0, AndroidUtilities.dp(crossfadeWidthDp), new int[]{0xffffffff, 0}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }};
    private final Paint crossfadeToPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{
        setShader(new LinearGradient(0, 0, 0, AndroidUtilities.dp(crossfadeWidthDp), new int[]{0, 0xffffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }};
    private final int crossfadeWidthDp = 140;
    private RLottieDrawable loadingMatrix;
    private Bitmap contentBitmap, oldContentBitmap, qrLogo;
    private String link;
    private int contentGeneration;
    private final float[] radii = new float[8];

    public QrView(Context context) {
        super(context);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            if (qrLogo != null) {
                qrLogo.recycle();
                qrLogo = null;
            }
            prepareContent(w, h);
        }
    }

    private void drawLoading(Canvas canvas, int multiple, int size, float scale) {
        if (loadingMatrix == null) {
            loadingMatrix = new RLottieDrawable(R.raw.qr_matrix, "qr_matrix", AndroidUtilities.dp(200), AndroidUtilities.dp(200));
            loadingMatrix.setMasterParent(this);
            loadingMatrix.setAutoRepeat(1);
            loadingMatrix.setColorFilter(Color.BLACK, PorterDuff.Mode.MULTIPLY);
            loadingMatrix.start();
        }
        int width = getWidth();
        loadingMatrix.setBounds(16, 16, width - 16, width - 16);
        loadingMatrix.draw(canvas);
        int imageBloks = Math.round((size - 32) / 4.65f / multiple);
        if (imageBloks % 2 != 37 % 2) {
            imageBloks++;
        }
        int imageSize = imageBloks * multiple - 24;
        int imageX = (size - imageSize) / 2;
        canvas.save();
        canvas.scale(scale, scale);
        paint.setColor(Color.BLACK);
        TelegramQRCodeWriter.drawSideQuads(canvas, 0, 0, paint, 7, multiple, 16, size, .75f, radii, true);
        if (qrLogo == null) {
            String svg = AndroidUtilities.readRes(null, R.raw.qr_logo);
            qrLogo = SvgHelper.getBitmap(svg, imageSize, imageSize, false);
        }
        canvas.drawBitmap(qrLogo, imageX, imageX, null);
        canvas.restore();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);

        int width = getWidth();
        int qrSize = 37;
        int multiple = getWidth() / qrSize;
        int size = multiple * qrSize + 32;
        float scale = width / (float) size;

        float crossfadeAlpha = contentBitmapAlpha.set(1f);
        boolean crossfading = crossfadeAlpha > 0 && crossfadeAlpha < 1;

        if (crossfadeAlpha < 1f) {
            if (crossfading) {
                AndroidUtilities.rectTmp.set(0, 0, size, size);
                canvas.saveLayerAlpha(AndroidUtilities.rectTmp, 255, Canvas.ALL_SAVE_FLAG);
            }
            if (oldContentBitmap != null) {
                canvas.save();
                canvas.scale(scale, scale);
                canvas.drawBitmap(oldContentBitmap, 0, 0, null);
                canvas.restore();
            } else {
                drawLoading(canvas, multiple, size, scale);
            }
            if (crossfading) {
                float h = AndroidUtilities.dp(crossfadeWidthDp);
                canvas.save();
                canvas.translate(0, -h + (size + h) * (1f - crossfadeAlpha));
                canvas.drawRect(0, 0, size, size + h, crossfadeToPaint);
                canvas.restore();
                canvas.restore();
            }
        }
        if (crossfadeAlpha > 0f) {
            if (crossfading) {
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                canvas.saveLayerAlpha(AndroidUtilities.rectTmp, 255, Canvas.ALL_SAVE_FLAG);
            }
            if (contentBitmap != null) {
                canvas.save();
                canvas.scale(scale, scale);
                canvas.drawBitmap(contentBitmap, 0f, 0f, null);
                canvas.restore();
            } else {
                drawLoading(canvas, multiple, size, scale);
            }
            if (crossfading) {
                float h = AndroidUtilities.dp(crossfadeWidthDp);
                canvas.save();
                canvas.translate(0, -h + (getHeight() + h) * (1f - crossfadeAlpha));
                canvas.drawRect(0, -h - getHeight(), getWidth(), getHeight() + h, crossfadeFromPaint);
                canvas.restore();
                canvas.restore();
            }
        }
    }

    public void clear() {
        contentGeneration++;
        link = hadLink = null;
        hadWidth = hadHeight = null;
        firstPrepare = true;
        if (oldContentBitmap != null && oldContentBitmap != contentBitmap) oldContentBitmap.recycle();
        if (contentBitmap != null) contentBitmap.recycle();
        contentBitmap = oldContentBitmap = null;
        contentBitmapAlpha.set(1f, true);
        invalidate();
    }

    public void setData(String link) {
        if (TextUtils.isEmpty(link)) {
            // Never keep a scannable expired token underneath the loading animation.
            clear();
            return;
        }
        this.link = link;
        prepareContent(getWidth(), getHeight());
        invalidate();
    }

    private Integer hadWidth, hadHeight;
    private String hadLink;
    private boolean firstPrepare = true;

    private void prepareContent(int w, int h) {
        final int owner = ++contentGeneration;
        final String data = link;
        if (w <= 0 || h <= 0 || TextUtils.isEmpty(data)) return;
        if (TextUtils.equals(data, hadLink) && hadWidth != null && hadHeight != null && hadWidth == w && hadHeight == h) return;
        Utilities.themeQueue.postRunnable(() -> {
            Bitmap result = null;
            HashMap<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);
            try {
                result = new TelegramQRCodeWriter().encode(data, w, h, hints, null, 0.75f, 0, Color.BLACK);
            } catch (Exception error) {
                // Do not log the login token or encoder exception containing its contents.
                FileLog.e("Failed to render login QR");
            }
            final Bitmap bitmap = result;
            if (bitmap == null) return;
            AndroidUtilities.runOnUIThread(() -> {
                if (owner != contentGeneration || !TextUtils.equals(link, data)) {
                    bitmap.recycle();
                    return;
                }
                hadWidth = w;
                hadHeight = h;
                hadLink = data;
                Bitmap oldBitmap = contentBitmap;
                contentBitmap = bitmap;
                if (!firstPrepare) contentBitmapAlpha.set(0, true);
                firstPrepare = false;
                if (oldContentBitmap != null) oldContentBitmap.recycle();
                oldContentBitmap = oldBitmap;
                invalidate();
            });
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        prepareContent(getWidth(), getHeight());
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        String data = link;
        clear();
        link = data;
        if (loadingMatrix != null) {
            loadingMatrix.stop();
            loadingMatrix.recycle(false);
            loadingMatrix = null;
        }
        if (qrLogo != null) {
            qrLogo.recycle();
            qrLogo = null;
        }
    }
}
