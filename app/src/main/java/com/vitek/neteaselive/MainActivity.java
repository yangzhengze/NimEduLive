package com.vitek.neteaselive;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.vitek.neteaselive.im.activity.LoginActivity;

public class MainActivity extends AppCompatActivity {
    Button btGo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btGo = (Button) findViewById(R.id.btGo);
    }

    @Override
    protected void onStart() {
        super.onStart();
        btGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LoginActivity.start(MainActivity.this);
            }
        });
    }
}
