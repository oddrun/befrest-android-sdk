package rest.bef.befrest_android_sdk;


import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.Random;

import bef.rest.BefrestFactory;
import rest.bef.befrest_android_sdk.connection.ApiClient;
import rest.bef.befrest_android_sdk.connection.ApiService;
import rest.bef.befrest_android_sdk.connection.model.Entity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private String TAG = "BEFRESTAPP";
    String chid = "";
    String auth = "";
    ApiService apiService;
    ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();



        if (auth == null) {
            Call<Entity> entityCall = apiService.getAuth(chid);
            entityCall.enqueue(new Callback<Entity>() {
                @Override
                public void onResponse(Call<Entity> call, Response<Entity> response) {
                    if (response != null) {
                        Toast.makeText(MainActivity.this, "Auth Token Received : " + response.body().getEntity(), Toast.LENGTH_SHORT).show();
                        auth = response.body().getEntity();
                        initBefrest();
                        saveAuth();
                    }
                }

                @Override
                public void onFailure(Call<Entity> call, Throwable t) {
                    Log.d(TAG, "onFailure: " + t.getMessage());
                }
            });
        } else
            initBefrest();

    }

    private void init() {
        apiService = ApiClient.getClient(this).create(ApiService.class);
        progressDialog = new ProgressDialog(this);
        handleChid();
        auth = getAuth();
    }

    private void saveAuth() {
        SharedPreferences sharedPrefs = getSharedPreferences(
                "auth", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString("auth", auth);
        editor.apply();
    }

    private void initBefrest() {
        BefrestFactory.getInstance(MainActivity.this)
                .init(12013, auth, chid)
                .start();
    }

    private String getAuth() {

        SharedPreferences sharedPrefs = getSharedPreferences(
                "auth", Context.MODE_PRIVATE);
        return sharedPrefs.getString("auth", null);
    }

    private void handleChid() {

        SharedPreferences sharedPrefs1 = getSharedPreferences("chid", Context.MODE_PRIVATE);
        chid = sharedPrefs1.getString("chid", null);
        Log.i(TAG, "onCreate: " + chid);
        if (chid == null) {
            int min = 0;
            int max = 10_000;
            int random = new Random().nextInt((max - min) + 1) + min;
            chid = Build.MANUFACTURER + String.valueOf(Build.VERSION.SDK_INT) + "0" + String.valueOf(System.currentTimeMillis()) + String.valueOf(random);
            sharedPrefs1 = getSharedPreferences(
                    "chid", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPrefs1.edit();
            editor.putString("chid", chid);
            editor.apply();
        }
    }

    public void subTopicOne(View view) {

        BefrestFactory.getInstance(this).addTopic("topicOne");
    }

    public void subTopicTwo(View view) {
        BefrestFactory.getInstance(this).addTopic("topicTwo");
    }

    public void unsubOne(View view) {
        BefrestFactory.getInstance(this).removeTopic("topicOne");
    }

    public void unsubTwo(View view) {
        BefrestFactory.getInstance(this).removeTopic("topicTwo");
    }

    public void stopService(View view) {
        BefrestFactory.getInstance(this).stop();
    }
}
