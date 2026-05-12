package com.bro.brorcc.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * Hosts the mission-control log tabs on the main dashboard.
 */
public class DashboardPagerAdapter extends FragmentStateAdapter {
    private static final int TAB_COUNT = 3;

    public DashboardPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new EventsFragment();
            case 1:
                return new TunnelViewFragment();
            case 2:
            default:
                return new MqttViewFragment();
        }
    }

    @Override
    public int getItemCount() {
        return TAB_COUNT;
    }
}
