package org.telegram.messenger.camera;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Size;
import android.util.SizeF;

import org.telegram.messenger.FileLog;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Describes the camera topology without opening or changing any camera session. */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public final class Camera2Topology {

    private static final float DUPLICATE_RATIO_TOLERANCE = 0.12f;
    private static final String PROFILE_PREFERENCES = "camera2_device_profile";
    private static final String PROFILE_FINGERPRINT = "fingerprint";
    private static final String PROFILE_VALIDATIONS = "validations";
    private static final int PROFILE_SCHEMA = 1;
    private static final long REJECTED_RETRY_INTERVAL = 24L * 60L * 60L * 1000L;

    private static Snapshot cachedSnapshot;
    private static String cachedFingerprint;

    private Camera2Topology() {
    }

    public static synchronized Snapshot get(Context context) {
        String fingerprint = getProfileFingerprint();
        if (cachedSnapshot == null || !fingerprint.equals(cachedFingerprint)) {
            cachedSnapshot = discover(context);
            cachedFingerprint = fingerprint;
        }
        return cachedSnapshot;
    }

    public static Snapshot discover(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return new Snapshot(Collections.emptyList(), Collections.emptyList());
        }
        ArrayList<CameraNode> cameras = new ArrayList<>();
        ArrayList<LensPreset> presets = new ArrayList<>();
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) {
                return new Snapshot(cameras, presets);
            }
            Map<String, String> physicalParents = new HashMap<>();
            for (String cameraId : manager.getCameraIdList()) {
                try {
                    CameraNode node = inspectCamera(context, manager, cameraId);
                    cameras.add(node);
                    for (String physicalId : node.physicalCameraIds) {
                        physicalParents.put(physicalId, cameraId);
                    }
                } catch (Exception e) {
                    FileLog.e("[CameraTopology] unable to inspect camera #" + cameraId, e);
                }
            }
            for (CameraNode node : cameras) {
                node.logicalParentId = physicalParents.get(node.cameraId);
            }
            buildFacingPresets(manager, cameras, presets, CameraCharacteristics.LENS_FACING_BACK);
            buildFacingPresets(manager, cameras, presets, CameraCharacteristics.LENS_FACING_FRONT);
        } catch (Exception e) {
            FileLog.e("[CameraTopology] discovery failed", e);
        }
        return new Snapshot(cameras, presets);
    }

    public static synchronized void markCameraValidated(Context context, String cameraId) {
        updateValidation(context, cameraId, true);
    }

    public static synchronized void markCameraRejected(Context context, String cameraId) {
        updateValidation(context, cameraId, false);
    }

    private static void updateValidation(Context context, String cameraId, boolean validated) {
        if (context == null || cameraId == null) {
            return;
        }
        try {
            SharedPreferences preferences = context.getSharedPreferences(PROFILE_PREFERENCES, Context.MODE_PRIVATE);
            ensureCurrentFingerprint(preferences);
            JSONObject validations = new JSONObject(preferences.getString(PROFILE_VALIDATIONS, "{}"));
            JSONObject value = new JSONObject();
            value.put("validated", validated);
            value.put("timestamp", System.currentTimeMillis());
            validations.put(cameraId, value);
            preferences.edit().putString(PROFILE_VALIDATIONS, validations.toString()).apply();
            cachedSnapshot = null;
            FileLog.d("[CameraTopology] validation camera=" + cameraId
                    + " state=" + (validated ? "validated" : "temporarily_rejected"));
        } catch (Exception e) {
            FileLog.e("[CameraTopology] unable to persist camera validation #" + cameraId, e);
        }
    }

    public static void logSnapshot(Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        for (CameraNode camera : snapshot.cameras) {
            FileLog.d("[CameraTopology] camera=" + camera.cameraId
                    + " facing=" + facingToString(camera.facing)
                    + " usable=" + camera.usable
                    + " preview=" + camera.hasPreviewOutput
                    + " jpeg=" + camera.hasJpegOutput
                    + " video=" + camera.hasVideoOutput
                    + " depthOnly=" + camera.depthOnly
                    + " monochrome=" + camera.monochrome
                    + " logical=" + camera.logicalMultiCamera
                    + " parent=" + (camera.logicalParentId == null ? "none" : camera.logicalParentId)
                    + " physical=" + camera.physicalCameraIds
                    + " focal=" + format(camera.focalLengthMm) + "mm"
                    + " sensorWidth=" + format(camera.sensorWidthMm) + "mm"
                    + " hfov=" + format(camera.horizontalFieldOfView) + "deg"
                    + " flash=" + camera.flashAvailable
                    + " autofocus=" + camera.autofocus
                    + " validation=" + camera.validationState.name().toLowerCase(Locale.US)
                    + " maxJpeg=" + format(camera.maxJpegPixels / 1_000_000f) + "MP");
        }
        logFacing(snapshot, CameraCharacteristics.LENS_FACING_BACK);
        logFacing(snapshot, CameraCharacteristics.LENS_FACING_FRONT);
        logPublicAccessSummary(snapshot, CameraCharacteristics.LENS_FACING_BACK);
        logPublicAccessSummary(snapshot, CameraCharacteristics.LENS_FACING_FRONT);
    }

    private static void logPublicAccessSummary(Snapshot snapshot, int facing) {
        int topLevelCameras = 0;
        int usableCameras = 0;
        int logicalCameras = 0;
        Set<String> physicalIds = new HashSet<>();
        for (CameraNode camera : snapshot.cameras) {
            if (camera.facing != facing || camera.logicalParentId != null) {
                continue;
            }
            topLevelCameras++;
            if (camera.usable) {
                usableCameras++;
            }
            if (camera.logicalMultiCamera) {
                logicalCameras++;
            }
            physicalIds.addAll(camera.physicalCameraIds);
        }

        int presetCount = 0;
        for (LensPreset preset : snapshot.presets) {
            if (preset.facing == facing) {
                presetCount++;
            }
        }

        boolean publicAuxiliaryRoutes = topLevelCameras > 1 || logicalCameras > 0
                || !physicalIds.isEmpty() || presetCount > 1;
        String auxiliaryVisibility = publicAuxiliaryRoutes
                ? "exposed"
                : "not_exposed_or_not_present";
        String note = publicAuxiliaryRoutes
                ? "public_api_routes_available"
                : "vendor_or_system_cameras_cannot_be_enumerated_via_public_api";
        FileLog.d("[CameraTopology] publicAccess facing=" + facingToString(facing)
                + " topLevel=" + topLevelCameras
                + " usable=" + usableCameras
                + " logical=" + logicalCameras
                + " physical=" + physicalIds
                + " presets=" + presetCount
                + " auxiliaryVisibility=" + auxiliaryVisibility
                + " note=" + note);
    }

    private static void logFacing(Snapshot snapshot, int facing) {
        ArrayList<LensPreset> facingPresets = new ArrayList<>();
        for (LensPreset preset : snapshot.presets) {
            if (preset.facing == facing) {
                facingPresets.add(preset);
            }
        }
        StringBuilder builder = new StringBuilder();
        for (LensPreset preset : facingPresets) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(format(preset.displayRatio)).append("x:")
                    .append(preset.cameraId);
            if (preset.physicalCameraId != null) {
                builder.append('/').append(preset.physicalCameraId);
            }
            builder.append('(')
                    .append(preset.route == Route.LOGICAL_ZOOM ? "logical" : "independent")
                    .append(',').append(preset.role.name().toLowerCase(Locale.US))
                    .append(",hfov=").append(format(preset.horizontalFieldOfView))
                    .append(')');
        }
        FileLog.d("[CameraTopology] facing=" + facingToString(facing)
                + " main=" + findMainCameraId(facingPresets)
                + " presets=[" + builder + "]");
    }

    private static String findMainCameraId(List<LensPreset> presets) {
        for (LensPreset preset : presets) {
            if (preset.role == LensRole.MAIN) {
                return preset.cameraId;
            }
        }
        return "none";
    }

    private static CameraNode inspectCamera(Context context, CameraManager manager, String cameraId) throws Exception {
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        Integer hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        boolean hasPreview = hasOutputs(map, SurfaceTexture.class);
        boolean hasJpeg = hasOutputs(map, ImageFormat.JPEG);
        boolean hasVideo = hasOutputs(map, MediaRecorder.class);
        boolean backwardCompatible = contains(capabilities, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE);
        boolean depthOutput = contains(capabilities, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT);
        boolean depthOnly = depthOutput && !backwardCompatible;
        boolean monochrome = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && contains(capabilities, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME);
        boolean logical = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && contains(capabilities, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA);
        Set<String> physicalIds = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? new HashSet<>(characteristics.getPhysicalCameraIds())
                : Collections.emptySet();
        float focalLength = shortestPositive(characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS));
        SizeF sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        float sensorWidth = sensorSize == null ? 0f : sensorSize.getWidth();
        float fieldOfView = calculateHorizontalFieldOfView(sensorWidth, focalLength);
        int[] autofocusModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        boolean autofocus = hasAutofocus(autofocusModes);
        boolean flash = Boolean.TRUE.equals(characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE));
        long maxJpegPixels = maxPixels(map == null ? null : map.getOutputSizes(ImageFormat.JPEG));
        ValidationState validationState = getValidationState(context, cameraId);
        boolean usable = hasPreview && hasJpeg && !depthOnly && !monochrome
                && validationState != ValidationState.REJECTED;
        return new CameraNode(
                cameraId,
                facing == null ? -1 : facing,
                hardwareLevel == null ? CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY : hardwareLevel,
                logical,
                physicalIds,
                hasPreview,
                hasJpeg,
                hasVideo,
                depthOnly,
                monochrome,
                usable,
                flash,
                autofocus,
                validationState,
                focalLength,
                sensorWidth,
                fieldOfView,
                maxJpegPixels
        );
    }

    private static void buildFacingPresets(CameraManager manager, List<CameraNode> cameras,
                                           List<LensPreset> destination, int facing) {
        ArrayList<CameraNode> usable = new ArrayList<>();
        for (CameraNode camera : cameras) {
            if (camera.facing == facing && camera.usable && camera.logicalParentId == null) {
                usable.add(camera);
            }
        }
        if (usable.isEmpty()) {
            return;
        }
        CameraNode main = Collections.max(usable, Comparator.comparingDouble(Camera2Topology::mainCameraScore));
        float referenceScale = resolveReferenceScale(manager, main);
        if (referenceScale <= 0f) {
            referenceScale = 1f;
        }

        ArrayList<LensPreset> candidates = new ArrayList<>();
        for (CameraNode camera : usable) {
            if (camera.logicalMultiCamera && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    && !camera.physicalCameraIds.isEmpty()) {
                for (String physicalId : camera.physicalCameraIds) {
                    try {
                        CameraCharacteristics physical = manager.getCameraCharacteristics(physicalId);
                        float focal = shortestPositive(physical.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS));
                        SizeF sensor = physical.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                        float sensorWidth = sensor == null ? 0f : sensor.getWidth();
                        addCandidate(candidates, facing, camera.cameraId, physicalId, Route.LOGICAL_ZOOM,
                                focal, sensorWidth, referenceScale);
                    } catch (Exception e) {
                        FileLog.e("[CameraTopology] unable to inspect physical camera #" + physicalId, e);
                    }
                }
            } else {
                addCandidate(candidates, facing, camera.cameraId, null, Route.INDEPENDENT_CAMERA,
                        camera.focalLengthMm, camera.sensorWidthMm, referenceScale);
            }
        }
        addMainCandidateIfMissing(candidates, facing, main, referenceScale);
        Collections.sort(candidates, Comparator.comparingDouble(preset -> preset.displayRatio));

        ArrayList<LensPreset> deduplicated = new ArrayList<>();
        for (LensPreset candidate : candidates) {
            int duplicateIndex = findDuplicateRatio(deduplicated, candidate.displayRatio);
            if (duplicateIndex < 0) {
                deduplicated.add(candidate);
            } else if (isBetterDuplicate(candidate, deduplicated.get(duplicateIndex), main.cameraId)) {
                deduplicated.set(duplicateIndex, candidate);
            }
        }
        ensureMainRole(deduplicated, main.cameraId);
        destination.addAll(deduplicated);
    }

    private static float resolveReferenceScale(CameraManager manager, CameraNode main) {
        float referenceScale = opticalScale(main.sensorWidthMm, main.focalLengthMm);
        if (!main.logicalMultiCamera || Build.VERSION.SDK_INT < Build.VERSION_CODES.P
                || main.physicalCameraIds.isEmpty()) {
            return referenceScale;
        }
        float bestScale = 0f;
        float bestFieldOfViewDistance = Float.MAX_VALUE;
        for (String physicalId : main.physicalCameraIds) {
            try {
                CameraCharacteristics physical = manager.getCameraCharacteristics(physicalId);
                float focal = shortestPositive(physical.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS));
                SizeF sensor = physical.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                float sensorWidth = sensor == null ? 0f : sensor.getWidth();
                float scale = opticalScale(sensorWidth, focal);
                float fieldOfView = calculateHorizontalFieldOfView(sensorWidth, focal);
                if (scale <= 0f || fieldOfView <= 0f) {
                    continue;
                }
                float distance = Math.abs(fieldOfView - 70f);
                if (distance < bestFieldOfViewDistance) {
                    bestFieldOfViewDistance = distance;
                    bestScale = scale;
                }
            } catch (Exception e) {
                FileLog.e("[CameraTopology] unable to resolve main physical camera #" + physicalId, e);
            }
        }
        return bestScale > 0f ? bestScale : referenceScale;
    }

    private static void addCandidate(List<LensPreset> destination, int facing, String cameraId,
                                     String physicalCameraId, Route route, float focalLength,
                                     float sensorWidth, float referenceScale) {
        float scale = opticalScale(sensorWidth, focalLength);
        if (scale <= 0f || referenceScale <= 0f) {
            return;
        }
        float ratio = normalizeRatio(scale / referenceScale);
        destination.add(new LensPreset(
                facing,
                cameraId,
                physicalCameraId,
                route,
                ratio,
                classify(ratio),
                calculateHorizontalFieldOfView(sensorWidth, focalLength)
        ));
    }

    private static void addMainCandidateIfMissing(List<LensPreset> presets, int facing,
                                                   CameraNode main, float referenceScale) {
        for (LensPreset preset : presets) {
            if (Math.abs(preset.displayRatio - 1f) < DUPLICATE_RATIO_TOLERANCE) {
                return;
            }
        }
        presets.add(new LensPreset(
                facing,
                main.cameraId,
                null,
                Route.INDEPENDENT_CAMERA,
                1f,
                LensRole.MAIN,
                main.horizontalFieldOfView
        ));
    }

    private static void ensureMainRole(List<LensPreset> presets, String mainCameraId) {
        LensPreset nearest = null;
        for (LensPreset preset : presets) {
            if (nearest == null || Math.abs(preset.displayRatio - 1f) < Math.abs(nearest.displayRatio - 1f)) {
                nearest = preset;
            }
        }
        if (nearest == null) {
            return;
        }
        int index = presets.indexOf(nearest);
        presets.set(index, new LensPreset(
                nearest.facing,
                mainCameraId,
                nearest.physicalCameraId,
                nearest.route,
                1f,
                LensRole.MAIN,
                nearest.horizontalFieldOfView
        ));
        Collections.sort(presets, Comparator.comparingDouble(preset -> preset.displayRatio));
    }

    private static int findDuplicateRatio(List<LensPreset> presets, float ratio) {
        for (int i = 0; i < presets.size(); i++) {
            if (Math.abs(presets.get(i).displayRatio - ratio) < DUPLICATE_RATIO_TOLERANCE) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isBetterDuplicate(LensPreset candidate, LensPreset current, String mainCameraId) {
        if (candidate.cameraId.equals(mainCameraId) != current.cameraId.equals(mainCameraId)) {
            return candidate.cameraId.equals(mainCameraId);
        }
        return candidate.route == Route.LOGICAL_ZOOM && current.route != Route.LOGICAL_ZOOM;
    }

    private static double mainCameraScore(CameraNode camera) {
        double score = 0;
        if (camera.logicalMultiCamera) score += 1000;
        if (camera.flashAvailable) score += 220;
        if (camera.autofocus) score += 120;
        if (camera.hasVideoOutput) score += 40;
        if (camera.horizontalFieldOfView > 0f) {
            score += Math.max(0, 120 - Math.abs(camera.horizontalFieldOfView - 70f) * 3f);
        }
        score += Math.min(100, camera.maxJpegPixels / 250_000d);
        if (camera.hardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL) score += 30;
        if (camera.hardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3) score += 40;
        return score;
    }

    private static boolean hasOutputs(StreamConfigurationMap map, Class<?> outputClass) {
        if (map == null) {
            return false;
        }
        try {
            Size[] sizes = map.getOutputSizes(outputClass);
            return sizes != null && sizes.length > 0;
        } catch (Exception ignore) {
            return false;
        }
    }

    private static boolean hasOutputs(StreamConfigurationMap map, int format) {
        if (map == null) {
            return false;
        }
        try {
            Size[] sizes = map.getOutputSizes(format);
            return sizes != null && sizes.length > 0;
        } catch (Exception ignore) {
            return false;
        }
    }

    private static boolean hasAutofocus(int[] modes) {
        if (modes == null) {
            return false;
        }
        for (int mode : modes) {
            if (mode != CaptureRequest.CONTROL_AF_MODE_OFF) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(int[] values, int expected) {
        if (values == null) {
            return false;
        }
        for (int value : values) {
            if (value == expected) {
                return true;
            }
        }
        return false;
    }

    private static float shortestPositive(float[] values) {
        if (values == null) {
            return 0f;
        }
        float result = Float.MAX_VALUE;
        for (float value : values) {
            if (value > 0f) {
                result = Math.min(result, value);
            }
        }
        return result == Float.MAX_VALUE ? 0f : result;
    }

    private static long maxPixels(Size[] sizes) {
        long result = 0;
        if (sizes != null) {
            for (Size size : sizes) {
                result = Math.max(result, (long) size.getWidth() * size.getHeight());
            }
        }
        return result;
    }

    private static float opticalScale(float sensorWidth, float focalLength) {
        return sensorWidth > 0f && focalLength > 0f ? focalLength / sensorWidth : 0f;
    }

    private static float calculateHorizontalFieldOfView(float sensorWidth, float focalLength) {
        if (sensorWidth <= 0f || focalLength <= 0f) {
            return 0f;
        }
        return (float) Math.toDegrees(2d * Math.atan(sensorWidth / (2d * focalLength)));
    }

    private static float normalizeRatio(float ratio) {
        if (Math.abs(ratio - 1f) < DUPLICATE_RATIO_TOLERANCE) {
            return 1f;
        }
        float nearestInteger = Math.round(ratio);
        if (nearestInteger >= 1f && Math.abs(ratio - nearestInteger) <= 0.15f) {
            return nearestInteger;
        }
        float nearestHalf = Math.round(ratio * 2f) / 2f;
        if (nearestHalf > 0f && Math.abs(ratio - nearestHalf) <= 0.08f) {
            return nearestHalf;
        }
        return Math.max(0.1f, Math.round(ratio * 10f) / 10f);
    }

    private static LensRole classify(float ratio) {
        if (ratio < 0.9f) {
            return LensRole.ULTRAWIDE;
        }
        if (ratio > 1.15f) {
            return LensRole.TELEPHOTO;
        }
        return LensRole.MAIN;
    }

    private static String facingToString(int facing) {
        if (facing == CameraCharacteristics.LENS_FACING_BACK) return "back";
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) return "front";
        if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) return "external";
        return "unknown";
    }

    private static String format(float value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String getProfileFingerprint() {
        return PROFILE_SCHEMA + ":" + Build.VERSION.SDK_INT + ":" + Build.FINGERPRINT;
    }

    private static void ensureCurrentFingerprint(SharedPreferences preferences) {
        String fingerprint = getProfileFingerprint();
        if (!fingerprint.equals(preferences.getString(PROFILE_FINGERPRINT, null))) {
            preferences.edit()
                    .putString(PROFILE_FINGERPRINT, fingerprint)
                    .remove(PROFILE_VALIDATIONS)
                    .apply();
        }
    }

    private static ValidationState getValidationState(Context context, String cameraId) {
        if (context == null) {
            return ValidationState.UNKNOWN;
        }
        try {
            SharedPreferences preferences = context.getSharedPreferences(PROFILE_PREFERENCES, Context.MODE_PRIVATE);
            ensureCurrentFingerprint(preferences);
            JSONObject validations = new JSONObject(preferences.getString(PROFILE_VALIDATIONS, "{}"));
            JSONObject value = validations.optJSONObject(cameraId);
            if (value == null) {
                return ValidationState.UNKNOWN;
            }
            if (value.optBoolean("validated", false)) {
                return ValidationState.VALIDATED;
            }
            long timestamp = value.optLong("timestamp", 0L);
            if (System.currentTimeMillis() - timestamp < REJECTED_RETRY_INTERVAL) {
                return ValidationState.REJECTED;
            }
        } catch (Exception e) {
            FileLog.e("[CameraTopology] unable to read camera validation #" + cameraId, e);
        }
        return ValidationState.UNKNOWN;
    }

    public enum Route {
        LOGICAL_ZOOM,
        INDEPENDENT_CAMERA
    }

    public enum LensRole {
        ULTRAWIDE,
        MAIN,
        TELEPHOTO
    }

    public enum ValidationState {
        UNKNOWN,
        VALIDATED,
        REJECTED
    }

    public static final class Snapshot {
        public final List<CameraNode> cameras;
        public final List<LensPreset> presets;

        Snapshot(List<CameraNode> cameras, List<LensPreset> presets) {
            this.cameras = Collections.unmodifiableList(new ArrayList<>(cameras));
            this.presets = Collections.unmodifiableList(new ArrayList<>(presets));
        }

        public List<LensPreset> getPresets(int facing) {
            ArrayList<LensPreset> result = new ArrayList<>();
            for (LensPreset preset : presets) {
                if (preset.facing == facing) {
                    result.add(preset);
                }
            }
            return result;
        }

        public String getMainCameraId(int facing) {
            for (LensPreset preset : presets) {
                if (preset.facing == facing && preset.role == LensRole.MAIN) {
                    return preset.cameraId;
                }
            }
            return null;
        }

        public boolean isCameraUsable(String cameraId) {
            for (CameraNode camera : cameras) {
                if (camera.cameraId.equals(cameraId)) {
                    return camera.usable;
                }
            }
            return false;
        }
    }

    public static final class CameraNode {
        public final String cameraId;
        public final int facing;
        public final int hardwareLevel;
        public final boolean logicalMultiCamera;
        public final Set<String> physicalCameraIds;
        public final boolean hasPreviewOutput;
        public final boolean hasJpegOutput;
        public final boolean hasVideoOutput;
        public final boolean depthOnly;
        public final boolean monochrome;
        public final boolean usable;
        public final boolean flashAvailable;
        public final boolean autofocus;
        public final ValidationState validationState;
        public final float focalLengthMm;
        public final float sensorWidthMm;
        public final float horizontalFieldOfView;
        public final long maxJpegPixels;
        public String logicalParentId;

        CameraNode(String cameraId, int facing, int hardwareLevel, boolean logicalMultiCamera,
                   Set<String> physicalCameraIds, boolean hasPreviewOutput, boolean hasJpegOutput,
                   boolean hasVideoOutput, boolean depthOnly, boolean monochrome, boolean usable,
                   boolean flashAvailable, boolean autofocus, ValidationState validationState,
                   float focalLengthMm, float sensorWidthMm,
                   float horizontalFieldOfView, long maxJpegPixels) {
            this.cameraId = cameraId;
            this.facing = facing;
            this.hardwareLevel = hardwareLevel;
            this.logicalMultiCamera = logicalMultiCamera;
            this.physicalCameraIds = Collections.unmodifiableSet(new HashSet<>(physicalCameraIds));
            this.hasPreviewOutput = hasPreviewOutput;
            this.hasJpegOutput = hasJpegOutput;
            this.hasVideoOutput = hasVideoOutput;
            this.depthOnly = depthOnly;
            this.monochrome = monochrome;
            this.usable = usable;
            this.flashAvailable = flashAvailable;
            this.autofocus = autofocus;
            this.validationState = validationState;
            this.focalLengthMm = focalLengthMm;
            this.sensorWidthMm = sensorWidthMm;
            this.horizontalFieldOfView = horizontalFieldOfView;
            this.maxJpegPixels = maxJpegPixels;
        }
    }

    public static final class LensPreset {
        public final int facing;
        public final String cameraId;
        public final String physicalCameraId;
        public final Route route;
        public final float displayRatio;
        public final LensRole role;
        public final float horizontalFieldOfView;

        LensPreset(int facing, String cameraId, String physicalCameraId, Route route,
                   float displayRatio, LensRole role, float horizontalFieldOfView) {
            this.facing = facing;
            this.cameraId = cameraId;
            this.physicalCameraId = physicalCameraId;
            this.route = route;
            this.displayRatio = displayRatio;
            this.role = role;
            this.horizontalFieldOfView = horizontalFieldOfView;
        }
    }
}
