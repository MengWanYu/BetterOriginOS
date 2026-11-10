package com.betteroriginos.core;

import android.util.Log;

import com.betteroriginos.feature.camera.CameraShutterFeature;
import com.betteroriginos.feature.desktop.DesktopMotionFeature;
import com.betteroriginos.feature.media.MediaCardFeature;
import com.betteroriginos.feature.notify.NotifyRowFeature;
import com.betteroriginos.feature.fileman.FileManagerRenameFeature;
import com.betteroriginos.feature.picker.PickerAlbumsFeature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FeatureManager {
    public static final String TAG = "BetterOriginOS";

    private static final List<Feature> FEATURES = Collections.unmodifiableList(
            Arrays.<Feature>asList(
                    new NotifyRowFeature(),
                    new MediaCardFeature(),
                    new PickerAlbumsFeature(),
                    new FileManagerRenameFeature(),
                    new CameraShutterFeature(),
                    new DesktopMotionFeature()
            ));

    private FeatureManager() {
    }

    public static List<Feature> all() {
        return FEATURES;
    }

    public static Feature byId(String id) {
        for (Feature f : FEATURES) {
            if (f.id().equals(id)) {
                return f;
            }
        }
        return null;
    }

    public static void installAll(ClassLoader classLoader, String packageName) {
        for (Feature f : FEATURES) {
            if (!targets(f.scopes(), packageName)) {
                continue;
            }
            try {
                f.install(classLoader, packageName);
                Log.i(TAG, "installed feature " + f.id() + " for " + packageName);
            } catch (Throwable t) {
                Log.e(TAG, "install " + f.id() + " failed", t);
            }
        }
    }

    public static List<String> appPackages() {
        Set<String> set = new LinkedHashSet<>();
        for (Feature f : FEATURES) {
            String[] scopes = f.scopes();
            if (scopes != null && scopes.length > 0) {
                set.add(scopes[0]);
            }
        }
        return new ArrayList<>(set);
    }

    public static List<Feature> forPackage(String packageName) {
        List<Feature> list = new ArrayList<>();
        for (Feature f : FEATURES) {
            String[] scopes = f.scopes();
            if (scopes != null && scopes.length > 0 && scopes[0].equals(packageName)) {
                list.add(f);
            }
        }
        return list;
    }

    private static boolean targets(String[] scopes, String packageName) {
        if (scopes == null) {
            return false;
        }
        for (String s : scopes) {
            if (s.equals(packageName)) {
                return true;
            }
        }
        return false;
    }
}
