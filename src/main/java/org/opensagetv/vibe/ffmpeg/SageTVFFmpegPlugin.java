package org.opensagetv.vibe.ffmpeg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

import sage.SageTVPlugin;
import sage.SageTVPluginRegistry;

/**
 * Server-side SageTV Standard plugin for OpenSageTV Vibe FFmpeg Plugin.
 *
 * The authoritative configuration remains ffmpeg.real.ini. Sage.properties is
 * not used to mirror MIM settings. The built-in SageTV plugin configuration
 * API and the companion STVi both call these methods, which edit the INI file.
 */
public final class SageTVFFmpegPlugin implements SageTVPlugin {
    public static final String VERSION = "0.1.5";
    private final RuntimePaths paths;
    private final MimRuntime mim;
    private final LauncherRepair launcher;
    private final CaptionSideChannelService captions;
    private final MimDirectSessionService directMedia;
    private CaptionSideChannelHttpServer captionHttp;
    private volatile String lastAction = "";
    private volatile long restoreConfirmationUntilMs;
    private volatile String lastHardwareReportPath = "";

    private static final String[] VIRTUAL_SETTINGS = new String[] {
        "health.launcher", "health.runtime", "ini.path", "ini.lastModified",
        "status.mimVersion", "status.platform", "status.last.state",
        "status.last.backend", "status.last.encoder", "status.last.hardwareDecode",
        "capabilities.ffmpegVersion", "capabilities.selectedBackend",
        "capabilities.vaapi.compiled", "capabilities.vaapi.devicePresent", "capabilities.vaapi.preflight", "capabilities.vaapi.usable",
        "capabilities.qsv.compiled", "capabilities.qsv.devicePresent", "capabilities.qsv.preflight", "capabilities.qsv.usable",
        "capabilities.nvenc.compiled", "capabilities.nvenc.devicePresent", "capabilities.nvenc.preflight", "capabilities.nvenc.usable",
        "capabilities.amf.compiled", "capabilities.amf.devicePresent", "capabilities.amf.preflight", "capabilities.amf.usable",
        "capabilities.d3d12va.compiled", "capabilities.d3d12va.devicePresent", "capabilities.d3d12va.preflight", "capabilities.d3d12va.usable",
        "capabilities.software.compiled", "capabilities.software.devicePresent", "capabilities.software.preflight", "capabilities.software.usable",
        "hardwareTest.state", "hardwareTest.summary", "hardwareTest.selectedBackend",
        "hardwareTest.vaapi.decode", "hardwareTest.vaapi.filter", "hardwareTest.vaapi.encode", "hardwareTest.vaapi.pipeline",
        "hardwareTest.qsv.decode", "hardwareTest.qsv.filter", "hardwareTest.qsv.encode", "hardwareTest.qsv.pipeline", "hardwareTest.qsv.fallbackPipeline",
        "hardwareTest.nvenc.decode", "hardwareTest.nvenc.filter", "hardwareTest.nvenc.encode", "hardwareTest.nvenc.pipeline",
        "hardwareTest.amf.decode", "hardwareTest.amf.filter", "hardwareTest.amf.encode", "hardwareTest.amf.pipeline",
        "hardwareTest.d3d12va.decode", "hardwareTest.d3d12va.filter", "hardwareTest.d3d12va.encode", "hardwareTest.d3d12va.pipeline",
        "hardwareTest.software.decode", "hardwareTest.software.filter", "hardwareTest.software.encode", "hardwareTest.software.pipeline",
        "hardwareTest.raw", "hardwareTest.reportPath",
        "captionSideChannel.status",
        "status.raw", "capabilities.raw", "action.last",
        "action.reload", "action.runHardwareTest", "action.exportHardwareReport",
        "action.repairLauncher", "action.restoreDefaultIni"
    };

