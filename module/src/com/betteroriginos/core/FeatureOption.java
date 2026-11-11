package com.betteroriginos.core;

public final class FeatureOption {
    public enum Type {
        SWITCH, CHOICE
    }

    public final String key;
    public final int titleRes;
    public final int summaryRes;
    public final Type type;
    public final String defaultValue;
    public final String[] values;
    public final int[] labelRes;
    public final int[] iconRes;

    private FeatureOption(String key, int titleRes, int summaryRes, Type type,
                          String defaultValue, String[] values, int[] labelRes, int[] iconRes) {
        this.key = key;
        this.titleRes = titleRes;
        this.summaryRes = summaryRes;
        this.type = type;
        this.defaultValue = defaultValue;
        this.values = values;
        this.labelRes = labelRes;
        this.iconRes = iconRes;
    }

    public static FeatureOption choice(String key, int titleRes, int summaryRes,
                                       String defaultValue, String[] values,
                                       int[] labelRes, int[] iconRes) {
        return new FeatureOption(key, titleRes, summaryRes, Type.CHOICE,
                defaultValue, values, labelRes, iconRes);
    }

    public static FeatureOption toggle(String key, int titleRes, int summaryRes,
                                       boolean defaultValue) {
        return new FeatureOption(key, titleRes, summaryRes, Type.SWITCH,
                defaultValue ? "1" : "0", new String[]{"1", "0"}, null, null);
    }

    public int indexOf(String value) {
        if (values == null) {
            return -1;
        }
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }
}
