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

import android.os.Environment;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class BefLog {
    public static final String TAG_PREF = "BEFREST-";
    private static final String TAG = TAG_PREF + "BefLog";
    private static final boolean LogToFile = false;
    public static final String SDK_VERSION_NAME = "2.1.2";
    private OutputStreamWriter streamWriter = null;
    private SimpleDateFormat dateFormat;
    private FileLogThread logQueue = null;
    private File currentFile = null;

    private static int getLogLevel() {
        return BefrestInternal.Util.getLogLevel();
    }

    private static volatile BefLog Instance = null;

    private static BefLog getInstance() {
        BefLog localInstance = Instance;
        if (localInstance == null) {
            synchronized (BefLog.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new BefLog();
                }
            }
        }
        return localInstance;
    }

    private BefLog() {
        if (!LogToFile) {
            return;
        }
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        try {
            String LogsDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/BefrestLogs/" + SDK_VERSION_NAME;
            Log.v("BefLog File", "path : " + LogsDir);
            File dir = new File(LogsDir);
            dir.mkdirs();
            currentFile = new File(dir, new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + ".txt");
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            logQueue = new FileLogThread("logQueue");
            currentFile.createNewFile();
            FileOutputStream stream = new FileOutputStream(currentFile);
            streamWriter = new OutputStreamWriter(stream);
            streamWriter.write("---start log " + dateFormat.format(System.currentTimeMillis()) + " (Pid:" + Process.myPid() + ")-----\n");
            streamWriter.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void e(final String tag, final String message, final Throwable exception) {
        if (!(getLogLevel() > BefrestImpl.LOG_LEVEL_ERROR))
            Log.e(tag, message, exception);
        if (!LogToFile) return;
        final int Tid = Process.myTid();
        final long time = System.currentTimeMillis();
        if (getInstance().streamWriter != null) {
            getInstance().logQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        getInstance().streamWriter.write(getInstance().dateFormat.format(time) + " (Tid:" + Tid + ")" + " E/" + tag + "﹕ " + message + "\n");
                        getInstance().streamWriter.write(exception.toString());
                        getInstance().streamWriter.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    public static void e(final String tag, final String message) {
        if (!(getLogLevel() > BefrestImpl.LOG_LEVEL_ERROR))
        Log.e(tag, message);
        if (!LogToFile) return;
        final int Tid = Process.myTid();
        final long time = System.currentTimeMillis();
        if (getInstance().streamWriter != null) {
            getInstance().logQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        getInstance().streamWriter.write(getInstance().dateFormat.format(time) + " (Tid:" + Tid + ")" + " E/" + tag + "﹕ " + message + "\n");
                        getInstance().streamWriter.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    public static void e(final String tag, final Throwable e) {
        if (!(getLogLevel() > BefrestImpl.LOG_LEVEL_ERROR))
        e.printStackTrace();
        if (!LogToFile) return;
        final int Tid = Process.myTid();
        final long time = System.currentTimeMillis();
        if (getInstance().streamWriter != null) {
            getInstance().logQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        getInstance().streamWriter.write(getInstance().dateFormat.format(time) + " (Tid:" + Tid + ")" + " E/" + tag + "﹕ " + e + "\n");
                        StackTraceElement[] stack = e.getStackTrace();
                        for (StackTraceElement el : stack) {
                            getInstance().streamWriter.write(getInstance().dateFormat.format(time) + " (Tid:" + Tid + ")" + " E/" + tag + "﹕ " + el + "\n");
                        }
                        getInstance().streamWriter.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } else {
            e.printStackTrace();
        }
    }

    public static void d(final String tag, final String message) {
        if (!(getLogLevel() > BefrestImpl.LOG_LEVEL_DEBUG))
        Log.d(tag, message);
        if (!LogToFile) return;
        final int Tid = Process.myTid();
        final long time = System.currentTimeMillis();
        if (getInstance().streamWriter != null) {
            getInstance().logQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        getInstance().streamWriter.write(getInstance().dateFormat.format(time) + " (Tid:" + Tid + ")" + " D/" + tag + "﹕ " + message + "\n");
                        getInstance().streamWriter.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    public static void i(final String tag, final String message) {
        if (!(getLogLevel() > BefrestImpl.LOG_LEVEL_INFO))
        Log.i(tag, message);
        if (!LogToFile) return;
        final int Tid = Process.myTid();
        final long time = System.currentTimeMillis();
        if (getInstance().streamWriter != null) {
            getInstance().logQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        getInstance().streamWriter.write(getInstance().dateFormat.format(time) + " (Tid:" + Tid + ")" + " I/" + tag + "﹕ " + message + "\n");
                        getInstance().streamWriter.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    public static void v(final String tag, final String message) {
        if (!(getLogLevel() > BefrestImpl.LOG_LEVEL_VERBOSE))
        Log.v(tag, message);
        if (!LogToFile) return;
        final int Tid = Process.myTid();
        final long time = System.currentTimeMillis();
        if (getInstance().streamWriter != null) {
            getInstance().logQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        getInstance().streamWriter.write(getInstance().dateFormat.format(time) + " (Tid:" + Tid + ")" + " V/" + tag + "﹕ " + message + "\n");
                        getInstance().streamWriter.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    public static void w(final String tag, final String message) {
        if (!(getLogLevel() > BefrestImpl.LOG_LEVEL_WARN))
        Log.w(tag, message);
        if (!LogToFile) return;
        final int Tid = Process.myTid();
        final long time = System.currentTimeMillis();
        if (getInstance().streamWriter != null) {
            getInstance().logQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        getInstance().streamWriter.write(getInstance().dateFormat.format(time) + " (Tid:" + Tid + ")" + " W/" + tag + ": " + message + "\n");
                        getInstance().streamWriter.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    public static void w(String TAG , String msg, Throwable e){
        w(TAG, msg + "\n" + e.getMessage());
    }

    public static void wtf(String TAG, Throwable ex) {
        e(TAG, ex);
    }

    public static void v(String TAG, String message, Object... objects) {
        String s = "";
        for (Object o : objects) {
            s += o + ", ";
        }
        v(TAG, message + " " + "[" + s + "]");
    }
}