    private static final Map<String, Setting> SETTINGS = new LinkedHashMap<String, Setting>();
    static {
        add("general.enabled", "general", "enabled", "true", CONFIG_BOOL, null, "MIM Enabled");
        add("hardware.backend", "hardware", "backend", "auto", CONFIG_CHOICE,
                new String[]{"auto","vaapi","qsv","nvenc","amf","d3d12va","software"}, "Hardware Backend");
        add("hardware.hardware_decode", "hardware", "hardware_decode", "false", CONFIG_BOOL, null, "GPU Decode");
        add("hardware.active_file_hardware_decode", "hardware", "active_file_hardware_decode", "false", CONFIG_BOOL, null, "Live/Growing GPU Decode");
        add("closed_captions.preserve_a53cc", "closed_captions", "preserve_a53cc", "true", CONFIG_BOOL, null, "Preserve CEA-608/708 (A/53)");
        add("caption_side_channel.enabled", "caption_side_channel", "enabled", "false", CONFIG_BOOL, null, "Fixed Caption Side Channel");
        add("caption_side_channel.bind_address", "caption_side_channel", "bind_address", "0.0.0.0", CONFIG_TEXT, null, "Caption API Bind Address");
        add("caption_side_channel.api_port", "caption_side_channel", "api_port", "31910", CONFIG_INTEGER, null, "Caption API Port");
        add("caption_side_channel.port_base", "caption_side_channel", "port_base", "31920", CONFIG_INTEGER, null, "Caption Listener Port Base");
        add("caption_side_channel.pool_size", "caption_side_channel", "pool_size", "2", CONFIG_INTEGER, null, "Caption Listener Slots");
        add("caption_side_channel.max_records", "caption_side_channel", "max_records", "4096", CONFIG_INTEGER, null, "Caption Record Limit");
        add("caption_side_channel.max_bytes", "caption_side_channel", "max_bytes", "262144", CONFIG_INTEGER, null, "Caption Byte Limit");
        add("filters.deinterlace", "filters", "deinterlace", "auto", CONFIG_CHOICE,
                new String[]{"auto","on","off"}, "Deinterlace");
        add("audio.mode", "audio", "mode", "copy", CONFIG_CHOICE,
                new String[]{"copy","ac3","inherit"}, "Audio Mode");
        add("seek.fast_seek", "seek", "fast_seek", "false", CONFIG_BOOL, null, "Fast Seek");
        add("logging.enabled", "logging", "enabled", "true", CONFIG_BOOL, null, "MIM Logging");
        add("logging.level", "logging", "level", "info", CONFIG_CHOICE,
                new String[]{"error","warning","info","debug","trace"}, "Logging Level");
    }

    public SageTVFFmpegPlugin(SageTVPluginRegistry registry) { this(registry, false); }
    public SageTVFFmpegPlugin(SageTVPluginRegistry registry, boolean reset) {
        paths = RuntimePaths.detect();
        mim = new MimRuntime(paths);
        launcher = new LauncherRepair(paths);
        captions = new CaptionSideChannelService(paths);
        directMedia = new MimDirectSessionService(paths,
                new SageMediaSourceAuthorizer(), captions);
        if (reset) resetConfig();
    }

    public void start() {
        try { ensureLiveIni(); } catch (Exception e) { log("Unable to prepare INI: " + e); }
        String runtimePermissions = mim.repairExecutablePermissions();
        if (!"OK".equals(runtimePermissions)) {
            lastAction = "startup runtime permission repair: " + runtimePermissions;
            log(lastAction);
        }
        String health = launcher.health();
        if (!"healthy".equals(health)) {
            lastAction = "startup launcher repair: " + launcher.repair();
            log(lastAction);
        }
        mim.capabilities(true);
        if (!refreshCaptionService()) log(lastAction);
        log("started version=" + VERSION + " sageHome=" + paths.sageHome);
    }
    public void stop() { stopCaptionServices(); }
    public void destroy() { stopCaptionServices(); }

    public String[] getConfigSettings() {
        String[] result = new String[SETTINGS.size() + VIRTUAL_SETTINGS.length];
        int index = 0;
        for (String setting : SETTINGS.keySet()) result[index++] = setting;
        for (String setting : VIRTUAL_SETTINGS) result[index++] = setting;
        return result;
    }

