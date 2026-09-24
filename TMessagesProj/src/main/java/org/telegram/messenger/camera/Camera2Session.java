package org.telegram.messenger.camera;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.params.MeteringRectangle;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.util.Size;
import android.util.SizeF;
import android.view.Surface;
import android.view.OrientationEventListener;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2Session {

    private static final long LOW_END_MAX_PICTURE_PIXELS = 8_000_000L;
    private static final long AVERAGE_MAX_PICTURE_PIXELS = 12_000_000L;
    private static final long HIGH_END_MAX_PICTURE_PIXELS = 16_000_000L;
    private static final long LOW_END_MAX_PREVIEW_PIXELS = 1_000_000L;
    private static final long AVERAGE_MAX_PREVIEW_PIXELS = 1_600_000L;
    private static final long HIGH_END_MAX_PREVIEW_PIXELS = 2_100_000L;

    private volatile boolean isError;
    private volatile boolean isSuccess;
    private volatile boolean isClosed;

    private final CameraManager cameraManager;
    private final boolean isFront;
    public final String cameraId;
    private CameraCharacteristics cameraCharacteristics;

    private HandlerThread thread;
    private Handler handler;
    private final Runnable updateZoomRequestRunnable = this::updateCaptureRequest;

    private CameraDevice cameraDevice;
    private SurfaceTexture surfaceTexture;
    private CameraCaptureSession captureSession;
    private Surface surface;

    private final CameraDevice.StateCallback cameraStateCallback;
    private final CameraCaptureSession.StateCallback captureStateCallback;
    private final CameraCaptureSession.CaptureCallback repeatingCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            if (!logicalMultiCamera || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return;
            }
            String physicalCameraId = result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
            if (activePhysicalCameraLogged && (physicalCameraId == null
                    ? activePhysicalCameraId == null
                    : physicalCameraId.equals(activePhysicalCameraId))) {
                return;
            }
            activePhysicalCameraLogged = true;
            activePhysicalCameraId = physicalCameraId;
            float appliedZoom = currentZoom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Float resultZoom = result.get(CaptureResult.CONTROL_ZOOM_RATIO);
                if (resultZoom != null) {
                    appliedZoom = resultZoom;
                }
            }
            FileLog.d("[CameraLens] logical=" + cameraId
                    + " activePhysical=" + (physicalCameraId == null ? "unspecified" : physicalCameraId)
                    + " zoom=" + String.format(Locale.US, "%.2fx", appliedZoom)
                    + " mode=" + (recordingVideo ? "video" : "preview"));
        }
    };
    private CaptureRequest.Builder captureRequestBuilder;
    private Rect sensorSize;
    private float minZoom = 1f;
    private float maxZoom = 1f;
    private float currentZoom = 1f;
    private boolean nativeZoomRatioSupported;
    private boolean logicalMultiCamera;
    private final ArrayList<Float> availableLensZoomRatios = new ArrayList<>();
    private Range<Integer> videoFpsRange;
    private int videoFrameRate = 30;
    private boolean videoStabilizationSupported;
    private boolean opticalStabilizationSupported;
    private boolean activePhysicalCameraLogged;
    private String activePhysicalCameraId;
    private boolean flashAvailable;
    private final ArrayList<String> availableFlashModes = new ArrayList<>();
    private String currentFlashMode = Camera.Parameters.FLASH_MODE_OFF;
    private boolean torchEnabled;
    private boolean flipFront = true;
    private Rect focusRegion;
    private Rect meteringRegion;
    private boolean manualFocus;
    private int sensorOrientation;
    private int deviceOrientation;
    private OrientationEventListener orientationEventListener;
    private boolean hasDeviceOrientation;
    private Runnable errorCallback;
    private boolean errorDispatched;
    private boolean errorCallbackDelivered;

    private final Size previewSize;
    private final Size pictureSize;

    private ImageReader imageReader;

    private long lastTime;

    public static Camera2Session create(boolean front, int viewWidth, int viewHeight) {
        final Context context = ApplicationLoader.applicationContext;
        final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        final int requestedFacing = front
                ? CameraCharacteristics.LENS_FACING_FRONT
                : CameraCharacteristics.LENS_FACING_BACK;
        final Camera2Topology.Snapshot topology = Camera2Topology.get(context);
        final String profileMainCameraId = topology.getMainCameraId(requestedFacing);

        double bestScore = Double.NEGATIVE_INFINITY;
        Size bestSize = null;
        Size bestPictureSize = null;
        String cameraId = null;
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            for (int i = 0; i < cameraIds.length; ++i) {
                final String id = cameraIds[i];
                if (!topology.isCameraUsable(id)) {
                    continue;
                }
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                if (characteristics == null) continue;
                Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (lensFacing == null || lensFacing != (front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK)) {
                    continue;
                }
                StreamConfigurationMap confMap = (StreamConfigurationMap) characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size pixelSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                float cameraAspectRatio = pixelSize == null ? 0 : (float) pixelSize.getWidth() / pixelSize.getHeight();
                if (cameraAspectRatio <= 0f) {
                    continue;
                }
                if ((viewWidth / (float) viewHeight >= 1f) != (cameraAspectRatio >= 1f)) {
                    cameraAspectRatio = 1f / cameraAspectRatio;
                }
                boolean isLogicalMultiCamera = isLogicalMultiCamera(characteristics);
                double score = (id.equals(profileMainCameraId) ? 10_000d : 0d)
                        + (isLogicalMultiCamera ? 1_000d : 0d)
                        - Math.abs((float) viewWidth / viewHeight - cameraAspectRatio) * 100d;
                if (score > bestScore) {
                    if (confMap != null) {
                        Size size = choosePreviewSize(confMap.getOutputSizes(SurfaceTexture.class), viewWidth, viewHeight, getMaxPreviewPixels());
                        if (size != null) {
                            bestScore = score;
                            cameraId = id;
                            bestSize = size;
                            bestPictureSize = choosePictureSize(confMap.getOutputSizes(ImageFormat.JPEG), size, getMaxPicturePixels());
                        }
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        if (cameraId == null || bestSize == null) {
            return null;
        }
        return new Camera2Session(context, front, cameraId, bestSize, bestPictureSize == null ? bestSize : bestPictureSize);
    }

    private static boolean isLogicalMultiCamera(CameraCharacteristics characteristics) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || characteristics == null) {
            return false;
        }
        int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        if (capabilities == null) {
            return false;
        }
        for (int capability : capabilities) {
            if (capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) {
                return true;
            }
        }
        return false;
    }

    private Camera2Session(Context context, boolean isFront, String cameraId, Size size, Size pictureSize) {
        thread = new HandlerThread("tg_camera2");
        thread.start();
        handler = new Handler(thread.getLooper());

        cameraStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                if (isClosed) {
                    camera.close();
                    return;
                }
                Camera2Session.this.cameraDevice = camera;
                Camera2Session.this.lastTime = System.currentTimeMillis();
                FileLog.d("Camera2Session camera #" + cameraId + " opened");
                checkOpen();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                camera.close();
                Camera2Session.this.cameraDevice = null;
                FileLog.d("Camera2Session camera #" + cameraId + " disconnected");
                dispatchError("camera disconnected", null);
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                camera.close();
                Camera2Session.this.cameraDevice = null;
                FileLog.e("Camera2Session camera #" + cameraId + " received " + error + " error");
                dispatchError("camera error " + error, null);
            }
        };

        captureStateCallback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                if (isClosed) {
                    session.close();
                    return;
                }
                captureSession = session;
                FileLog.d("Camera2Session camera #" + cameraId + " capture session configured");
                Camera2Session.this.lastTime = System.currentTimeMillis();
                try {
                    updateCaptureRequest();
                    Camera2Topology.markCameraValidated(context, cameraId);
                    AndroidUtilities.runOnUIThread(() -> {
                        isSuccess = true;
                        if (doneCallback != null) {
                            doneCallback.run();
                            doneCallback = null;
                        }
                    });
                } catch (Exception e) {
                    dispatchError("initial request failed", e);
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                captureSession = session;
                FileLog.e("Camera2Session camera #" + cameraId + " capture session failed to configure");
                dispatchError("capture session configuration failed", null);
            }
        };

        this.isFront = isFront;
        this.cameraId = cameraId;
        this.previewSize = size;
        this.pictureSize = pictureSize;
        this.lastTime = System.currentTimeMillis();
        this.imageReader = ImageReader.newInstance(pictureSize.getWidth(), pictureSize.getHeight(), ImageFormat.JPEG, 2);
        FileLog.d("[CameraQuality] Camera2 cameraId=" + cameraId
                + " preview=" + size.getWidth() + "x" + size.getHeight()
                + " jpeg=" + pictureSize.getWidth() + "x" + pictureSize.getHeight()
                + " max=" + String.format(Locale.US, "%.0fMP", getMaxPicturePixels() / 1_000_000d));
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            sensorSize = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Integer orientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = orientation == null ? 0 : orientation;
            flashAvailable = Boolean.TRUE.equals(cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE));
            if (flashAvailable) {
                availableFlashModes.add(Camera.Parameters.FLASH_MODE_OFF);
                if (isAeModeSupported(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                        || isAeModeSupported(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE)) {
                    availableFlashModes.add(Camera.Parameters.FLASH_MODE_AUTO);
                }
                availableFlashModes.add(Camera.Parameters.FLASH_MODE_ON);
            }
            SharedPreferences preferences = context.getSharedPreferences("camera", Context.MODE_PRIVATE);
            String savedFlashMode = preferences.getString(isFront ? "flashMode_front" : "flashMode", Camera.Parameters.FLASH_MODE_OFF);
            currentFlashMode = availableFlashModes.contains(savedFlashMode) ? savedFlashMode : Camera.Parameters.FLASH_MODE_OFF;
            final Float value = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            maxZoom = (value == null || value < 1f) ? 1f : value;
            logicalMultiCamera = isLogicalMultiCamera(cameraCharacteristics);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Range<Float> zoomRatioRange = cameraCharacteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                if (zoomRatioRange != null && zoomRatioRange.getLower() > 0f && zoomRatioRange.getUpper() >= 1f) {
                    minZoom = Math.min(1f, zoomRatioRange.getLower());
                    maxZoom = Math.max(1f, zoomRatioRange.getUpper());
                    nativeZoomRatioSupported = true;
                }
            }
            configureVideoCapabilities();
            logLensConfiguration();
            deviceOrientation = getDisplayRotationDegrees();
            orientationEventListener = new OrientationEventListener(context) {
                @Override
                public void onOrientationChanged(int orientation) {
                    if (orientation != ORIENTATION_UNKNOWN) {
                        deviceOrientation = ((orientation + 45) / 90 * 90) % 360;
                        hasDeviceOrientation = true;
                    }
                }
            };
            if (orientationEventListener.canDetectOrientation()) {
                orientationEventListener.enable();
            }
            cameraManager.openCamera(cameraId, cameraStateCallback, handler);
        } catch (Exception e) {
            dispatchError("open failed", e);
        }
    }

    private void logLensConfiguration() {
        StringBuilder lenses = new StringBuilder();
        availableLensZoomRatios.clear();
        if (logicalMultiCamera && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Set<String> physicalCameraIds = cameraCharacteristics.getPhysicalCameraIds();
            ArrayList<LensInfo> lensInfos = new ArrayList<>(physicalCameraIds.size());
            float logicalOpticalScale = getOpticalScale(cameraCharacteristics);
            for (String physicalCameraId : physicalCameraIds) {
                try {
                    CameraCharacteristics physicalCharacteristics = cameraManager.getCameraCharacteristics(physicalCameraId);
                    float[] focalLengths = physicalCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    if (focalLengths != null && focalLengths.length > 0) {
                        float shortestFocalLength = focalLengths[0];
                        for (int i = 1; i < focalLengths.length; i++) {
                            if (focalLengths[i] < shortestFocalLength) {
                                shortestFocalLength = focalLengths[i];
                            }
                        }
                        float opticalScale = getOpticalScale(physicalCharacteristics);
                        float zoomRatio = logicalOpticalScale > 0f && opticalScale > 0f ? opticalScale / logicalOpticalScale : 1f;
                        lensInfos.add(new LensInfo(physicalCameraId, shortestFocalLength, zoomRatio));
                    }
                } catch (Exception e) {
                    FileLog.e("Camera2Session unable to inspect physical camera #" + physicalCameraId, e);
                }
            }
            Collections.sort(lensInfos, Comparator.comparingDouble(lens -> lens.zoomRatio));
            if (minZoom < 0.99f) {
                addLensZoomRatio(minZoom);
            }
            addLensZoomRatio(1f);
            for (int i = 0; i < lensInfos.size(); i++) {
                LensInfo lens = lensInfos.get(i);
                float zoomRatio = normalizeLensZoomRatio(lens.zoomRatio);
                addLensZoomRatio(zoomRatio);
                if (lenses.length() > 0) {
                    lenses.append(", ");
                }
                lenses.append(lens.cameraId)
                        .append('=')
                        .append(String.format(Locale.US, "%.2fmm", lens.focalLength))
                        .append('(')
                        .append(classifyLens(zoomRatio))
                        .append('@')
                        .append(String.format(Locale.US, "%.2fx", zoomRatio))
                        .append(')');
            }
        }
        if (availableLensZoomRatios.isEmpty()) {
            addLensZoomRatio(1f);
        }
        if (logicalMultiCamera) {
            int facing = isFront
                    ? CameraCharacteristics.LENS_FACING_FRONT
                    : CameraCharacteristics.LENS_FACING_BACK;
            List<Camera2Topology.LensPreset> profilePresets = Camera2Topology
                    .get(ApplicationLoader.applicationContext)
                    .getPresets(facing);
            ArrayList<Float> validatedRatios = new ArrayList<>();
            for (Camera2Topology.LensPreset preset : profilePresets) {
                if (cameraId.equals(preset.cameraId)
                        && preset.route == Camera2Topology.Route.LOGICAL_ZOOM
                        && preset.displayRatio >= minZoom
                        && preset.displayRatio <= maxZoom) {
                    boolean duplicate = false;
                    for (float ratio : validatedRatios) {
                        if (Math.abs(ratio - preset.displayRatio) < 0.1f) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) {
                        validatedRatios.add(preset.displayRatio);
                    }
                }
            }
            if (!validatedRatios.isEmpty()) {
                availableLensZoomRatios.clear();
                availableLensZoomRatios.addAll(validatedRatios);
            }
        }
        Collections.sort(availableLensZoomRatios);
        FileLog.d("[CameraLens] logical=" + cameraId
                + " multiCamera=" + logicalMultiCamera
                + " zoom=" + String.format(Locale.US, "%.2fx..%.2fx", minZoom, maxZoom)
                + " mode=" + (nativeZoomRatioSupported ? "CONTROL_ZOOM_RATIO" : "SCALER_CROP_REGION")
                + " physical=[" + lenses + "]"
                + " presets=" + availableLensZoomRatios);
    }

    private void configureVideoCapabilities() {
        Range<Integer>[] fpsRanges = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if (fpsRanges != null) {
            for (Range<Integer> range : fpsRanges) {
                if (!range.contains(30)) {
                    continue;
                }
                if (videoFpsRange == null
                        || range.getLower() > videoFpsRange.getLower()
                        || range.getLower().equals(videoFpsRange.getLower())
                        && range.getUpper() < videoFpsRange.getUpper()) {
                    videoFpsRange = range;
                }
            }
            if (videoFpsRange == null && fpsRanges.length > 0) {
                for (Range<Integer> range : fpsRanges) {
                    if (videoFpsRange == null || range.getUpper() > videoFpsRange.getUpper()) {
                        videoFpsRange = range;
                    }
                }
                videoFrameRate = Math.max(15, Math.min(30, videoFpsRange.getUpper()));
            }
        }
        videoStabilizationSupported = containsMode(
                cameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES),
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
        opticalStabilizationSupported = containsMode(
                cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION),
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
        FileLog.d("[CameraVideo] cameraId=" + cameraId
                + " size=" + previewSize.getWidth() + "x" + previewSize.getHeight()
                + " fps=" + videoFrameRate
                + " aeRange=" + videoFpsRange
                + " stabilization=" + (videoStabilizationSupported ? "video" : opticalStabilizationSupported ? "optical" : "none"));
    }

    private static float getOpticalScale(CameraCharacteristics characteristics) {
        float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        SizeF sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        if (focalLengths == null || focalLengths.length == 0 || sensorSize == null || sensorSize.getWidth() <= 0f) {
            return 0f;
        }
        float shortestFocalLength = focalLengths[0];
        for (int i = 1; i < focalLengths.length; i++) {
            shortestFocalLength = Math.min(shortestFocalLength, focalLengths[i]);
        }
        return shortestFocalLength / sensorSize.getWidth();
    }

    private float normalizeLensZoomRatio(float zoomRatio) {
        zoomRatio = Utilities.clamp(zoomRatio, maxZoom, minZoom);
        if (zoomRatio < 1f && Math.abs(zoomRatio - minZoom) < 0.2f) {
            return minZoom;
        }
        float nearestInteger = Math.round(zoomRatio);
        if (nearestInteger >= 1f && Math.abs(zoomRatio - nearestInteger) <= 0.15f) {
            return nearestInteger;
        }
        return Math.round(zoomRatio * 10f) / 10f;
    }

    private void addLensZoomRatio(float zoomRatio) {
        zoomRatio = Utilities.clamp(zoomRatio, maxZoom, minZoom);
        for (float existingRatio : availableLensZoomRatios) {
            if (Math.abs(existingRatio - zoomRatio) < 0.1f) {
                return;
            }
        }
        availableLensZoomRatios.add(zoomRatio);
    }

    private static String classifyLens(float zoomRatio) {
        if (zoomRatio < 0.9f) {
            return "ultrawide";
        }
        if (zoomRatio > 1.15f) {
            return "telephoto";
        }
        return "main";
    }

    private static class LensInfo {
        final String cameraId;
        final float focalLength;
        final float zoomRatio;

        LensInfo(String cameraId, float focalLength, float zoomRatio) {
            this.cameraId = cameraId;
            this.focalLength = focalLength;
            this.zoomRatio = zoomRatio;
        }
    }

    public void setErrorCallback(Runnable callback) {
        errorCallback = callback;
        if (isError && callback != null) {
            AndroidUtilities.runOnUIThread(this::deliverErrorCallback);
        }
    }

    private void dispatchError(String message, Throwable error) {
        if (isClosed) {
            return;
        }
        if (error != null) {
            FileLog.e("Camera2Session camera #" + cameraId + ' ' + message, error);
        } else {
            FileLog.e("Camera2Session camera #" + cameraId + ' ' + message);
        }
        isError = true;
        if (!isSuccess) {
            Camera2Topology.markCameraRejected(ApplicationLoader.applicationContext, cameraId);
        }
        if (errorDispatched) {
            return;
        }
        errorDispatched = true;
        AndroidUtilities.runOnUIThread(this::deliverErrorCallback);
    }

    private void deliverErrorCallback() {
        if (!errorCallbackDelivered && errorCallback != null) {
            errorCallbackDelivered = true;
            errorCallback.run();
        }
    }

    private Runnable doneCallback;
    public void whenDone(Runnable doneCallback) {
        if (isInitiated()) {
            doneCallback.run();
            this.doneCallback = null;
        } else {
            this.doneCallback = doneCallback;
        }
    }

    public void open(SurfaceTexture surfaceTexture) {
        handler.post(() -> {
            this.surfaceTexture = surfaceTexture;
            if (surfaceTexture != null) {
                surfaceTexture.setDefaultBufferSize(getPreviewWidth(), getPreviewHeight());
            }
            checkOpen();
        });
    }

    private boolean opened = false;
    private void checkOpen() {
        if (opened) return;
        if (surfaceTexture == null || cameraDevice == null) return;
        opened = true;

        surface = new Surface(surfaceTexture);

        try {
            ArrayList<Surface> surfaces = new ArrayList<>();
            surfaces.add(surface);
            surfaces.add(imageReader.getSurface());
            cameraDevice.createCaptureSession(surfaces, captureStateCallback, null);
        } catch (Exception e) {
            dispatchError("capture session creation failed", e);
        }
    }

    public boolean isInitiated() {
        return !isError && isSuccess && !isClosed;
    }

    public boolean isFrontCamera() {
        return isFront;
    }

    public int getDisplayOrientation() {
        int degrees = getDisplayRotationDegrees();
        if (isFront) {
            return (360 - (sensorOrientation + degrees) % 360) % 360;
        }
        return (sensorOrientation - degrees + 360) % 360;
    }

    private int getJpegOrientation() {
        if (isFront) {
            return (sensorOrientation - deviceOrientation + 360) % 360;
        }
        return (sensorOrientation + deviceOrientation) % 360;
    }

    private int getDisplayRotationDegrees() {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return 0;
            }
            int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            switch (rotation) {
                case Surface.ROTATION_90:
                    return 90;
                case Surface.ROTATION_180:
                    return 180;
                case Surface.ROTATION_270:
                    return 270;
                case Surface.ROTATION_0:
                default:
                    return 0;
            }
        } catch (Exception e) {
            FileLog.e(e);
            return 0;
        }
    }

    public int getWorldAngle() {
        return 0;
    }

    public int getCurrentOrientation() {
        return getDisplayOrientation();
    }

    public boolean isSameTakePictureOrientation() {
        int displayOrientation = getDisplayOrientation();
        int jpegOrientation = getJpegOrientation();
        return isFront ? (360 - displayOrientation) % 360 == jpegOrientation : displayOrientation == jpegOrientation;
    }

    public void updateRotation() {
        if (!hasDeviceOrientation) {
            deviceOrientation = getDisplayRotationDegrees();
        }
    }

    public void setFlipFront(boolean flip) {
        flipFront = flip;
    }

    private final Rect cropRegion = new Rect();
    public void setZoom(float value) {
        float zoom = Utilities.clamp(value, maxZoom, minZoom);
        if (Math.abs(currentZoom - zoom) < 0.001f) {
            return;
        }
        currentZoom = zoom;
        handler.removeCallbacks(updateZoomRequestRunnable);
        handler.post(updateZoomRequestRunnable);
    }

    public void setFlash(boolean flash) {
        if (torchEnabled != flash) {
            torchEnabled = flash;
            handler.post(this::updateCaptureRequest);
        }
    }
    public boolean getFlash() {
        return torchEnabled;
    }

    public void setCurrentFlashMode(String flashMode) {
        if (!Camera.Parameters.FLASH_MODE_OFF.equals(flashMode) && !availableFlashModes.contains(flashMode)) {
            flashMode = Camera.Parameters.FLASH_MODE_OFF;
        }
        if (!flashMode.equals(currentFlashMode)) {
            currentFlashMode = flashMode;
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("camera", Context.MODE_PRIVATE);
            preferences.edit().putString(isFront ? "flashMode_front" : "flashMode", flashMode).apply();
            handler.post(this::updateCaptureRequest);
        }
    }

    public String getCurrentFlashMode() {
        return currentFlashMode;
    }

    public String getNextFlashMode() {
        if (availableFlashModes.isEmpty()) {
            return Camera.Parameters.FLASH_MODE_OFF;
        }
        int index = availableFlashModes.indexOf(currentFlashMode);
        if (index < 0) {
            return availableFlashModes.get(0);
        }
        return availableFlashModes.get((index + 1) % availableFlashModes.size());
    }

    public boolean hasFlashModes() {
        return availableFlashModes.size() > 1;
    }

    public float getZoom() {
        return currentZoom;
    }

    public float getMaxZoom() {
        return maxZoom;
    }

    public float getMinZoom() {
        // Existing controls use zero as 1x. Step 6 will expose sub-1x lens presets.
        return 1f;
    }

    public float getHardwareMinZoom() {
        return minZoom;
    }

    public List<Float> getAvailableLensZoomRatios() {
        return new ArrayList<>(availableLensZoomRatios);
    }

    public void focusToRect(Rect focusRect, Rect meteringRect) {
        if (focusRect == null || sensorSize == null) {
            return;
        }
        focusRegion = mapLegacyAreaToSensor(focusRect);
        meteringRegion = mapLegacyAreaToSensor(meteringRect == null ? focusRect : meteringRect);
        manualFocus = true;
        handler.post(() -> {
            if (!isInitiated() || captureRequestBuilder == null || captureSession == null) {
                return;
            }
            try {
                applyFocus(captureRequestBuilder);
                if (isFocusModeSupported(CaptureRequest.CONTROL_AF_MODE_AUTO)) {
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
                    captureSession.capture(captureRequestBuilder.build(), null, handler);
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                    captureSession.capture(captureRequestBuilder.build(), null, handler);
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                }
                captureSession.setRepeatingRequest(captureRequestBuilder.build(), repeatingCaptureCallback, handler);
            } catch (Exception e) {
                FileLog.e("Camera2Session tap-to-focus failed", e);
            }
        });
    }

    private Rect mapLegacyAreaToSensor(Rect legacyArea) {
        Rect activeRegion = getCurrentCropRegion();
        int left = activeRegion.left + Math.round((legacyArea.left + 1000) / 2000f * activeRegion.width());
        int top = activeRegion.top + Math.round((legacyArea.top + 1000) / 2000f * activeRegion.height());
        int right = activeRegion.left + Math.round((legacyArea.right + 1000) / 2000f * activeRegion.width());
        int bottom = activeRegion.top + Math.round((legacyArea.bottom + 1000) / 2000f * activeRegion.height());
        left = Utilities.clamp(left, activeRegion.right - 1, activeRegion.left);
        top = Utilities.clamp(top, activeRegion.bottom - 1, activeRegion.top);
        right = Utilities.clamp(right, activeRegion.right, left + 1);
        bottom = Utilities.clamp(bottom, activeRegion.bottom, top + 1);
        return new Rect(left, top, right, bottom);
    }

    public int getPreviewWidth() {
        return previewSize.getWidth();
    }

    public int getPreviewHeight() {
        return previewSize.getHeight();
    }

    public void destroy(boolean async) {
        destroy(async, null);
    }

    public void destroy(boolean async, Runnable afterCallback) {
        if (isClosed) {
            if (afterCallback != null) {
                AndroidUtilities.runOnUIThread(afterCallback);
            }
            return;
        }
        isClosed = true;
        if (orientationEventListener != null) {
            orientationEventListener.disable();
            orientationEventListener = null;
        }
        if (async) {
            handler.post(() -> {
                if (captureSession != null) {
                    captureSession.close();
                    captureSession = null;
                }
                if (cameraDevice != null) {
                    cameraDevice.close();
                    cameraDevice = null;
                }
                if (imageReader != null) {
                    imageReader.close();
                    imageReader = null;
                }
                if (surface != null) {
                    surface.release();
                    surface = null;
                }
                thread.quitSafely();
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        thread.join();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (afterCallback != null) {
                        afterCallback.run();
                    }
                });
            });
        } else {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
            if (surface != null) {
                surface.release();
                surface = null;
            }
            thread.quitSafely();
            try {
                thread.join();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (afterCallback != null) {
                AndroidUtilities.runOnUIThread(afterCallback);
            }
        }
    }

    private boolean recordingVideo;
    public void setRecordingVideo(boolean recording) {
        if (recordingVideo != recording) {
            recordingVideo = recording;
            FileLog.d("[CameraVideo] cameraId=" + cameraId
                    + " recording=" + recording
                    + " zoom=" + String.format(Locale.US, "%.2fx", currentZoom));
            handler.post(this::updateCaptureRequest);
        }
    }

    public int getVideoFrameRate() {
        return videoFrameRate;
    }

    private boolean scanningBarcode;
    public void setScanningBarcode(boolean scanning) {
        if (scanningBarcode != scanning) {
            scanningBarcode = scanning;
            handler.post(this::updateCaptureRequest);
        }
    }

    private boolean nightMode;
    public void setNightMode(boolean enable) {
        if (nightMode != enable) {
            nightMode = enable;
            handler.post(this::updateCaptureRequest);
        }
    }

    private void updateCaptureRequest() {
        if (cameraDevice == null || surface == null || captureSession == null) return;
        try {
            int template;
            if (recordingVideo) {
                template = CameraDevice.TEMPLATE_RECORD;
            } else if (scanningBarcode) {
                template = CameraDevice.TEMPLATE_STILL_CAPTURE;
            } else {
                template = CameraDevice.TEMPLATE_PREVIEW;
            }
            captureRequestBuilder = cameraDevice.createCaptureRequest(template);
            applyCommonRequestSettings(captureRequestBuilder, false);

            captureRequestBuilder.addTarget(surface);
            captureSession.setRepeatingRequest(captureRequestBuilder.build(), repeatingCaptureCallback, handler);
        } catch (Exception e) {
            if (recordingVideo) {
                FileLog.e("[CameraVideo] request update failed, keeping previous capture request", e);
            } else {
                dispatchError("repeating request failed", e);
            }
        }
    }

    private void applyCommonRequestSettings(CaptureRequest.Builder builder, boolean stillCapture) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        if (scanningBarcode && isSceneModeSupported(CameraMetadata.CONTROL_SCENE_MODE_BARCODE)) {
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_BARCODE);
        } else if (nightMode && isSceneModeSupported(isFront ? CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT : CameraMetadata.CONTROL_SCENE_MODE_NIGHT)) {
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, isFront ? CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT : CameraMetadata.CONTROL_SCENE_MODE_NIGHT);
        }

        applyFocus(builder);
        applyFlash(builder, stillCapture);
        applyZoom(builder);

        if (recordingVideo) {
            if (videoFpsRange != null) {
                builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, videoFpsRange);
            }
            if (videoStabilizationSupported) {
                builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
                if (opticalStabilizationSupported) {
                    builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
                }
            } else if (opticalStabilizationSupported) {
                builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
            }
            builder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD);
        } else if (stillCapture) {
            builder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
        }
    }

    private void applyFocus(CaptureRequest.Builder builder) {
        int focusMode = findSupportedFocusMode(recordingVideo
                ? CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        if (manualFocus && focusRegion != null) {
            focusMode = findSupportedFocusMode(CaptureRequest.CONTROL_AF_MODE_AUTO);
            Integer maxAfRegions = cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
            if (maxAfRegions != null && maxAfRegions > 0) {
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{new MeteringRectangle(focusRegion, MeteringRectangle.METERING_WEIGHT_MAX)});
            }
            Integer maxAeRegions = cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
            if (maxAeRegions != null && maxAeRegions > 0 && meteringRegion != null) {
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{new MeteringRectangle(meteringRegion, MeteringRectangle.METERING_WEIGHT_MAX)});
            }
        }
        builder.set(CaptureRequest.CONTROL_AF_MODE, focusMode);
    }

    private int findSupportedFocusMode(int preferredMode) {
        int[] modes = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        if (modes == null || modes.length == 0) {
            return CaptureRequest.CONTROL_AF_MODE_OFF;
        }
        for (int mode : modes) {
            if (mode == preferredMode) {
                return mode;
            }
        }
        for (int mode : modes) {
            if (mode == CaptureRequest.CONTROL_AF_MODE_AUTO) {
                return mode;
            }
        }
        return modes[0];
    }

    private boolean isFocusModeSupported(int expectedMode) {
        return containsMode(cameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES), expectedMode);
    }

    private boolean isSceneModeSupported(int expectedMode) {
        return containsMode(cameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES), expectedMode);
    }

    private boolean isAeModeSupported(int expectedMode) {
        return containsMode(cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES), expectedMode);
    }

    private boolean containsMode(int[] modes, int expectedMode) {
        if (modes == null) {
            return false;
        }
        for (int mode : modes) {
            if (mode == expectedMode) {
                return true;
            }
        }
        return false;
    }

    private void applyFlash(CaptureRequest.Builder builder, boolean stillCapture) {
        builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        if (!flashAvailable) {
            return;
        }
        if (torchEnabled || recordingVideo && Camera.Parameters.FLASH_MODE_ON.equals(currentFlashMode)) {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
        } else if (!recordingVideo && Camera.Parameters.FLASH_MODE_AUTO.equals(currentFlashMode)
                && (isAeModeSupported(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                || isAeModeSupported(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE))) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, isAeModeSupported(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    ? CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                    : CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
        } else if (!recordingVideo && Camera.Parameters.FLASH_MODE_ON.equals(currentFlashMode)) {
            if (stillCapture && isAeModeSupported(CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
            } else if (stillCapture) {
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
            }
        }
    }

    private void applyZoom(CaptureRequest.Builder builder) {
        if (nativeZoomRatioSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, currentZoom);
                return;
            } catch (IllegalArgumentException e) {
                nativeZoomRatioSupported = false;
                minZoom = 1f;
                currentZoom = Math.max(1f, currentZoom);
                FileLog.e("Camera2Session native zoom ratio rejected, using crop region", e);
            }
        }
        if (sensorSize == null || currentZoom <= 1.001f) {
            return;
        }
        builder.set(CaptureRequest.SCALER_CROP_REGION, getCurrentCropRegion());
    }

    private Rect getCurrentCropRegion() {
        if (sensorSize == null || currentZoom <= 1.001f) {
            return sensorSize == null ? new Rect() : new Rect(sensorSize);
        }
        int centerX = sensorSize.centerX();
        int centerY = sensorSize.centerY();
        int halfWidth = Math.max(1, Math.round(sensorSize.width() / (2f * currentZoom)));
        int halfHeight = Math.max(1, Math.round(sensorSize.height() / (2f * currentZoom)));
        cropRegion.set(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight);
        return new Rect(cropRegion);
    }

    public boolean takePicture(final File file, Utilities.Callback<Integer> whenDone) {
        if (cameraDevice == null || captureSession == null || imageReader == null) return false;
        try {
            CaptureRequest.Builder stillBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            final int orientation = getJpegOrientation();
            final boolean shouldMirror = isFront && flipFront;
            stillBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientation);
            stillBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
            applyCommonRequestSettings(stillBuilder, true);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireLatestImage();
                if (image == null) {
                    return;
                }
                int reportedOrientation = orientation;
                boolean saved = false;
                try {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);

                    if (shouldMirror) {
                        Bitmap bitmap = null;
                        Bitmap mirrored = null;
                        try {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inPurgeable = true;
                            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                            if (bitmap != null) {
                                Matrix matrix = new Matrix();
                                if (orientation != 0) {
                                    matrix.setRotate(orientation);
                                }
                                matrix.postScale(-1f, 1f);
                                mirrored = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                                try (FileOutputStream output = new FileOutputStream(file)) {
                                    saved = mirrored.compress(Bitmap.CompressFormat.JPEG, 95, output);
                                    output.flush();
                                    output.getFD().sync();
                                }
                                if (saved) {
                                    reportedOrientation = 0;
                                }
                            }
                        } catch (Throwable e) {
                            FileLog.e("Camera2Session front mirror failed", e);
                        } finally {
                            if (mirrored != null && mirrored != bitmap) {
                                mirrored.recycle();
                            }
                            if (bitmap != null && !bitmap.isRecycled()) {
                                bitmap.recycle();
                            }
                        }
                    }

                    if (!saved) {
                        try (FileOutputStream output = new FileOutputStream(file)) {
                            output.write(bytes);
                            output.flush();
                            output.getFD().sync();
                            saved = true;
                        }
                    }

                    final int callbackOrientation = reportedOrientation;
                    if (saved) {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (whenDone != null) {
                                whenDone.run(callbackOrientation);
                            }
                        });
                    }
                } catch (Throwable e) {
                    FileLog.e("Camera2Session image save failed", e);
                } finally {
                    image.close();
                }
            }, handler);
            stillBuilder.addTarget(imageReader.getSurface());
            captureSession.capture(stillBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                    FileLog.e("Camera2Session still capture failed: " + failure.getReason());
                }
            }, handler);
            return true;
        } catch (Exception e) {
            FileLog.e("Camera2Sessions takePicture error", e);
            return false;
        }
    }

    private static long getMaxPicturePixels() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return LOW_END_MAX_PICTURE_PIXELS;
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return AVERAGE_MAX_PICTURE_PIXELS;
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
            default:
                return HIGH_END_MAX_PICTURE_PIXELS;
        }
    }

    private static long getMaxPreviewPixels() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return LOW_END_MAX_PREVIEW_PIXELS;
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return AVERAGE_MAX_PREVIEW_PIXELS;
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
            default:
                return HIGH_END_MAX_PREVIEW_PIXELS;
        }
    }

    private static Size choosePreviewSize(Size[] choices, int viewWidth, int viewHeight, long maxPixels) {
        if (choices == null || choices.length == 0) {
            return null;
        }
        float targetRatio = Math.max(viewWidth, viewHeight) / (float) Math.max(1, Math.min(viewWidth, viewHeight));
        Size bestSize = null;
        float bestRatioDifference = Float.MAX_VALUE;
        for (Size choice : choices) {
            long pixels = (long) choice.getWidth() * choice.getHeight();
            if (pixels > maxPixels || Math.max(choice.getWidth(), choice.getHeight()) > 1920) {
                continue;
            }
            float ratio = Math.max(choice.getWidth(), choice.getHeight())
                    / (float) Math.max(1, Math.min(choice.getWidth(), choice.getHeight()));
            float ratioDifference = Math.abs(ratio - targetRatio);
            if (bestSize == null
                    || ratioDifference < bestRatioDifference - 0.001f
                    || Math.abs(ratioDifference - bestRatioDifference) <= 0.001f
                    && pixels > (long) bestSize.getWidth() * bestSize.getHeight()) {
                bestSize = choice;
                bestRatioDifference = ratioDifference;
            }
        }
        if (bestSize != null) {
            return bestSize;
        }
        return Collections.min(Arrays.asList(choices), Comparator.comparingLong(size -> (long) size.getWidth() * size.getHeight()));
    }

    private static Size choosePictureSize(Size[] choices, Size previewSize, long maxPixels) {
        if (choices == null || choices.length == 0) {
            return null;
        }
        float targetRatio = previewSize.getWidth() / (float) previewSize.getHeight();
        Size bestMatching = null;
        Size bestFallback = null;
        float bestFallbackRatioDifference = Float.MAX_VALUE;
        for (Size choice : choices) {
            long pixels = (long) choice.getWidth() * choice.getHeight();
            if (pixels > maxPixels) {
                continue;
            }
            float ratio = choice.getWidth() / (float) choice.getHeight();
            float ratioDifference = Math.abs(ratio - targetRatio);
            if (ratioDifference <= 0.03f
                    && (bestMatching == null || pixels > (long) bestMatching.getWidth() * bestMatching.getHeight())) {
                bestMatching = choice;
            }
            if (bestFallback == null
                    || ratioDifference < bestFallbackRatioDifference - 0.001f
                    || Math.abs(ratioDifference - bestFallbackRatioDifference) <= 0.001f
                    && pixels > (long) bestFallback.getWidth() * bestFallback.getHeight()) {
                bestFallback = choice;
                bestFallbackRatioDifference = ratioDifference;
            }
        }
        if (bestMatching != null) {
            return bestMatching;
        }
        if (bestFallback != null) {
            return bestFallback;
        }
        return Collections.min(Arrays.asList(choices), Comparator.comparingLong(size -> (long) size.getWidth() * size.getHeight()));
    }


    public static Size chooseOptimalSize(Size[] choices, int width, int height, boolean notBigger) {
        List<Size> bigEnoughWithAspectRatio = new ArrayList<>(choices.length);
        List<Size> bigEnough = new ArrayList<>(choices.length);
        int w = width;
        int h = height;
        for (int a = 0; a < choices.length; a++) {
            Size option = choices[a];
            if (notBigger && (option.getHeight() > height || option.getWidth() > width)) {
                continue;
            }
            if (option.getHeight() == option.getWidth() * h / w && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnoughWithAspectRatio.add(option);
            } else if (option.getHeight() * option.getWidth() <= width * height * 4 && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnoughWithAspectRatio.size() > 0) {
            return Collections.min(bigEnoughWithAspectRatio, new CompareSizesByArea());
        } else if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return Collections.max(Arrays.asList(choices), new CompareSizesByArea());
        }
    }
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

}
