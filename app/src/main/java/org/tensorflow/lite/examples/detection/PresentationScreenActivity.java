package org.tensorflow.lite.examples.detection;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.view.Window;
import android.view.WindowManager;

public class PresentationScreenActivity extends AppCompatActivity {

    SharedPreferences prefs = null;

    private static int SPLASH_TIME_OUT = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_presentation_screen);
        Window window = this.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.background));
        prefs = getSharedPreferences("org.tensorflow.lite.examples.detection", MODE_PRIVATE);
        setContentView(R.layout.activity_presentation_screen);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(PresentationScreenActivity.this, DetectorActivity.class));
                finish();
            }
        }, SPLASH_TIME_OUT);
    }

    @Override
    public void startActivity(Intent intent) {
        try{
            overridePendingTransition(R.anim.activity_open_enter, 0);
            super.startActivity(intent);
        } catch (Exception ignored){}
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause (){
        super.onPause();
        overridePendingTransition(R.anim.activity_close_bottom,0);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.activity_close_bottom,0);
    }

}
