/******************************************************************************
 * Copyright 2015-2016 Befrest
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package bef.rest;

import android.content.Context;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

class BefrestInvocHandler implements InvocationHandler {
    private static final String TAG = BefLog.TAG_PREF + "BefrestInvHdlr";
    BefrestImpl obj;
    Context context;

    public BefrestInvocHandler(BefrestImpl obj, Context context) {
        this.obj = obj;
        this.context = context;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object res;
        try {
            res = method.invoke(obj, args);
        } catch (Throwable t) {
            if (isBefrestGeneratedException(t))
                throw getSpecificBefrestException(t);
            ACRACrashReport crash = new ACRACrashReport(context, t);
            crash.message = "Exception while invoking " + method.getName() + "on BefrestImpl";
            crash.setHandled(false);
            crash.report();
            throw t;
        }
        return res;
    }

    private boolean isBefrestGeneratedException(Throwable t) {
        while (t != null) {
            if (t instanceof BefrestImpl.BefrestException)
                return true;
            t = t.getCause();
        }
        return false;
    }

    private Throwable getSpecificBefrestException(Throwable t) {
        while (!(t instanceof BefrestImpl.BefrestException))
            t = t.getCause();
        return t;
    }
}
