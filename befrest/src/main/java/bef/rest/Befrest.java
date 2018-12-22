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

/**
 * Created by hojjatimani on 3/1/2016 AD.
 */
public interface Befrest {
    /**
     * Every Detail Will Be Printed In Logcat.
     */
    int LOG_LEVEL_VERBOSE = 2;
    /**
     * Data Needed For Debug Will Be Printed.
     */
    int LOG_LEVEL_DEBUG = 3;
    /**
     * Standard Level. You Will Be Aware Of BefrestImpl's Main State
     */
    int LOG_LEVEL_INFO = 4;
    /**
     * Only Warning And Errors.
     */
    int LOG_LEVEL_WARN = 5;
    /**
     * Only Errors.
     */
    int LOG_LEVEL_ERROR = 6;
    /**
     * None Of BefrestImpl Logs Will Be Shown.
     */
    int LOG_LEVEL_NO_LOG = 100;

    Befrest init(long uId, String auth, String chId);
    Befrest setCustomPushService(Class<? extends PushService> customPushService);
    Befrest setUId(long uId);
    Befrest setChId(String chId) ;
    Befrest setAuth(String auth);

    void start();
    void stop();
    Befrest addTopic(String topicName);
    Befrest addTopics(String... topics);
    boolean removeTopic(String topicName);
    Befrest removeTopics(String... topics);
    String[] getCurrentTopics();
    boolean refresh();
    void registerPushReceiver(BefrestPushReceiver receiver);
    void unregisterPushReceiver(BefrestPushReceiver receiver);
    Befrest setLogLevel(int logLevel);
    int getLogLevel();
    int getSdkVersion();
}