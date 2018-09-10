package bef.rest;

import android.content.Context;
import android.os.AsyncTask;

import java.io.File;
import java.io.IOException;
import java.net.URL;

class ACRACrashReportSender {
    private static final String TAG = BefLog.TAG_PREF + "ACRACrashReportSender";
    Context context;
    ACRAReportLocator locator;

    final ACRAHttpRequest.Type type = ACRAHttpRequest.Type.JSON;
    final ACRAHttpRequest.Method method = ACRAHttpRequest.Method.PUT;
    final String formUri = "http://79.127.127.144:5984/acra-befrest/_design/acra-storage/_update/report";

    public static void sendCoughtReportsInPossible(Context context) {
        try {
            if (BefrestInternal.Util.isWifiConnected(context)) {
                ACRACrashReportSender crashSender = new ACRACrashReportSender(context);
                crashSender.sendCoughtReports();
            }
        } catch (Exception e) {
            //catch this to prevent recursive reports
            BefLog.v(TAG, "Error in sending befrest reports. ");
        }
    }

    public ACRACrashReportSender(Context context) {
        this.context = context.getApplicationContext();
        locator = new ACRAReportLocator(context);
    }

    public void sendCoughtReports() {
        markReportsAsApproved();
        final File[] reports = locator.getApprovedReports();
        if (reports != null && reports.length > 0 && BefrestImpl.Util.isWifiConnected(context)) {
            new CrashSender(reports).execute();
        }
    }

    private void deleteFile(File file) {
        final boolean deleted = file.delete();
        if (!deleted) {
            BefLog.w(TAG, "Could not delete error report : " + file);
        }
    }

    /**
     * Flag all pending reports as "approved" by the user. These reports can be sent.
     */
    private void markReportsAsApproved() {
        BefLog.d(TAG, "Mark all pending reports as approved.");

        for (File report : locator.getUnapprovedReports()) {
            final File approvedReport = new File(locator.getApprovedFolder(), report.getName());
            if (!report.renameTo(approvedReport)) {
                BefLog.w(TAG, "Could not rename approved report from " + report + " to " + approvedReport);
            }
        }
    }

    class CrashSender extends AsyncTask<String, Void, String> {
        private static final String TAG = BefLog.TAG_PREF + "CrashSender";
        File[] reports;

        public CrashSender(File[] reports) {
            this.reports = reports;
        }

        @Override
        protected String doInBackground(String... params) {
            Thread.currentThread().setName(TAG);
            for (File report : reports) {
                BefLog.d(TAG, "trying to send crash data.");
                if (!BefrestInternal.Util.isWifiConnected(context))
                    break;

                final ACRACrashReportPersister persister = new ACRACrashReportPersister();
                try {
                    final ACRACrashReportData previousCrashReport = persister.load(report);
                    boolean success = send(previousCrashReport);
                    if (success) deleteFile(report);
                    else break;
                } catch (IOException e) {
                    BefLog.w(TAG, "could not load report due to io problems", e);
                    break;
                } catch (Throwable t) {
                    BefLog.w(TAG, "could not send report with unKnwon reason!");
                }
            }
            return null;
        }

        private boolean send(ACRACrashReportData report) {
            try {
                URL reportUrl = new URL(formUri);
                BefLog.d(TAG, "Connect to " + reportUrl.toString());
                final ACRAHttpRequest request = new ACRAHttpRequest();

                // Generate report body depending on requested type
                String reportAsString = "";
                switch (type) {
                    case JSON:
                        reportAsString = report.toJSON().toString();
                        break;
//                    case FORM:
//                    default:
//                        final Map<String, String> finalReport = remap(report);
//                        reportAsString = ACRAHttpRequest.getParamsAsFormString(finalReport);
//                        break;
                }

                // Adjust URL depending on method
                switch (method) {
                    case PUT:
                        reportUrl = new URL(reportUrl.toString() + '/' + report.getProperty(ACRAReportField.REPORT_ID));
                        break;
                    case POST:
                        break;
                    default:
                        throw new UnsupportedOperationException("Unknown method: " + method.name());
                }
                request.send(reportUrl, method, reportAsString, type);
            } catch (IOException e) {
                return false;
            } catch (ACRAJSONReportBuilder.JSONReportException e) {
                return false;
            }
            return true;
        }
    }

    class ReportSenderException extends Exception {

        /**
         * Creates a new {@link ReportSenderException} instance. You can provide a
         * detailed message to explain what went wrong.
         *
         * @param detailMessage A message to explain the cause of this exception.
         * @param throwable     An optional throwable which caused this Exception.
         */
        public ReportSenderException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }

        /**
         * Creates a new {@link ReportSenderException} instance. You can provide a
         * detailed message to explain what went wrong.
         *
         * @param detailMessage A message to explain the cause of this exception.
         **/
        public ReportSenderException(String detailMessage) {
            super(detailMessage);
        }
    }

}
