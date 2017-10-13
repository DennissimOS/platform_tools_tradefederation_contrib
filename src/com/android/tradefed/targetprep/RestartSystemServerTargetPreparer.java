/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

@OptionClass(alias = "restart-system-server")
public class RestartSystemServerTargetPreparer implements ITargetPreparer {
    private static final long SLEEP_MILLIS = 5000L;

    private IRunUtil mRunUtil;

    public RestartSystemServerTargetPreparer() {
        this(RunUtil.getDefault());
    }

    public RestartSystemServerTargetPreparer(IRunUtil runUtil) {
        this.mRunUtil = runUtil;
    }

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        String pid = device.executeShellCommand("pidof system_server");
        device.executeShellCommand("kill " + pid);
        mRunUtil.sleep(SLEEP_MILLIS);
    }
}
