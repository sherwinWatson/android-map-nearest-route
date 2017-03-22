package com.shiverwind.routemap;

import android.content.Context;
import android.os.Bundle;
import android.support.multidex.MultiDex;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        MainActivityFragment fragmentS1 = new MainActivityFragment();
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_frame, fragmentS1).commit();
        System.out.println("tes");
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }


}
