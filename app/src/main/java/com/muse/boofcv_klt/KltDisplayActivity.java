package com.muse.boofcv_klt;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;

import boofcv.abst.feature.detect.interest.ConfigGeneralDetector;
import boofcv.abst.feature.tracker.PointTracker;
import boofcv.factory.feature.tracker.FactoryPointTracker;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayU8;
import georegression.struct.point.Point2D_I32;

/**
 * Displays KLT tracks.
 *
 * @author Peter Abeles
 */
public class KltDisplayActivity extends PointTrackerDisplayActivity {

    Spinner spinnerView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LayoutInflater inflater = getLayoutInflater();
        LinearLayout controls = (LinearLayout) inflater.inflate(R.layout.objecttrack_controls, null);

        LinearLayout parent = getViewContent();
        parent.addView(controls);

//        FrameLayout iv = getViewPreview();
//        iv.setOnTouchListener(this);

        spinnerView = (Spinner) controls.findViewById(R.id.spinner_algs);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.tracking_objects, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerView.setAdapter(adapter);
//        spinnerView.setOnItemSelectedListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ConfigGeneralDetector config = new ConfigGeneralDetector();
        config.maxFeatures = 150;
        config.threshold = 40;
        config.radius = 3;

        PointTracker<GrayU8> tracker =
                FactoryPointTracker.klt(new int[]{1, 2, 4}, config, 5, GrayU8.class, GrayS16.class);

        setProcessing(new PointProcessing(tracker));
    }

    public void resetPressed(View view) {
        mode = 0;
    }
}