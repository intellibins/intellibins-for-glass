package com.intellibins.glassware;

import com.google.android.glass.view.WindowUtils;
import com.google.android.glass.widget.CardBuilder;

import com.intellibins.glassware.binlocation.BinLocationUtils;
import com.intellibins.glassware.binlocation.IBinLocation;
import com.intellibins.glassware.model.Bin;
import com.intellibins.glassware.userlocation.IUserLocation;
import com.intellibins.glassware.userlocation.UserLocation;
import com.intellibins.glassware.view.TuggableView;

import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;

import java.util.List;

import javax.inject.Inject;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class MenuActivity extends BaseGlassActivity {

    private static final String TAG = MenuActivity.class.getSimpleName();

    @Inject
    IBinLocation mBinLocation;

    @Inject
    IUserLocation mUserLocation;

    private TuggableView mTuggableView;

    private View mView;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        IntellibinsApp app = IntellibinsApp.get(this);
        app.inject(this);

        mView = buildView();

        mTuggableView = new TuggableView(this, mView);

        getWindow().requestFeature(WindowUtils.FEATURE_VOICE_COMMANDS);
        setContentView(mTuggableView);

        mBinLocation.getBins()
                .toSortedList(new BinLocationUtils().compare(40.742994, -73.984030))
                .flatMap(new Func1<List<Bin>, Observable<Bin>>() {
                    @Override
                    public Observable<Bin> call(List<Bin> bins) {
                        return Observable.from(bins);
                    }
                })
                .take(5)
                .subscribeOn(Schedulers.newThread())
                .subscribe(new Action1<Bin>() {
                    @Override
                    public void call(Bin bin) {
                        Log.v(TAG, "bin " + bin.name);
                        Log.v(TAG, "address " + bin.address);
                    }
                });

        mUserLocation.start();
        mUserLocation.observe()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Location>() {
                    @Override
                    public void call(Location location) {
                        Log.v(TAG,
                                location == null ? "empty" : "user loc : " + location.toString());
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTuggableView.activate();
    }

    @Override
    protected void onPause() {
        mTuggableView.deactivate();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mUserLocation.stop();
    }

    private View buildView() {
        CardBuilder card = new CardBuilder(this, CardBuilder.Layout.TEXT);

        card.setText(R.string.hello_world);
        return card.getView();
    }

    @Override
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        if (featureId == WindowUtils.FEATURE_VOICE_COMMANDS ||
                featureId == Window.FEATURE_OPTIONS_PANEL) {
            getMenuInflater().inflate(R.menu.menu_main, menu);
            return true;
        }
        return super.onCreatePanelMenu(featureId, menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if (featureId == WindowUtils.FEATURE_VOICE_COMMANDS ||
                featureId == Window.FEATURE_OPTIONS_PANEL) {
            switch (item.getItemId()) {
                case R.id.plastic_menu_item:
                    break;
                case R.id.paper_menu_item:
                    break;
                default:
                    return true;
            }
            return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    @Override
    protected boolean onTap() {
        openOptionsMenu();
        return true;
    }

}
