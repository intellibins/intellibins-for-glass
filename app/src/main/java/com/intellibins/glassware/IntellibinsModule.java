/*
 * *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.intellibins.glassware;

import com.intellibins.glassware.binlocation.BinLocationModule;
import com.intellibins.glassware.userlocation.UserLocationModule;

import android.app.Application;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Created by prt2121 on 9/28/14.
 */
@Module(
        injects = {
                BinLocationModule.class,
                UserLocationModule.class
        }
)
public class IntellibinsModule {

    private final IntellibinsApp mApp;

    public IntellibinsModule(IntellibinsApp app) {
        this.mApp = app;
    }

    @Provides
    @Singleton
    Application provideApplication() {
        return mApp;
    }
}