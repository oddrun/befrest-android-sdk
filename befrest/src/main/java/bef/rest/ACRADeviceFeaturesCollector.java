/*
 *  Copyright 2010 Kevin Gaudin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package bef.rest;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;

/**
 * Features declared as available on the device.
 * 
 * @author Kevin Gaudin
 * 
 */
final class ACRADeviceFeaturesCollector {
    private static final String TAG = BefLog.TAG_PREF + "ACRADeviceFeaturesCollector";

    public static String getFeatures( Context ctx) {

        final StringBuilder result = new StringBuilder();
        try {
            final PackageManager pm = ctx.getPackageManager();
            final FeatureInfo[] features = pm.getSystemAvailableFeatures();
            for (final FeatureInfo feature : features) {
                final String featureName = feature.name;
                if(featureName != null) {
                    result.append(featureName);
                } else {
                    result.append("glEsVersion = ").append(feature.getGlEsVersion());
                }
                result.append("\n");
            }
        } catch (Throwable e) {
            BefLog.w(TAG, "Couldn't retrieve DeviceFeatures for " + ctx.getPackageName() + "\n"+ e.getMessage());
            result.append("Could not retrieve data: ");
            result.append(e.getMessage());
        }

        return result.toString();
    }
}
