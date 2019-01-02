/*
 Copyright 2015-2016 Befrest
 <p/>
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 <p/>
 http://www.apache.org/licenses/LICENSE-2.0
 <p/>
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package bef.rest;

import android.content.Context;

import java.lang.reflect.Proxy;

/**
 * Created by hojjatimani on 3/2/2016 AD.
 */
public class BefrestFactory {
    private static Object instance;

    public static Befrest getInstance(Context context) {
        if (instance != null) return (Befrest) instance;
        synchronized (BefrestImpl.class) {
            if (instance == null) {
                Context applicationContext = context.getApplicationContext();
                BefrestImpl befrest = new BefrestImpl(applicationContext);
                ClassLoader classLoader = befrest.getClass().getClassLoader();
                Class<?>[] interfaces = {Befrest.class, BefrestInternal.class};
                BefrestInvocHandler invocationHandler = new BefrestInvocHandler(befrest, applicationContext);
                instance = Proxy.newProxyInstance(classLoader, interfaces, invocationHandler);
            }
        }
        return (Befrest) instance;
    }

    static BefrestInternal getInternalInstance(Context context) {
        return (BefrestInternal) getInstance(context);
    }

    static Befrest getInstanceIfExist(){
        return (Befrest) instance;
    }
}