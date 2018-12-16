package rest.bef.befrest_android_sdk;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import bef.rest.BefrestFactory;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BefrestFactory.getInstance(this)
                .init(12013, new BefrestAuth().generateSubscriptionAuth("chOne",2), "chOne")
                .start();

    }
}
