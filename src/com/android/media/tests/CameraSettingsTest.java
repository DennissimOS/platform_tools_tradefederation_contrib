/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.media.tests;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.StreamUtil;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Camera zoom stress test that increments the camera's zoom level across the
 * entire range [min, max], taking a picture at each level.
 */
public class CameraSettingsTest implements IDeviceTest, IRemoteTest {

    private static final String ZOOM_STANZA = "testStressCameraZoom";
    private static final String SCENE_MODES_STANZA = "testStressCameraSceneModes";
    private static final Pattern EXPECTED_LOOP_COUNT_PATTERN =
            Pattern.compile("(Total number of loops:)(\\s*)(\\d+)");
    private static final Pattern ACTUAL_LOOP_COUNT_PATTERN =
            Pattern.compile("(No of loop:)(.*,\\s)(\\d+)$");

    private static final String TEST_CLASS_NAME =
            "com.android.mediaframeworktest.stress.CameraStressTest";
    private static final String TEST_PACKAGE_NAME = "com.android.mediaframeworktest";
    private static final String TEST_RUNNER_NAME =
            "com.android.mediaframeworktest.CameraStressTestRunner";
    private static final String TEST_RU = "CameraApplicationStress";

    private final String mOutputPath = "cameraStressOutput.txt";
    private static final int MAX_TIME_OUT = 90 * 60 * 1000; //90 mins

    @Option(name="testMethodName", description="Used to specify a specific test method to run")
    private String mTestMethodName = null;

    ITestDevice mTestDevice = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);

        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(TEST_PACKAGE_NAME,
                TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.setClassName(TEST_CLASS_NAME);

        if (mTestMethodName != null) {
            runner.setMethodName(TEST_CLASS_NAME, mTestMethodName);
        }
        runner.setMaxTimeToOutputResponse(MAX_TIME_OUT, TimeUnit.MILLISECONDS);

        BugreportCollector bugListener = new BugreportCollector(listener, mTestDevice);
        bugListener.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);
        bugListener.setDescriptiveName(this.getClass().getName());
        Assert.assertTrue(mTestDevice.runInstrumentationTests(runner, bugListener));

        Map<String, String> metrics = parseOutputFile();
        reportMetrics(bugListener, TEST_RU, metrics);
        cleanupDevice();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    /**
     * Wipes the device's external memory of test collateral from prior runs.
     *
     * @throws DeviceNotAvailableException If the device is unavailable or
     *         something happened while deleting files
     */
    private void cleanupDevice() throws DeviceNotAvailableException {
        String extStore = mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        mTestDevice.executeShellCommand(String.format("rm %s/%s", extStore, mOutputPath));
    }

    /**
     * Parses the output file generated by the underlying instrumentation test
     * and returns it to the main driver for later reporting.
     *
     * @return The {@link Map} that contains metrics for the test.
     * @throws DeviceNotAvailableException If the device is unavailable or
     *         something happened while deleting files
     */
    private Map<String, String> parseOutputFile() throws DeviceNotAvailableException {
        File outputFile = null;
        BufferedReader reader = null;
        ArrayList<String> lines = new ArrayList<String>();
        String line = null;
        String key = null;
        Integer expectedCount = null;
        Integer actualCount = null;
        ListIterator<String> listIterator = null;
        Map<String, String> metrics = new HashMap<String, String>();

        // Read in data
        try {
            outputFile = mTestDevice.pullFileFromExternal(mOutputPath);
            reader = new BufferedReader(new FileReader(outputFile));

            while ((line = reader.readLine()) != null) {
                if (line != "") {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            CLog.e(String.format("IOException reading from file: %s", e.toString()));
        } finally {
            StreamUtil.close(reader);
        }

        // Output file looks like:
        // Test name:
        // Total number of loops: 123
        // No of loop: 0, 1, 2, 3, ..., 122 (0 based)
        // Note that the actual count should be +1 as the # of loop is 0 based.
        listIterator = lines.listIterator();

        while (listIterator.hasNext()) {
            line = listIterator.next();
            CLog.d(String.format("Parsing line: \"%s\"", line));

            if (ZOOM_STANZA.equals(line)) {
                key = "CameraZoom";
            } else if (SCENE_MODES_STANZA.equals(line)) {
                key = "CameraSceneMode";
            }

            Matcher expectedMatcher = EXPECTED_LOOP_COUNT_PATTERN.matcher(line);
            if (expectedMatcher.matches()) {
                expectedCount = Integer.valueOf(expectedMatcher.group(3));
                CLog.d(String.format("Found expected count for key \"%s\": %s",
                        key, expectedCount));
            }

            Matcher actualMatcher = ACTUAL_LOOP_COUNT_PATTERN.matcher(line);
            if (actualMatcher.matches()) {
                actualCount = 1 + Integer.valueOf(actualMatcher.group(3));
                CLog.d(String.format("Found actual count for key \"%s\": %s", key, actualCount));
            }

            if ((key != null) && (expectedCount != null) && (actualCount != null)) {
                metrics.put(key, String.format("%d", actualCount));
                key = null;
                expectedCount = null;
                actualCount = null;
            }
        }

        return metrics;
    }

    /**
     * Report run metrics by creating an empty test run to stick them in.
     *
     * @param listener The {@link ITestInvocationListener} of test results
     * @param runName The test name
     * @param metrics The {@link Map} that contains metrics for the given test
     */
    private void reportMetrics(ITestInvocationListener listener, String runName,
            Map<String, String> metrics) {
        InputStreamSource bugreport = mTestDevice.getBugreport();
        listener.testLog("bugreport", LogDataType.BUGREPORT, bugreport);
        bugreport.cancel();

        CLog.d(String.format("About to report metrics: %s", metrics));
        listener.testRunStarted(runName, 0);
        listener.testRunEnded(0, metrics);
    }
}