    public String getConfigValue(String setting) {
        try {
            Setting mapped = SETTINGS.get(setting);
            if (mapped != null) return loadIni().get(mapped.section, mapped.key, mapped.defaultValue);

            if ("ini.path".equals(setting)) return paths.ini.toString();
            if ("ini.defaultPath".equals(setting)) return paths.defaultIni.toString();
            if ("ini.lastModified".equals(setting)) return Files.exists(paths.ini) ? String.valueOf(Files.getLastModifiedTime(paths.ini).toMillis()) : "0";
            if ("health.launcher".equals(setting)) return launcher.health();
            if ("health.runtime".equals(setting)) return mim.health();
            if ("action.last".equals(setting)) return lastAction;
            if ("hardwareTest.reportPath".equals(setting)) return lastHardwareReportPath;
            if ("captionSideChannel.status".equals(setting)) return captions.capabilitiesJson();

            if (setting.startsWith("hardwareTest.")) {
                MimRuntime.Snapshot test = mim.hardwareTest();
                if ("hardwareTest.raw".equals(setting)) return test.ok() ? test.raw : "ERROR: " + test.error;
                String tail = setting.substring("hardwareTest.".length());
                if ("summary".equals(tail))
                    return test.ok() ? compactHardwareSummary(MimRuntime.string(test.json.get(tail))) : "Not Run";
                if ("state".equals(tail))
                    return test.ok() ? displayPipelineState(MimRuntime.string(test.json.get(tail))) : "Not Run";
                if ("selectedBackend".equals(tail))
                    return test.ok() ? displayBackend(MimRuntime.string(test.json.get(tail))) : "Not Run";
                int separator = tail.indexOf('.');
                if (separator > 0) {
                    Object value = MimRuntime.path(test.json, "backends." + tail);
                    return value == null ? "Not Tested" : displayPipelineState(MimRuntime.string(value));
                }
            }

            MimRuntime.Snapshot status = mim.status(false);
            if ("status.raw".equals(setting)) return status.ok() ? status.raw : "ERROR: " + status.error;
            if ("status.error".equals(setting)) return status.error == null ? "" : status.error;
            if (setting.startsWith("status.")) {
                String field = setting.substring("status.".length());
                if ("mimVersion".equals(field) || "platform".equals(field)) return MimRuntime.string(status.json.get(field));
                if (field.startsWith("last.")) return MimRuntime.string(MimRuntime.lastTranscode(status.json, field.substring(5)));
            }

            MimRuntime.Snapshot caps = mim.capabilities(false);
            if ("capabilities.raw".equals(setting)) return caps.ok() ? caps.raw : "ERROR: " + caps.error;
            if ("capabilities.error".equals(setting)) return caps.error == null ? "" : caps.error;
            if (setting.startsWith("capabilities.")) {
                String tail = setting.substring("capabilities.".length());
                if ("ffmpegVersion".equals(tail))
                    return compactFFmpegVersion(MimRuntime.string(caps.json.get(tail)));
                if ("mimVersion".equals(tail) || "platform".equals(tail) || "selectedBackend".equals(tail))
                    return MimRuntime.string(caps.json.get(tail));
                int separator = tail.indexOf('.');
                if (separator > 0) {
                    String backend = tail.substring(0, separator);
                    String field = tail.substring(separator + 1);
                    if ("compiled".equals(field) || "devicePresent".equals(field) ||
                            "preflight".equals(field) || "usable".equals(field)) {
                        Object value = MimRuntime.path(caps.json, "backends." + backend + "." + field);
                        return value == null ? "unavailable" : MimRuntime.string(value);
                    }
                }
            }
        } catch (Exception e) {
            return "ERROR: " + e;
        }
        return "";
    }

    public String[] getConfigValues(String setting) { return null; }

    public int getConfigType(String setting) {
        Setting mapped = SETTINGS.get(setting);
        if (mapped != null) return mapped.type;
        return setting != null && setting.startsWith("action.") && !"action.last".equals(setting)
                ? CONFIG_BUTTON : CONFIG_TEXT;
    }

