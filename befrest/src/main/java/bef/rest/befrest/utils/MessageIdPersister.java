package bef.rest.befrest.utils;

import android.util.Log;

import java.util.Arrays;
import java.util.Iterator;

import static bef.rest.befrest.utils.BefrestPreferences.PREF_LAST_RECEIVED_MESSAGES;
import static bef.rest.befrest.utils.BefrestPreferences.getPrefs;
import static bef.rest.befrest.utils.BefrestPreferences.saveString;

/******************************************************************************
 * Copyright 2015-2016 Befrest
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
public class MessageIdPersister extends BoundedLinkedHashSet<String> {
    int i = 0 ;

    public MessageIdPersister() {
        super(100);
        load();
    }

    private void load() {
        String s = getPrefs().getString(PREF_LAST_RECEIVED_MESSAGES, "");
        if (s != null) {
            if (s.length() > 0)
                this.addAll(Arrays.asList(s.split(",")));
        }
    }

    public void save() {
        Log.i("AKA", "save: "+ ++i);
        saveString(PREF_LAST_RECEIVED_MESSAGES, toString());
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString() {
        try {
            Iterator<String> it = iterator();
            if (!it.hasNext())
                return "";
            StringBuilder sb = new StringBuilder();
            sb.append(it.next());
            while (it.hasNext())
                sb.append(",").append(it.next());
            return sb.toString();
        }catch (Exception e){
            WatchSdk.reportCrash(e,null);
        }
        return "";
    }
}
