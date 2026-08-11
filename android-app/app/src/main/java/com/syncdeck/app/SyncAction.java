package com.syncdeck.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SyncAction {
    public String id = "";
    public String label = "";
    public String type = "app";
    public String target = "";
    public String arguments = "";
    public String workingDirectory = "";
    public String[] processNames = new String[0];
    public String[] appNames = new String[0];
    public String fallbackUrl = "";
    public String icon = "app";
    public String imageKey = "";
    public String color = "#697386";
    public boolean confirm;
    public boolean closable = true;
    public boolean enabled = true;
    public boolean isOpen;
    public int windowCount;

    public static SyncAction fromJson(JSONObject object) throws JSONException {
        SyncAction action = new SyncAction();
        action.id = text(object, "Id", "id");
        action.label = text(object, "Label", "label");
        action.type = fallback(text(object, "Type", "type"), "app");
        action.target = text(object, "Target", "target");
        action.arguments = text(object, "Arguments", "arguments");
        action.workingDirectory = text(object, "WorkingDirectory", "workingDirectory");
        action.processNames = array(object, "ProcessNames", "processNames");
        action.appNames = array(object, "AppNames", "appNames");
        action.fallbackUrl = text(object, "FallbackUrl", "fallbackUrl");
        action.icon = fallback(text(object, "Icon", "icon"), "app");
        action.imageKey = text(object, "ImageKey", "imageKey");
        action.color = fallback(text(object, "Color", "color"), "#697386");
        action.confirm = bool(object, "Confirm", "confirm", false);
        action.closable = bool(object, "Closable", "closable", false);
        action.enabled = bool(object, "Enabled", "enabled", true);
        action.isOpen = bool(object, "IsOpen", "isOpen", false);
        action.windowCount = integer(object, "WindowCount", "windowCount", 0);
        return action;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("Id", normalizeId());
        object.put("Label", label.trim());
        object.put("Type", type);
        object.put("Target", target.trim());
        object.put("Arguments", arguments.trim());
        object.put("WorkingDirectory", workingDirectory.trim());
        object.put("ProcessNames", new JSONArray(processNames));
        object.put("AppNames", new JSONArray(appNames));
        object.put("FallbackUrl", fallbackUrl.trim());
        object.put("Icon", icon);
        object.put("Color", color.toUpperCase(Locale.ROOT));
        object.put("Confirm", confirm || "command".equals(type));
        object.put("Closable", closable);
        object.put("Enabled", enabled);
        return object;
    }

    public SyncAction copy() {
        SyncAction copy = new SyncAction();
        copy.id = id; copy.label = label; copy.type = type; copy.target = target;
        copy.arguments = arguments; copy.workingDirectory = workingDirectory;
        copy.processNames = processNames.clone(); copy.appNames = appNames.clone();
        copy.fallbackUrl = fallbackUrl; copy.icon = icon; copy.imageKey = imageKey; copy.color = color;
        copy.confirm = confirm; copy.closable = closable; copy.enabled = enabled;
        copy.isOpen = isOpen; copy.windowCount = windowCount;
        return copy;
    }

    public String normalizeId() {
        String value = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        value = value.replaceAll("[^a-z0-9-]+", "-").replaceAll("^-+|-+$", "");
        if (value.length() < 2) value = "acao-" + UUID.randomUUID().toString().substring(0, 8);
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    public static String[] split(String text) {
        if (text == null || text.trim().isEmpty()) return new String[0];
        String[] raw = text.split(",");
        List<String> values = new ArrayList<>();
        for (String item : raw) if (!item.trim().isEmpty()) values.add(item.trim());
        return values.toArray(new String[0]);
    }

    private static String text(JSONObject object, String primary, String alternate) {
        return object.optString(primary, object.optString(alternate, ""));
    }

    private static boolean bool(JSONObject object, String primary, String alternate, boolean defaultValue) {
        return object.has(primary) ? object.optBoolean(primary, defaultValue) : object.optBoolean(alternate, defaultValue);
    }

    private static int integer(JSONObject object, String primary, String alternate, int defaultValue) {
        return object.has(primary) ? object.optInt(primary, defaultValue) : object.optInt(alternate, defaultValue);
    }

    private static String[] array(JSONObject object, String primary, String alternate) {
        JSONArray values = object.optJSONArray(primary);
        if (values == null) values = object.optJSONArray(alternate);
        if (values == null) return new String[0];
        String[] result = new String[values.length()];
        for (int i = 0; i < values.length(); i++) result[i] = values.optString(i, "");
        return result;
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