    public void setConfigValue(String setting, String value) {
        try {
            Setting mapped = SETTINGS.get(setting);
            if (mapped != null) {
                IniDocument ini = loadIni();
                ini.set(mapped.section, mapped.key, value == null ? "" : value);
                ini.saveWithBackup();
                mim.capabilities(true);
                boolean refreshed = !setting.startsWith("caption_side_channel.") || refreshCaptionService();
                if (refreshed) lastAction = "updated INI " + mapped.section + "/" + mapped.key;
                return;
            }
            if ("action.reload".equals(setting)) {
                mim.status(true); mim.capabilities(true); lastAction = "reloaded status/capabilities"; return;
            }
            if ("action.runHardwareTest".equals(setting)) {
                lastAction = "hardware test running; synthetic media only";
                MimRuntime.Snapshot result = mim.runHardwareTest();
                String state = result.ok() ? MimRuntime.string(result.json.get("state")) : "";
                lastAction = result.ok() && "complete".equals(state)
                        ? "hardware test complete: " + MimRuntime.string(result.json.get("summary"))
                        : "FAILED: " + (result.ok() ? MimRuntime.string(result.json.get("summary")) : result.error);
                return;
            }
            if ("action.exportHardwareReport".equals(setting)) {
                lastHardwareReportPath = HardwareReportExporter.export(paths, mim.hardwareTest(), VERSION).toString();
                lastAction = "exported hardware report: " + lastHardwareReportPath;
                return;
            }
            if ("action.repairLauncher".equals(setting)) { lastAction = launcher.repair(); return; }
            if ("action.restoreDefaultIni".equals(setting)) {
                long now = System.currentTimeMillis();
                if (now > restoreConfirmationUntilMs) {
                    restoreConfirmationUntilMs = now + 15000;
                    lastAction = "confirmation required: press Restore Default INI again within 15 seconds";
                    return;
                }
                restoreConfirmationUntilMs = 0;
                if (!Files.isRegularFile(paths.defaultIni)) { lastAction = "FAILED: default INI missing"; return; }
                if (Files.exists(paths.ini)) Files.copy(paths.ini, paths.ini.resolveSibling("ffmpeg.real.ini.bak"), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(paths.defaultIni, paths.ini, StandardCopyOption.REPLACE_EXISTING);
                mim.capabilities(true); lastAction = "restored default INI"; return;
            }
        } catch (Exception e) {
            lastAction = "FAILED: " + e;
            log(lastAction);
        }
    }

    public void setConfigValues(String setting, String[] values) { }
    public String[] getConfigOptions(String setting) {
        Setting mapped = SETTINGS.get(setting);
        return mapped == null ? null : mapped.options;
    }
    public String getConfigHelpText(String setting) {
        if (SETTINGS.containsKey(setting)) return "";
        // Read-only status groups intentionally use compact label/value rows.
        // Their category footer explains the group, so repeating a help line
        // beneath every result wastes space and makes the results look like a
        // prose report instead of the Detailed Setup table requested by users.
        if (setting != null && setting.startsWith("capabilities.")) return "";
        if (setting != null && setting.startsWith("status.")) return "";
        if (setting != null && setting.startsWith("hardwareTest.")) return "";
        if (setting != null && setting.startsWith("health.")) return "";
        if ("action.runHardwareTest".equals(setting)) return "Runs bounded synthetic decode, hardware-filter, encode, and full-pipeline tests. It does not change the selected backend or live INI.";
        if ("action.exportHardwareReport".equals(setting)) return "Exports the latest credential-free JSON result to the server plugin reports directory. Run the hardware test first.";
        if ("action.restoreDefaultIni".equals(setting)) return "Press twice within 15 seconds. The current live INI is backed up before the default is restored.";
        if (setting != null && setting.startsWith("action.")) return "Server-side maintenance action; stock ffmpeg is never replaced.";
        return "Plugin and runtime health reported by the server.";
    }
    public String getConfigLabel(String setting) {
        Setting mapped = SETTINGS.get(setting);
        if (mapped != null) return mapped.label;
        if ("health.launcher".equals(setting)) return "Launcher Health (Read-Only)";
        if ("health.runtime".equals(setting)) return "Runtime Health (Read-Only)";
        if ("ini.path".equals(setting)) return "Live INI Path (Read-Only)";
        if ("ini.lastModified".equals(setting)) return "Live INI Last Modified (Read-Only)";
        if ("status.raw".equals(setting)) return "Raw MIM Status (Read-Only)";
        if ("capabilities.raw".equals(setting)) return "Raw Hardware Capabilities (Read-Only)";
        if ("action.last".equals(setting)) return "Last Maintenance Result (Read-Only)";
        if ("action.reload".equals(setting)) return "Reload Status and Hardware";
        if ("action.runHardwareTest".equals(setting)) return "Run Hardware Test";
        if ("action.exportHardwareReport".equals(setting)) return "Export Hardware Report";
        if ("action.repairLauncher".equals(setting)) return "Repair SageTVTranscoder Bridge";
        if ("action.restoreDefaultIni".equals(setting)) return "Restore Default INI (Press Twice)";
        if ("hardwareTest.reportPath".equals(setting)) return "Exported Hardware Report (Read-Only)";
        if ("captionSideChannel.status".equals(setting)) return "Fixed Caption Service (Read-Only)";
        if ("status.mimVersion".equals(setting)) return "MIM Version (Read-Only)";
        if ("status.platform".equals(setting)) return "Platform (Read-Only)";
        if ("hardwareTest.vaapi.pipeline".equals(setting)) return "VAAPI (Read-Only)";
        if ("hardwareTest.qsv.pipeline".equals(setting)) return "QSV (Read-Only)";
        if ("hardwareTest.qsv.fallbackPipeline".equals(setting)) return "QSV Fallback (Read-Only)";
        if ("hardwareTest.selectedBackend".equals(setting)) return "Backend (Read-Only)";
        if ("hardwareTest.nvenc.pipeline".equals(setting)) return "NVENC (Read-Only)";
        if ("hardwareTest.amf.pipeline".equals(setting)) return "AMF (Read-Only)";
        if ("hardwareTest.d3d12va.pipeline".equals(setting)) return "D3D12VA (Read-Only)";
        if ("hardwareTest.software.pipeline".equals(setting)) return "Software (Read-Only)";
        if (setting != null && setting.startsWith("hardwareTest.")) return title(setting.substring("hardwareTest.".length())) + " (Read-Only)";
        if (setting != null && setting.startsWith("status.last.")) return title(setting.substring("status.last.".length())) + " (Read-Only)";
        if (setting != null && setting.startsWith("status.")) return "MIM " + title(setting.substring("status.".length())) + " (Read-Only)";
        if ("capabilities.ffmpegVersion".equals(setting)) return "FFmpeg Ver. (Read-Only)";
        if ("capabilities.selectedBackend".equals(setting)) return "Backend (Read-Only)";
        if (setting != null && setting.startsWith("capabilities.")) return title(setting.substring("capabilities.".length())) + " (Read-Only)";
        return setting;
    }

    public void resetConfig() {
        // Intentionally does not overwrite a user's live INI. The STVi exposes
        // an explicit restore-default action so reset is never surprising.
        lastAction = "reset ignored: live INI is authoritative";
    }

    public void sageEvent(String eventName, Map eventVars) { }

    private IniDocument loadIni() throws IOException {
        ensureLiveIni();
        return IniDocument.load(paths.ini);
    }

    private void ensureLiveIni() throws IOException {
        if (Files.isRegularFile(paths.ini)) return;
        Files.createDirectories(paths.runtimeDir);
        if (!Files.isRegularFile(paths.defaultIni)) throw new IOException("Default INI missing: " + paths.defaultIni);
        Files.copy(paths.defaultIni, paths.ini);
    }

    private synchronized boolean refreshCaptionService() {
        stopCaptionServices();
        try {
            IniDocument ini = loadIni();
            boolean enabled = Boolean.parseBoolean(ini.get("caption_side_channel", "enabled", "false"));
            if (!enabled) {
                captions.start(false, 31920, 2, 4096, 262144);
                return true;
            }
            Path statusDirectory = resolveRuntimePath(
                    ini.get("status", "directory", "cache/status"), paths.statusDir);
            Path captionPoolDirectory = resolveChildPath(statusDirectory,
                    ini.get("caption_side_channel", "pool_directory", "caption-pool"));
            captions.start(
                    true,
                    integer(ini.get("caption_side_channel", "port_base", "31920"), 31920),
                    integer(ini.get("caption_side_channel", "pool_size", "2"), 2),
                    integer(ini.get("caption_side_channel", "max_records", "4096"), 4096),
                    integer(ini.get("caption_side_channel", "max_bytes", "262144"), 262144),
                    statusDirectory, captionPoolDirectory);
            directMedia.start(Boolean.parseBoolean(ini.get(
                    "caption_side_channel", "mim_direct_enabled", "true")));
            if (!captions.isOperational())
                throw new IOException("caption UDP listener pool did not become operational");
            captionHttp = new CaptionSideChannelHttpServer(
                    ini.get("caption_side_channel", "bind_address", "0.0.0.0").trim(),
                    integer(ini.get("caption_side_channel", "api_port", "31910"), 31910),
                    captions, directMedia,new DirectWatchRecoveryService(
                            DirectWatchSnapshot.STOCK_API,() -> System.nanoTime()/1_000_000L));
            captionHttp.start();
            return true;
        } catch (Exception failure) {
            if (captionHttp != null) captionHttp.stop();
            captionHttp = null;
            directMedia.close();
            captions.fail(failure);
            lastAction = "FAILED to configure Fixed caption service: " + failure;
            log(lastAction);
            return false;
        }
    }

    private synchronized void stopCaptionServices() {
        if (captionHttp != null) captionHttp.stop();
        captionHttp = null;
        captions.close();
        directMedia.close();
    }

    private Path resolveRuntimePath(String configured, Path fallback) {
        String value = configured == null ? "" : configured.trim();
        if (value.length() == 0) return fallback;
        Path candidate = Paths.get(value);
        if (!candidate.isAbsolute()) candidate = paths.runtimeDir.resolve(candidate);
        return candidate.toAbsolutePath().normalize();
    }

    private static Path resolveChildPath(Path parent, String configured) {
        String value = configured == null ? "" : configured.trim();
        Path candidate = value.length() == 0 ? Paths.get("caption-pool") : Paths.get(value);
        if (!candidate.isAbsolute()) candidate = parent.resolve(candidate);
        return candidate.toAbsolutePath().normalize();
    }

    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value == null ? "" : value.trim()); }
        catch (RuntimeException invalid) { return fallback; }
    }

    private static void add(String name, String section, String key, String def, int type, String[] options, String label) {
        SETTINGS.put(name, new Setting(section, key, def, type, options, label));
    }
    private static String title(String value) {
        if (value == null || value.length() == 0) return "";
        String spaced = value.replace('.', ' ').replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
    private static String compactFFmpegVersion(String value) {
        if (value == null) return "";
        String compact = value.trim();
        String prefix = "ffmpeg version ";
        if (compact.regionMatches(true, 0, prefix, 0, prefix.length()))
            compact = compact.substring(prefix.length()).trim();
        int separator = compact.indexOf(' ');
        return separator < 0 ? compact : compact.substring(0, separator);
    }
    private static String compactHardwareSummary(String value) {
        if (value == null) return "";
        String compact = value.trim();
        int marker = compact.indexOf(" hardware pipeline");
        if (marker > 0 && compact.indexOf("passed", marker) > marker)
            return compact.substring(0, marker) + " HW PASS";
        return compact;
    }
    private static String displayPipelineState(String value) {
        if (value == null || value.trim().length() == 0) return "Not Tested";
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if ("pass".equals(normalized)) return "PASS";
        if ("fail".equals(normalized) || "failed".equals(normalized)) return "FAIL";
        if ("unsupported".equals(normalized)) return "Unsupported";
        if ("not tested".equals(normalized) || "not_tested".equals(normalized)) return "Not Tested";
        if ("complete".equals(normalized)) return "Complete";
        if ("running".equals(normalized)) return "Running";
        return title(value);
    }
    private static String displayBackend(String value) {
        if (value == null || value.trim().length() == 0) return "Unavailable";
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if ("software".equals(normalized) || "unavailable".equals(normalized))
            return title(normalized);
        return normalized.toUpperCase(java.util.Locale.ROOT);
    }
    private static void log(String msg) { System.out.println("[SageTVFFmpegPlugin] " + msg); }

    private static final class Setting {
        final String section, key, defaultValue, label;
        final int type;
        final String[] options;
        Setting(String section, String key, String defaultValue, int type, String[] options, String label) {
            this.section = section; this.key = key; this.defaultValue = defaultValue; this.type = type; this.options = options; this.label = label;
        }
    }
}
