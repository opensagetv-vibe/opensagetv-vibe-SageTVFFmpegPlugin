package org.opensagetv.vibe.ffmpeg;

import java.io.IOException;
import java.nio.file.Files;
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
    public static final String VERSION = "0.1.2";
    private final RuntimePaths paths;
    private final MimRuntime mim;
    private final LauncherRepair launcher;
    private volatile String lastAction = "";
    private volatile long restoreConfirmationUntilMs;

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
        "status.raw", "capabilities.raw", "action.last",
        "action.reload", "action.repairLauncher", "action.restoreDefaultIni"
    };

    private static final Map<String, Setting> SETTINGS = new LinkedHashMap<String, Setting>();
    static {
        add("general.enabled", "general", "enabled", "true", CONFIG_BOOL, null, "MIM Enabled");
        add("hardware.backend", "hardware", "backend", "auto", CONFIG_CHOICE,
                new String[]{"auto","vaapi","qsv","nvenc","amf","d3d12va","software"}, "Hardware Backend");
        add("hardware.hardware_decode", "hardware", "hardware_decode", "false", CONFIG_BOOL, null, "Hardware Decode");
        add("hardware.active_file_hardware_decode", "hardware", "active_file_hardware_decode", "false", CONFIG_BOOL, null, "Live/Growing Hardware Decode");
        add("closed_captions.preserve_a53cc", "closed_captions", "preserve_a53cc", "true", CONFIG_BOOL, null, "Preserve CEA-608/708");
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
        log("started version=" + VERSION + " sageHome=" + paths.sageHome);
    }
    public void stop() { }
    public void destroy() { }

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
                if ("mimVersion".equals(tail) || "platform".equals(tail) || "ffmpegVersion".equals(tail) || "selectedBackend".equals(tail))
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
                lastAction = "updated INI " + mapped.section + "/" + mapped.key;
                return;
            }
            if ("action.reload".equals(setting)) {
                mim.status(true); mim.capabilities(true); lastAction = "reloaded status/capabilities"; return;
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
        if (SETTINGS.containsKey(setting)) return "Stored directly in ffmpeg.real.ini. Manual edits remain authoritative and are preserved.";
        if (setting != null && setting.startsWith("capabilities.")) return "Read-only value from MIM's authoritative hardware detector and preflight.";
        if (setting != null && setting.startsWith("status.")) return "Read-only current/last MIM transcode state.";
        if ("action.restoreDefaultIni".equals(setting)) return "Press twice within 15 seconds. The current live INI is backed up before the default is restored.";
        if (setting != null && setting.startsWith("action.")) return "Server-side maintenance action; stock ffmpeg is never replaced.";
        return "Read-only plugin/runtime health information.";
    }
    public String getConfigLabel(String setting) {
        Setting mapped = SETTINGS.get(setting);
        if (mapped != null) return mapped.label;
        if ("health.launcher".equals(setting)) return "Launcher Health";
        if ("health.runtime".equals(setting)) return "Runtime Health";
        if ("ini.path".equals(setting)) return "Live INI Path";
        if ("ini.lastModified".equals(setting)) return "Live INI Last Modified";
        if ("status.raw".equals(setting)) return "Raw MIM Status";
        if ("capabilities.raw".equals(setting)) return "Raw Hardware Capabilities";
        if ("action.last".equals(setting)) return "Last Maintenance Result";
        if ("action.reload".equals(setting)) return "Reload Status and Hardware";
        if ("action.repairLauncher".equals(setting)) return "Repair SageTVTranscoder Bridge";
        if ("action.restoreDefaultIni".equals(setting)) return "Restore Default INI (Press Twice)";
        if (setting != null && setting.startsWith("status.last.")) return "Last Transcode " + title(setting.substring("status.last.".length()));
        if (setting != null && setting.startsWith("status.")) return "MIM " + title(setting.substring("status.".length()));
        if (setting != null && setting.startsWith("capabilities.")) return "Hardware " + title(setting.substring("capabilities.".length()));
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

    private static void add(String name, String section, String key, String def, int type, String[] options, String label) {
        SETTINGS.put(name, new Setting(section, key, def, type, options, label));
    }
    private static String title(String value) {
        if (value == null || value.length() == 0) return "";
        String spaced = value.replace('.', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
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
