package com.betteroriginos.hook;

import android.util.Log;

import com.betteroriginos.core.FeatureManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        Log.i(FeatureManager.TAG, "loaded " + lp.packageName + "/" + lp.processName);
        FeatureManager.installAll(lp.classLoader, lp.packageName);
    }
}
