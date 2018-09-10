/*
 *  Copyright 2012 Kevin Gaudin
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

import org.json.JSONObject;

import java.util.EnumMap;
import java.util.Properties;

/**
 *
 * This is basically the source of {@link Properties} adapted to extend an
 * EnumMap instead of Hashtable and with a few tweaks to avoid losing crazy
 * amounts of android time in the generation of a date comment when storing to file.
 */
final class ACRACrashReportData extends EnumMap<ACRAReportField, String> {

    private static final long serialVersionUID = 4112578634029874840L;

    /**
     * Constructs a new {@code Properties} object.
     */
    public ACRACrashReportData() {
        super(ACRAReportField.class);
    }

    /**
     * Returns the property with the specified name.
     * 
     * @param key the name of the property to find.
     * @return the named property value, or {@code null} if it can't be found.
     */
    public String getProperty(ACRAReportField key) {
        return super.get(key);
    }

    public JSONObject toJSON() throws ACRAJSONReportBuilder.JSONReportException {
        return ACRAJSONReportBuilder.buildJSONReport(this);
    }
}
