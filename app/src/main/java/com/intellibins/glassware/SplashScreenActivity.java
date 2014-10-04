package com.intellibins.glassware;

import com.intellibins.glassware.binlocation.BinLocationUtils;
import com.intellibins.glassware.binlocation.IBinLocation;
import com.intellibins.glassware.model.Bin;
import com.intellibins.glassware.userlocation.IUserLocation;
import com.intellibins.glassware.view.TuggableView;
import com.prt2121.glass.widget.SliderView;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;

import java.util.List;

import javax.inject.Inject;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class SplashScreenActivity extends BaseGlassActivity {

    private static final String TAG = SplashScreenActivity.class.getSimpleName();

    @Inject
    IBinLocation mBinLocation;

    @Inject
    IUserLocation mUserLocation;

    Func1<Location, Observable<List<Bin>>> findClosestBins
            = new Func1<Location, Observable<List<Bin>>>() {
        @Override
        public Observable<List<Bin>> call(Location location) {
            return mBinLocation.getBins()
                    .toSortedList(
                            new BinLocationUtils()
                                    .compare(location.getLatitude(), location.getLongitude()));
        }
    };

    private TuggableView mTuggableView;

    private Subscription mSubscription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        IntellibinsApp app = IntellibinsApp.get(this);
        app.inject(this);

        mTuggableView = new TuggableView(this, R.layout.activity_splash_screen);
        setContentView(mTuggableView);
        SliderView slider = (SliderView) mTuggableView.findViewById(R.id.progressBar);
        slider.startIndeterminate();

        mUserLocation.start();
        getNearestBinThenFinish();
    }

    private void getNearestBinThenFinish() {
        mSubscription = mUserLocation.observe()
                .take(1)
                .flatMap(findClosestBins)
                .flatMap(new Func1<List<Bin>, Observable<Bin>>() {
                    @Override
                    public Observable<Bin> call(List<Bin> bins) {
                        return Observable.from(bins);
                    }
                })
                .take(5)
                .toList()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<List<Bin>>() {
                    @Override
                    public void call(List<Bin> bins) {
                        new Handler().post(new Runnable() {
                            @Override
                            public void run() {
                                startActivity(
                                        new Intent(SplashScreenActivity.this, MenuActivity.class));
                            }
                        });
                        SplashScreenActivity.this.finish();
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
        mSubscription.unsubscribe();
    }

}
