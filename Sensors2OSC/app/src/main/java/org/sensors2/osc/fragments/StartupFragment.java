package org.sensors2.osc.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

import org.sensors2.common.sensors.Parameters;
import org.sensors2.osc.R;
import org.sensors2.osc.activities.StartUpActivity;
import org.sensors2.osc.dispatch.Bundling;
import org.sensors2.osc.dispatch.OscDispatcher;
import org.sensors2.osc.dispatch.SensorConfiguration;
import org.sensors2.osc.sensors.SensorDimensions;

import java.util.Map;

public class StartupFragment extends Fragment {

    private CompoundButton activeButton;

    /*@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.activity_start_up, container, false);

        activeButton = (CompoundButton) v.findViewById(R.id.active);
        StartUpActivity activity = (StartUpActivity) getActivity();
        activeButton.setOnCheckedChangeListener(activity);
        for (Parameters parameters : activity.getSensors()) {
            createSensorFragments((org.sensors2.osc.sensors.Parameters) parameters);
        }

        return v;
    }*/
    @Override
    /*
    @author: Karishma Changlani
    Added May 3rd 2017
    New updated OnCreateView for wotw
     */
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.activity_start_up_test, container, false);

        activeButton = (ToggleButton) v.findViewById(R.id.toggleButton);
        StartUpActivity activity = (StartUpActivity) getActivity();
        for(Parameters parameters: activity.getSensors()) {
            org.sensors2.osc.sensors.Parameters newParameters = (org.sensors2.osc.sensors.Parameters) parameters;
            String name = newParameters.getName();
            activity.availableSensors.add(name);
            for (String s : activity.desiredSensors) {
                if (name.toLowerCase().contains(s.toLowerCase())) {
                    Bundle args = new Bundle();
                    int dimensions = newParameters.getDimensions();
                    String oscPrefix = newParameters.getOscPrefix();
                    for (Map.Entry<Integer, String> oscSuffix : SensorDimensions.GetOscSuffixes(dimensions).entrySet()) {
                        String direction = oscSuffix.getValue();
                        int i = oscSuffix.getKey();
                        args.putInt(Bundling.SENSOR_TYPE, newParameters.getSensorType());
                        args.putString(Bundling.NAME, direction);
                        args.putString(Bundling.OSC_PREFIX, oscPrefix + direction);
                        args.putInt(Bundling.INDEX, i);
                        args.putString(Bundling.NAME, newParameters.getName());
                        SensorConfiguration sc = new SensorConfiguration();
                        sc.setIndex(args.getInt(Bundling.INDEX, 0));
                        sc.setSensorType(args.getInt(Bundling.SENSOR_TYPE));
                        sc.setOscParam(args.getString(Bundling.OSC_PREFIX));
                        OscDispatcher dispatcher = (OscDispatcher) activity.getDispatcher();
                        dispatcher.addSensorConfiguration(sc);
                    }
                }
            }
        }
        activeButton.setOnCheckedChangeListener(activity);
        return v;
    }

    public void createSensorFragments(org.sensors2.osc.sensors.Parameters parameters) {
        FragmentManager manager = getActivity().getSupportFragmentManager();
        SensorGroupFragment groupFragment = (SensorGroupFragment) manager.findFragmentByTag(parameters.getName());

        if (groupFragment == null) {
            groupFragment = createFragment(parameters, manager);

            FragmentTransaction transaction = manager.beginTransaction();
            transaction.add(R.id.sensor_group, groupFragment, parameters.getName());
            transaction.commit();
        }

    }

    public SensorGroupFragment createFragment(org.sensors2.osc.sensors.Parameters parameters, FragmentManager manager) {
        SensorGroupFragment groupFragment = new SensorGroupFragment();
        Bundle args = new Bundle();
        args.putInt(Bundling.DIMENSIONS, parameters.getDimensions());
        args.putInt(Bundling.SENSOR_TYPE, parameters.getSensorType());
        args.putString(Bundling.OSC_PREFIX, parameters.getOscPrefix());
        args.putString(Bundling.NAME, parameters.getName());
        groupFragment.setArguments(args);

        return groupFragment;
    }

}
