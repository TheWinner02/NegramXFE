package org.telegram.messenger.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Range;
import android.util.SizeF;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class CameraDiagnostics {

    private static final String PREFIX = "[CameraDiagnostics] ";
    private static final int LOG_CHUNK_SIZE = 2800;
    private static final Set<Integer> LOGGED_CAMERA1_PARAMETERS = new HashSet<>();

    private CameraDiagnostics() {
    }

    static void logCamera1Count(int count, boolean fromCache) {
        if (!BuildVars.LOGS_ENABLED) {
            return;
        }
        log("Camera1 cameraCount=" + count + " source=" + (fromCache ? "cache" : "hardware"));
    }

    static void logCamera1(int cameraId, Camera.CameraInfo cameraInfo, Camera.Parameters parameters) {
        if (!BuildVars.LOGS_ENABLED || cameraInfo == null || parameters == null) {
            return;
        }
        synchronized (LOGGED_CAMERA1_PARAMETERS) {
            if (!LOGGED_CAMERA1_PARAMETERS.add(cameraId)) {
                return;
            }
        }
        try {
            float maxZoom = 1f;
            if (parameters.isZoomSupported()) {
                List<Integer> ratios = parameters.getZoomRatios();
                int maxZoomIndex = parameters.getMaxZoom();
                if (ratios != null && maxZoomIndex >= 0 && maxZoomIndex < ratios.size()) {
                    maxZoom = ratios.get(maxZoomIndex) / 100f;
                }
            }

            log("Camera1 id=" + cameraId
                    + " facing=" + camera1FacingToString(cameraInfo.facing)
                    + " orientation=" + cameraInfo.orientation + "deg"
                    + " focal=" + formatFloat(parameters.getFocalLength()) + "mm"
                    + " zoom=1.00x.." + formatFloat(maxZoom) + "x"
                    + " physicalIds=not_exposed");
            logLongValue("Camera1 id=" + cameraId + " previewSizes=", formatCamera1Sizes(parameters.getSupportedPreviewSizes()));
            logLongValue("Camera1 id=" + cameraId + " jpegSizes=", formatCamera1Sizes(parameters.getSupportedPictureSizes()));
            log("Camera1 id=" + cameraId
                    + " focusModes=" + String.valueOf(parameters.getSupportedFocusModes())
                    + " flashModes=" + String.valueOf(parameters.getSupportedFlashModes()));
        } catch (Exception e) {
            FileLog.e(PREFIX + "Camera1 id=" + cameraId + " diagnostics failed", e);
        }
    }

    static void logCachedCamera1(CameraInfo cachedInfo) {
        if (!BuildVars.LOGS_ENABLED || cachedInfo == null) {
            return;
        }
        try {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(cachedInfo.cameraId, cameraInfo);
            log("Camera1 id=" + cachedInfo.cameraId
                    + " facing=" + camera1FacingToString(cameraInfo.facing)
                    + " orientation=" + cameraInfo.orientation + "deg"
                    + " details=deferred_until_open");
            logLongValue("Camera1 id=" + cachedInfo.cameraId + " cachedPreviewSizes=", String.valueOf(cachedInfo.previewSizes));
            logLongValue("Camera1 id=" + cachedInfo.cameraId + " cachedJpegSizes=", String.valueOf(cachedInfo.pictureSizes));
        } catch (Exception e) {
            FileLog.e(PREFIX + "Camera1 id=" + cachedInfo.cameraId + " cached diagnostics failed", e);
        }
    }

    static void logOpenedCamera1(int cameraId, Camera.Parameters parameters) {
        if (!BuildVars.LOGS_ENABLED || parameters == null) {
            return;
        }
        try {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, cameraInfo);
            logCamera1(cameraId, cameraInfo, parameters);
        } catch (Exception e) {
            FileLog.e(PREFIX + "Camera1 id=" + cameraId + " opened diagnostics failed", e);
        }
    }

    static void logCamera2Inventory() {
        if (!BuildVars.LOGS_ENABLED || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) {
                log("Camera2 manager unavailable");
                return;
            }
            String[] cameraIds = manager.getCameraIdList();
            log("Camera2 cameraCount=" + cameraIds.length + " ids=" + Arrays.toString(cameraIds));
            for (String cameraId : cameraIds) {
                logCamera2(manager, cameraId);
            }
            Camera2Topology.logSnapshot(Camera2Topology.get(context));
        } catch (Exception e) {
            FileLog.e(PREFIX + "Camera2 inventory failed", e);
        }
    }

    private static void logCamera2(CameraManager manager, String cameraId) {
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            Integer hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            Boolean flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            SizeF physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            android.util.Size pixelSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
            Rect activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Float digitalZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            Range<Float> zoomRange = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                zoomRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            }

            Set<String> physicalIds = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                physicalIds = characteristics.getPhysicalCameraIds();
            }

            String zoom = zoomRange != null
                    ? formatFloat(zoomRange.getLower()) + "x.." + formatFloat(zoomRange.getUpper()) + "x"
                    : "1.00x.." + formatFloat(digitalZoom == null ? 1f : digitalZoom) + "x";
            log("Camera2 id=" + cameraId
                    + " facing=" + camera2FacingToString(facing)
                    + " orientation=" + (orientation == null ? "unknown" : orientation + "deg")
                    + " hardware=" + hardwareLevelToString(hardwareLevel)
                    + " logicalMultiCamera=" + hasCapability(capabilities, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
                    + " focal=" + Arrays.toString(focalLengths == null ? new float[0] : focalLengths) + "mm"
                    + " zoom=" + zoom
                    + " flash=" + Boolean.TRUE.equals(flashAvailable)
                    + " physicalIds=" + (physicalIds == null ? "not_available" : physicalIds));
            log("Camera2 id=" + cameraId
                    + " sensorPhysical=" + String.valueOf(physicalSize)
                    + " sensorPixels=" + String.valueOf(pixelSize)
                    + " activeArray=" + String.valueOf(activeArray));

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                logLongValue("Camera2 id=" + cameraId + " previewSizes=", formatCamera2Sizes(map.getOutputSizes(SurfaceTexture.class)));
                logLongValue("Camera2 id=" + cameraId + " jpegSizes=", formatCamera2Sizes(map.getOutputSizes(ImageFormat.JPEG)));
                logLongValue("Camera2 id=" + cameraId + " videoSizes=", formatCamera2Sizes(map.getOutputSizes(MediaRecorder.class)));
            } else {
                log("Camera2 id=" + cameraId + " streamConfigurationMap=unavailable");
            }
        } catch (Exception e) {
            FileLog.e(PREFIX + "Camera2 id=" + cameraId + " diagnostics failed", e);
        }
    }

    private static String formatCamera1Sizes(List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < sizes.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            Camera.Size size = sizes.get(i);
            appendSize(builder, size.width, size.height);
        }
        return builder.append(']').toString();
    }

    private static String formatCamera2Sizes(android.util.Size[] sizes) {
        if (sizes == null || sizes.length == 0) {
            return "[]";
        }
        android.util.Size[] sorted = sizes.clone();
        Arrays.sort(sorted, Comparator.comparingLong(CameraDiagnostics::area).reversed());
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < sorted.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            appendSize(builder, sorted[i].getWidth(), sorted[i].getHeight());
        }
        return builder.append(']').toString();
    }

    private static long area(android.util.Size size) {
        return (long) size.getWidth() * size.getHeight();
    }

    private static void appendSize(StringBuilder builder, int width, int height) {
        builder.append(width).append('x').append(height)
                .append('(').append(String.format(Locale.US, "%.1f", width * (double) height / 1_000_000d)).append("MP)");
    }

    private static String camera1FacingToString(int facing) {
        if (facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return "front";
        }
        if (facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
            return "back";
        }
        return "unknown(" + facing + ')';
    }

    private static String camera2FacingToString(Integer facing) {
        if (facing == null) {
            return "unknown";
        }
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            return "front";
        }
        if (facing == CameraCharacteristics.LENS_FACING_BACK) {
            return "back";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
            return "external";
        }
        return "unknown(" + facing + ')';
    }

    private static boolean hasCapability(int[] capabilities, int expectedCapability) {
        if (capabilities == null) {
            return false;
        }
        for (int capability : capabilities) {
            if (capability == expectedCapability) {
                return true;
            }
        }
        return false;
    }

    private static String hardwareLevelToString(Integer level) {
        if (level == null) {
            return "unknown";
        }
        switch (level) {
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                return "legacy";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                return "limited";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                return "full";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                return "level_3";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL:
                return "external";
            default:
                return "unknown(" + level + ')';
        }
    }

    private static String formatFloat(float value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static void logLongValue(String label, String value) {
        if (value == null || value.length() <= LOG_CHUNK_SIZE) {
            log(label + value);
            return;
        }
        int part = 1;
        for (int start = 0; start < value.length(); start += LOG_CHUNK_SIZE) {
            int end = Math.min(value.length(), start + LOG_CHUNK_SIZE);
            log(label + "part=" + part++ + ' ' + value.substring(start, end));
        }
    }

    private static void log(String value) {
        FileLog.d(PREFIX + value);
    }
}
