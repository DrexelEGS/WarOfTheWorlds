package info.strank.wotw.fragments;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import info.strank.wotw.R;
import info.strank.wotw.dispatch.Bundling;

public class HelpSensorFragment extends Fragment {

	public HelpSensorFragment() {
		super();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.help_single_sensor, null);
		Bundle args = this.getArguments();
		((TextView) view.findViewById(R.id.name)).setText("/" + args.getString(Bundling.OSC_PREFIX));
		return view;
	}
}