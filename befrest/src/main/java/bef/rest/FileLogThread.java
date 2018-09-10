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

package bef.rest;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

final class FileLogThread extends Thread {
    public volatile Handler handler = null;
    private final Object handlerSyncObject = new Object();

    public FileLogThread(final String threadName) {
        setName(threadName);
        start();
    }

    private void sendMessage(Message msg, int delay) {
        if (handler == null) {
            try {
                synchronized (handlerSyncObject) {
                    handlerSyncObject.wait();
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        if (handler != null) {
            if (delay <= 0) {
                handler.sendMessage(msg);
            } else {
                handler.sendMessageDelayed(msg, delay);
            }
        }
    }

    public void cancelRunnable(Runnable runnable) {
        if (handler == null) {
            synchronized (handlerSyncObject) {
                if (handler == null) {
                    try {
                        handlerSyncObject.wait();
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        }

        if (handler != null) {
            handler.removeCallbacks(runnable);
        }
    }

    public void postRunnable(Runnable runnable) {
        postRunnable(runnable, 0);
    }

    public void postRunnable(Runnable runnable, long delay) {
        if (handler == null) {
            synchronized (handlerSyncObject) {
                if (handler == null) {
                    try {
                        handlerSyncObject.wait();
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        }

        if (handler != null) {
            if (delay <= 0) {
                handler.post(runnable);
            } else {
                handler.postDelayed(runnable, delay);
            }
        }
    }

    public void cleanupQueue() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    public void run() {
        Looper.prepare();
        synchronized (handlerSyncObject) {
            handler = new Handler();
            handlerSyncObject.notify();
        }
        Looper.loop();
    }
}
