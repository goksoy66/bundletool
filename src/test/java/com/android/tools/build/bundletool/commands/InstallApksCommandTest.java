/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApksArchiveFile;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createMasterApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createSplitApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createVariant;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createVariantForSingleSplitApk;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithLocales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredBuilderPropertyException;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.exceptions.InstallationException;
import com.android.tools.build.bundletool.testing.FakeAdbServer;
import com.android.tools.build.bundletool.testing.FakeAndroidHomeVariableProvider;
import com.android.tools.build.bundletool.testing.FakeAndroidSerialVariableProvider;
import com.android.tools.build.bundletool.testing.FakeDevice;
import com.android.tools.build.bundletool.utils.EnvironmentVariableProvider;
import com.android.tools.build.bundletool.utils.flags.FlagParser;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class InstallApksCommandTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;
  private static final String DEVICE_ID = "id1";

  private EnvironmentVariableProvider androidHomeProvider;
  private EnvironmentVariableProvider androidSerialProvider;
  private Path adbPath;
  private Path sdkDirPath;

  @Before
  public void setUp() throws IOException {
    tmpDir = tmp.getRoot().toPath();
    sdkDirPath = Files.createDirectory(tmpDir.resolve("android-sdk"));
    adbPath = sdkDirPath.resolve("platform-tools").resolve("adb");
    Files.createDirectories(adbPath.getParent());
    Files.createFile(adbPath);
    adbPath.toFile().setExecutable(true);
    this.androidHomeProvider = new FakeAndroidHomeVariableProvider(sdkDirPath.toString());
    this.androidSerialProvider = new FakeAndroidSerialVariableProvider(DEVICE_ID);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_onlyApkPaths() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + apksFile),
            androidHomeProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_apkPathsAndAdb() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + apksFile, "--adb=" + adbPath),
            androidHomeProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_deviceId() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser()
                .parse("--apks=" + apksFile, "--adb=" + adbPath, "--device-id=" + DEVICE_ID),
            androidHomeProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setDeviceId(DEVICE_ID)
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_androidSerialVariable() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + apksFile, "--adb=" + adbPath),
            androidSerialProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setDeviceId(DEVICE_ID)
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_modules() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser()
                .parse("--apks=" + apksFile, "--adb=" + adbPath, "--modules=base,feature"),
            androidHomeProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setModules(ImmutableSet.of("base", "feature"))
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void missingApksFlag_fails() {
    expectMissingRequiredBuilderPropertyException(
        "apksArchivePath",
        () ->
            InstallApksCommand.builder()
                .setAdbPath(adbPath)
                .setAdbServer(fakeServerOneDevice(lDeviceWithLocales("en-US")))
                .build());

    expectMissingRequiredFlagException(
        "apks",
        () ->
            InstallApksCommand.fromFlags(
                new FlagParser().parse("--adb=" + adbPath),
                androidHomeProvider,
                fakeServerOneDevice(lDeviceWithLocales("en-US"))));
  }

  @Test
  public void badApkLocation_fails() throws Exception {
    Path apksFile = tmpDir.resolve("/the/apks/is/not/there.apks");

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(fakeServerOneDevice(lDeviceWithLocales("en-US")))
            .build();

    Throwable exception = assertThrows(IllegalArgumentException.class, () -> command.execute());
    assertThat(exception)
        .hasMessageThat()
        .contains(String.format("File '%s' was not found.", apksFile));
  }

  @Test
  public void badAdbLocation_fails() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setAdbPath(Paths.get("bad_adb_path"))
            .setApksArchivePath(apksFile)
            .setAdbServer(fakeServerOneDevice(lDeviceWithLocales("en-US")))
            .build();
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("File 'bad_adb_path' was not found.");
  }

  @Test
  public void noDeviceId_moreThanOneDeviceConnected() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    AdbServer adbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US")),
                FakeDevice.fromDeviceSpec(
                    "id2", DeviceState.ONLINE, lDeviceWithLocales("en-US", "en-GB"))));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .build();

    Throwable exception = assertThrows(CommandExecutionException.class, () -> command.execute());
    assertThat(exception)
        .hasMessageThat()
        .contains("More than one device connected, please provide --device-id.");
  }

  @Test
  public void missingDeviceWithId() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    AdbServer adbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"))));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .setDeviceId("doesnt-exist")
            .build();

    Throwable exception = assertThrows(CommandExecutionException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("Unable to find the requested device.");
  }

  @Test
  public void adbInstallFails_throws() throws Exception {
    Path apksFile =
        createApksArchiveFile(
            createSimpleTableOfContent(Paths.get("base-master.apk")),
            tmpDir.resolve("bundle.apks"));

    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));
    fakeDevice.setInstallApksSideEffect(
        (apks, reinstall) -> {
          throw InstallationException.builder().withMessage("Sample error message").build();
        });

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .build();

    Throwable exception = assertThrows(InstallationException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("Sample error message");
  }

  @Test
  public void deviceSdkIncompatible_throws() throws Exception {
    Path apksFile =
        createApksArchiveFile(
            createLPlusTableOfContent(Paths.get("base-master.apk")), tmpDir.resolve("bundle.apks"));

    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(
            DEVICE_ID,
            DeviceState.ONLINE,
            mergeSpecs(
                sdkVersion(19), abis("arm64_v8a"), locales("en-US"), density(DensityAlias.HDPI)));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .build();

    Throwable exception = assertThrows(CommandExecutionException.class, () -> command.execute());
    assertThat(exception)
        .hasMessageThat()
        .contains("The app doesn't support SDK version of the device: (19).");
  }

  @Test
  public void deviceAbiIncompatible_throws() throws Exception {
    Path apkL = Paths.get("splits/apkL.apk");
    Path apkLx86 = Paths.get("splits/apkL-x86.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .addVariant(
                createVariant(
                    variantSdkTargeting(sdkVersionFrom(21)),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkL),
                        createApkDescription(
                            apkAbiTargeting(AbiAlias.X86, ImmutableSet.of()),
                            apkLx86,
                            /* isMasterSplit= */ false))))
            .build();

    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec =
        mergeSpecs(sdkVersion(21), abis("arm64-v8a"), locales("en-US"), density(DensityAlias.HDPI));
    FakeDevice fakeDevice = FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, deviceSpec);
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .build();

    Throwable exception = assertThrows(CommandExecutionException.class, () -> command.execute());
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "The app doesn't support ABI architectures of the device. Device ABIs: [arm64-v8a], "
                + "app ABIs: [x86]");
  }

  @Test
  public void badSdkVersionDevice_throws() throws Exception {
    Path apksFile =
        createApksArchiveFile(
            createSimpleTableOfContent(Paths.get("base-master.apk")),
            tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec =
        mergeSpecs(sdkVersion(1), density(480), abis("x86_64", "x86"), locales("en-US"));
    FakeDevice fakeDevice = FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, deviceSpec);
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .build();

    Throwable exception = assertThrows(IllegalStateException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("Error retrieving device SDK version");
  }

  @Test
  public void badDensityDevice_throws() throws Exception {
    Path apksFile =
        createApksArchiveFile(
            createSimpleTableOfContent(Paths.get("base-master.apk")),
            tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec =
        mergeSpecs(sdkVersion(21), density(-1), abis("x86_64", "x86"), locales("en-US"));
    FakeDevice fakeDevice = FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, deviceSpec);
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .build();

    Throwable exception = assertThrows(IllegalStateException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("Error retrieving device density");
  }

  @Test
  public void badAbisDevice_throws() throws Exception {
    Path apksFile =
        createApksArchiveFile(
            createSimpleTableOfContent(Paths.get("base-master.apk")),
            tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = mergeSpecs(sdkVersion(21), density(480), abis(), locales("en-US"));
    FakeDevice fakeDevice = FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, deviceSpec);
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .build();

    Throwable exception = assertThrows(IllegalStateException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("Error retrieving device ABIs");
  }

  @Test
  public void installsOnlySpecifiedModules() throws Exception {
    BuildApksResult tableOfContent =
        BuildApksResult.newBuilder()
            .addVariant(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), Paths.get("base-master.apk"))),
                    createSplitApkSet(
                        "feature1",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), Paths.get("feature1-master.apk"))),
                    createSplitApkSet(
                        "feature2",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), Paths.get("feature2-master.apk")))))
            .build();
    Path apksFile = createApksArchiveFile(tableOfContent, tmpDir.resolve("bundle.apks"));

    List<Path> installedApks = new ArrayList<>();
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));
    fakeDevice.setInstallApksSideEffect((apks, reinstall) -> installedApks.addAll(apks));

    InstallApksCommand.builder()
        .setApksArchivePath(apksFile)
        .setAdbPath(adbPath)
        .setAdbServer(adbServer)
        .setModules(ImmutableSet.of("base", "feature2"))
        .build()
        .execute();

    assertThat(Lists.transform(installedApks, apkPath -> apkPath.getFileName().toString()))
        .containsExactly("base-master.apk", "feature1-master.apk", "feature2-master.apk");
  }

  @Test
  public void moduleDependencies_installDependency() throws Exception {
    BuildApksResult tableOfContent =
        BuildApksResult.newBuilder()
            .addVariant(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    createSplitApkSet(
                        /* moduleName= */ "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), Paths.get("base-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature1",
                        /* onDemand= */ true,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), Paths.get("feature1-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature2",
                        /* onDemand= */ true,
                        /* moduleDependencies= */ ImmutableList.of("feature1"),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), Paths.get("feature2-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature3",
                        /* onDemand= */ true,
                        /* moduleDependencies= */ ImmutableList.of("feature2"),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), Paths.get("feature3-master.apk")))))
            .build();
    Path apksFile = createApksArchiveFile(tableOfContent, tmpDir.resolve("bundle.apks"));

    List<Path> installedApks = new ArrayList<>();
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));
    fakeDevice.setInstallApksSideEffect((apks, reinstall) -> installedApks.addAll(apks));

    InstallApksCommand.builder()
        .setApksArchivePath(apksFile)
        .setAdbPath(adbPath)
        .setAdbServer(adbServer)
        .setModules(ImmutableSet.of("feature2"))
        .build()
        .execute();

    assertThat(Lists.transform(installedApks, apkPath -> apkPath.getFileName().toString()))
        .containsExactly("base-master.apk", "feature1-master.apk", "feature2-master.apk");
  }

  @Test
  public void moduleDependencies_diamondGraph() throws Exception {
    BuildApksResult tableOfContent =
        BuildApksResult.newBuilder()
            .addVariant(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    createSplitApkSet(
                        /* moduleName= */ "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), Paths.get("base-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature1",
                        /* onDemand= */ true,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), Paths.get("feature1-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature2",
                        /* onDemand= */ true,
                        /* moduleDependencies= */ ImmutableList.of("feature1"),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), Paths.get("feature2-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature3",
                        /* onDemand= */ true,
                        /* moduleDependencies= */ ImmutableList.of("feature1"),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), Paths.get("feature3-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature4",
                        /* onDemand= */ true,
                        /* moduleDependencies= */ ImmutableList.of("feature2", "feature3"),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), Paths.get("feature4-master.apk")))))
            .build();
    Path apksFile = createApksArchiveFile(tableOfContent, tmpDir.resolve("bundle.apks"));

    List<Path> installedApks = new ArrayList<>();
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));
    fakeDevice.setInstallApksSideEffect((apks, reinstall) -> installedApks.addAll(apks));

    InstallApksCommand.builder()
        .setApksArchivePath(apksFile)
        .setAdbPath(adbPath)
        .setAdbServer(adbServer)
        .setModules(ImmutableSet.of("feature4"))
        .build()
        .execute();

    assertThat(Lists.transform(installedApks, apkPath -> apkPath.getFileName().toString()))
        .containsExactly(
            "base-master.apk",
            "feature1-master.apk",
            "feature2-master.apk",
            "feature3-master.apk",
            "feature4-master.apk");
  }

  @Test
  public void printHelp_doesNotCrash() {
    GetDeviceSpecCommand.help();
  }

  private static AdbServer fakeServerOneDevice(DeviceSpec deviceSpec) {
    return new FakeAdbServer(
        /* hasInitialDeviceList= */ true,
        ImmutableList.of(FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, deviceSpec)));
  }

  /** Creates a table of content matching L+ devices. */
  private static BuildApksResult createLPlusTableOfContent(Path apkPath) {
    return BuildApksResult.newBuilder()
        .addVariant(
            createVariantForSingleSplitApk(
                variantSdkTargeting(sdkVersionFrom(21)),
                ApkTargeting.getDefaultInstance(),
                apkPath))
        .build();
  }

  /** Creates a table of content matching all devices to a given apkPath. */
  private static BuildApksResult createSimpleTableOfContent(Path apkPath) {
    return BuildApksResult.newBuilder()
        .addVariant(
            createVariantForSingleSplitApk(
                VariantTargeting.getDefaultInstance(), ApkTargeting.getDefaultInstance(), apkPath))
        .build();
  }
}
