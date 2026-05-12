package com.bro.brorcc.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.bro.brorcc.R;

/** Simple container Activity hosting {@link TunnelViewFragment}. */
public class TunnelViewActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_container);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new TunnelViewFragment())
                    .commit();
        }
    }
}
