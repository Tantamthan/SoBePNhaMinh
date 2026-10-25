package com.sobep.ui.discover;

import android.os.Bundle;

import com.sobep.R;
import com.sobep.databinding.ActivityDiscoverBinding;
import com.sobep.ui.BaseActivity;


public class DiscoverActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityDiscoverBinding binding = ActivityDiscoverBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        binding.textResultCount.setText(getString(R.string.format_recipe_count, 0));
        binding.buttonBack.setOnClickListener(v -> finish());
    }
}
