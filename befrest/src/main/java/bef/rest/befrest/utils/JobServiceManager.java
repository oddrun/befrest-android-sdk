package bef.rest.befrest.utils;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;

import java.util.concurrent.TimeUnit;

import bef.rest.befrest.Befrest;

import static bef.rest.befrest.utils.SDKConst.JOB_ID;

@RequiresApi(api = Build.VERSION_CODES.O)
public class JobServiceManager {
    private static final String TAG = "JobServiceManager";
    private JobScheduler jobScheduler;

    private static class Loader {
        private static volatile JobServiceManager instance = new JobServiceManager();
    }


    private JobServiceManager() {
        if (Befrest.getInstance().isBefrestInitialized())
        jobScheduler = (JobScheduler) Befrest.getInstance().getContext()
                .getSystemService(Context.JOB_SCHEDULER_SERVICE);
        else
            BefrestLog.e(TAG,"Befrest not initialized yet");
    }

    public static JobServiceManager getInstance() {
        return Loader.instance;
    }

    private boolean isJobExist() {
        return jobScheduler != null && jobScheduler.getPendingJob(JOB_ID) != null;
    }

    public void cancelJob() {
        if (isJobExist()) {
            BefrestLog.w(TAG, "job with Id : " + JOB_ID + " cancel");
            jobScheduler.cancel(JOB_ID);
        } else
            BefrestLog.v(TAG, "cancelJob : job already exist");
    }

    public void scheduleJob() {
        if (!isJobExist()) {
            BefrestLog.i(TAG, "setupJob  ");
            JobInfo jobInfo = new JobInfo.Builder(JOB_ID,
                    new ComponentName(Befrest.getInstance().getContext(), Befrest.getInstance().getBackgroundService()))
                    .setPeriodic(TimeUnit.MINUTES.toMillis(15), TimeUnit.MINUTES.toMillis(5))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .setBackoffCriteria(TimeUnit.MINUTES.toMillis(1),
                            JobInfo.BACKOFF_POLICY_LINEAR)
                    .build();
            if (jobScheduler.schedule(jobInfo) == JobScheduler.RESULT_SUCCESS)
                BefrestLog.w(TAG, "job with Id : " + JOB_ID + " successfully scheduled");
        } else {
            BefrestLog.v(TAG, "job already exist");
        }
    }
}
