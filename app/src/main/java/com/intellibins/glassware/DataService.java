/*
 * Copyright (c) 2014 Intellibins authors
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of The Intern nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE LISTED COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.intellibins.glassware;

import com.intellibins.glassware.binlocation.IFindBin;
import com.intellibins.glassware.dropofflocation.IFindDropOff;
import com.intellibins.glassware.model.Loc;
import com.intellibins.glassware.storelocation.GooglePlaceService;
import com.intellibins.glassware.storelocation.model.Photo;
import com.intellibins.glassware.storelocation.model.Place;
import com.intellibins.glassware.storelocation.model.Result;
import com.intellibins.glassware.userlocation.IUserLocation;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import retrofit.RestAdapter;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class DataService extends Service {

    private static final String TAG = DataService.class.getSimpleName();

    private static final int MAX_LOCATION = 2;

    @Inject
    IFindBin mBinLocation;

    @Inject
    IUserLocation mUserLocation;

    @Inject
    IFindDropOff mDropOffLocation;

    @Inject
    RestAdapter mRestAdapter;

    // wish Java supports currying :)
    Func1<Location, Observable<List<Loc>>> findClosestBins
            = new Func1<Location, Observable<List<Loc>>>() {
        @Override
        public Observable<List<Loc>> call(Location location) {
            return mBinLocation.getLocs()
                    .toSortedList(
                            new LocUtils()
                                    .compare(location.getLatitude(), location.getLongitude()));
        }
    };

    Func1<Location, Observable<List<Loc>>> findClosestDropOffs
            = new Func1<Location, Observable<List<Loc>>>() {
        @Override
        public Observable<List<Loc>> call(Location location) {
            return mDropOffLocation.getLocs()
                    .toSortedList(
                            new LocUtils()
                                    .compare(location.getLatitude(), location.getLongitude()));
        }
    };

    private Subscription mSubscription;

    public DataService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntellibinsApp app = IntellibinsApp.get(this);
        app.inject(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mUserLocation.start();
        getNearestBinThenFinish();
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mUserLocation.stop();
        mSubscription.unsubscribe();
    }


    private void getNearestBinThenFinish() {
        Observable<Location> userLocation = mUserLocation.observe()
                .take(1);

        userLocation.flatMap(findClosestDropOffs)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<List<Loc>>() {
                    @Override
                    public void call(List<Loc> locs) {
                        EventBus.getDefault().postSticky(locs.get(0));
                        for (Loc loc : locs) {
                            Log.d(TAG, "drop-off : " + loc.address);
                        }
                    }
                });

        Observable<Loc> storeLoc = userLocation.flatMap(new Func1<Location, Observable<Place>>() {
            @Override
            public Observable<Place> call(Location location) {
                return mRestAdapter.create(GooglePlaceService.class)
                        .getPlaces(location.getLatitude() + "," + location.getLongitude(),
                                getResources().getString(R.string.place_api_key));
            }
        }).flatMap(new Func1<Place, Observable<Result>>() {
            @Override
            public Observable<Result> call(Place place) {
                return Observable.from(place.getResults());
            }
        }).first()
                .map(new Func1<Result, Loc>() {
                    @Override
                    public Loc call(Result result) {
                        List<Photo> photo = result.getPhotos();
                        String ref = (photo != null && !photo.isEmpty()) ? photo.get(0)
                                .getPhotoReference() : null;
                        //EventBus.getDefault().postSticky(new ImageEvent(getImage(ref)));
                        com.intellibins.glassware.storelocation.model.Location location = result
                                .getGeometry().getLocation();
                        return new Loc.Builder(result.getName())
                                .address(result.getVicinity())
                                .latitude(location.getLat())
                                .longitude(location.getLng())
                                .image(getImage(ref))
                                .build();
                    }
                });

        Observable<Loc> binLoc = userLocation
                .flatMap(findClosestBins)
                .flatMap(new Func1<List<Loc>, Observable<Loc>>() {
                    @Override
                    public Observable<Loc> call(List<Loc> locs) {
                        return Observable.from(locs);
                    }
                })
                .take(MAX_LOCATION);

        mSubscription = binLoc
                .concatWith(storeLoc)
//                .toSortedList() TODO need function
                .toList()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<List<Loc>>() {
                    @Override
                    public void call(List<Loc> locs) {
                        EventBus.getDefault().postSticky(new DataEvent(locs));
                        stopSelf();
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.v(TAG, throwable.toString());
                    }
                });
    }

    private String getImage(String ref) {
        if (!TextUtils.isEmpty(ref)) {
            Bitmap bitmap;
            InputStream iStream = null;
            try {
                URL url = new URL(
                        "https://maps.googleapis.com/maps/api/place/photo?maxwidth=1600&key="
                                + getResources().getString(R.string.place_api_key)
                                + "&photoreference="
                                + ref);
                HttpURLConnection urlConnection = (HttpURLConnection) url
                        .openConnection();
                urlConnection.connect();
                iStream = urlConnection.getInputStream();
                bitmap = BitmapFactory.decodeStream(iStream);
                return saveBitmap(DataService.this, bitmap);
            } catch (Exception e) {
                Log.d(TAG, e.toString());
            } finally {
                try {
                    if (iStream != null) {
                        iStream.close();
                    }
                } catch (Exception e) {
                    Log.d(TAG, e.toString());
                }
            }
        }
        return "";
    }

    private String saveBitmap(Context context, Bitmap bmp) {
        FileOutputStream out = null;
        try {
            File file = File.createTempFile("store_image", ".jpg", context.getCacheDir());
            out = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 50, out);
            return file.getName();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Throwable ignore) {
            }
        }
        return null;
    }

}
