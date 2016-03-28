/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */
package com.skcraft.launcher.launch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.skcraft.concurrency.DefaultProgress;
import com.skcraft.concurrency.ProgressObservable;
import com.skcraft.launcher.*;
import static com.skcraft.launcher.LauncherUtils.checkInterrupted;
import com.skcraft.launcher.auth.Session;
import com.skcraft.launcher.install.ZipExtract;
import com.skcraft.launcher.model.minecraft.AssetsIndex;
import com.skcraft.launcher.model.minecraft.Library;
import com.skcraft.launcher.model.minecraft.VersionManifest;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.util.Environment;
import com.skcraft.launcher.util.Platform;
import com.skcraft.launcher.util.SharedLocale;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.java.Log;
import nz.co.lolnet.james137137.LauncherGobalSettings;
import nz.co.lolnet.james137137.MemoryChecker;
import nz.co.lolnet.statistics.ThreadLaunchedModpack;
import org.apache.commons.lang.text.StrSubstitutor;
import org.json.simple.parser.ParseException;

/**
 * Handles the launching of an instance.
 */
@Log
public class Runner implements Callable<Process>, ProgressObservable {

    private ProgressObservable progress = new DefaultProgress(0, SharedLocale.tr("runner.preparing"));

    private final ObjectMapper mapper = new ObjectMapper();
    private final Launcher launcher;
    private final Instance instance;
    private final Session session;
    private final File extractDir;
    @Getter
    @Setter
    private Environment environment = Environment.getInstance();

    private VersionManifest versionManifest;
    private AssetsIndex assetsIndex;
    private File virtualAssetsDir;
    private Configuration config;
    private JavaProcessBuilder builder;
    private AssetsRoot assetsRoot;

    /**
     * Create a new instance launcher.
     *
     * @param launcher the launcher
     * @param instance the instance
     * @param session the session
     * @param extractDir the directory to extract to
     */
    public Runner(@NonNull Launcher launcher, @NonNull Instance instance,
            @NonNull Session session, @NonNull File extractDir) {
        this.launcher = launcher;
        this.instance = instance;
        this.session = session;
        this.extractDir = extractDir;
    }

    /**
     * Get the path to the JAR.
     *
     * @return the JAR path
     */
    private File getJarPath() {
        File jarPath = instance.getCustomJarPath();
        if (!jarPath.exists()) {
            jarPath = launcher.getJarPath(versionManifest);
        }
        return jarPath;
    }

    @Override
    public Process call() throws Exception {
        if (!instance.isInstalled()) {
            throw new LauncherException("Update required", SharedLocale.tr("runner.updateRequired"));
        }

        config = launcher.getConfig();
        builder = new JavaProcessBuilder();
        assetsRoot = launcher.getAssets();

        // Load manifiests
        versionManifest = mapper.readValue(instance.getVersionPath(), VersionManifest.class);

        // Load assets index
        File assetsFile = assetsRoot.getIndexPath(versionManifest);
        try {
            assetsIndex = mapper.readValue(assetsFile, AssetsIndex.class);
        } catch (FileNotFoundException e) {
            instance.setInstalled(false);
            Persistence.commitAndForget(instance);
            throw new LauncherException("Missing assets index " + assetsFile.getAbsolutePath(),
                    SharedLocale.tr("runner.missingAssetsIndex", instance.getTitle(), assetsFile.getAbsolutePath()));
        } catch (IOException e) {
            instance.setInstalled(false);
            Persistence.commitAndForget(instance);
            throw new LauncherException("Corrupt assets index " + assetsFile.getAbsolutePath(),
                    SharedLocale.tr("runner.corruptAssetsIndex", instance.getTitle(), assetsFile.getAbsolutePath()));
        }

        // Copy over assets to the tree
        try {
            AssetsRoot.AssetsTreeBuilder assetsBuilder = assetsRoot.createAssetsBuilder(versionManifest);
            progress = assetsBuilder;
            virtualAssetsDir = assetsBuilder.build();
        } catch (LauncherException e) {
            instance.setInstalled(false);
            Persistence.commitAndForget(instance);
            throw e;
        }

        progress = new DefaultProgress(0.9, SharedLocale.tr("runner.collectingArgs"));

        addJvmArgs();
        addLibraries();
        addJarArgs();
        addWindowArgs();
        addPlatformArgs();
        
        String launchCount = LauncherGobalSettings.get("InstanceLaunchCount_" + instance.getTitle());
        if (launchCount.length() == 0)
        {
            launchCount = Integer.toString(1);
        }
        else
        {
            launchCount = Integer.toString(Integer.parseInt(launchCount) + 1);
        }
        LauncherGobalSettings.put("InstanceLaunchCount_" + instance.getTitle(),launchCount);
        builder.classPath(getJarPath());
        builder.setMainClass(versionManifest.getMainClass());

        callLaunchModifier();

        ProcessBuilder processBuilder = new ProcessBuilder(builder.buildCommand());
        processBuilder.directory(instance.getContentDir());
        Runner.log.info("Launching: " + builder);
        checkInterrupted();

        progress = new DefaultProgress(1, SharedLocale.tr("runner.startingJava"));

        //cptwin, launcher statistics
        new ThreadLaunchedModpack(instance.getTitle());

        return processBuilder.start();
    }

    /**
     * Call the manifest launch modifier.
     */
    private void callLaunchModifier() {
        instance.modify(builder);
    }

    /**
     * Add platform-specific arguments.
     */
    private void addPlatformArgs() {
        // Mac OS X arguments
        if (getEnvironment().getPlatform() == Platform.MAC_OS_X) {
            File icnsPath = assetsIndex.getObjectPath(assetsRoot, "icons/minecraft.icns");
            if (icnsPath != null) {
                builder.getFlags().add("-Xdock:icon=" + icnsPath.getAbsolutePath());
                builder.getFlags().add("-Xdock:name=Minecraft");
            }
        }

        // Windows arguments
        if (getEnvironment().getPlatform() == Platform.WINDOWS) {
            builder.getFlags().add("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump");
        }
    }

    /**
     * Add libraries.
     */
    private void addLibraries() throws LauncherException {
        // Add libraries to classpath or extract the libraries as necessary
        LinkedHashSet<Library> libraries = versionManifest.getLibraries();
        for (Library library : libraries) {
            if (!library.matches(environment)) {
                continue;
            }

            File path = new File(launcher.getLibrariesDir(), library.getPath(environment));

            if (path.exists()) {
                Library.Extract extract = library.getExtract();
                if (extract != null) {
                    ZipExtract zipExtract = new ZipExtract(Files.asByteSource(path), extractDir);
                    zipExtract.setExclude(extract.getExclude());
                    zipExtract.run();
                } else {
                    builder.classPath(path);
                }
            } else {
                instance.setInstalled(false);
                Persistence.commitAndForget(instance);
                throw new LauncherException("Missing library " + library.getName(),
                        SharedLocale.tr("runner.missingLibrary", instance.getTitle(), library.getName()));
            }
        }

        builder.getFlags().add("-Djava.library.path=" + extractDir.getAbsoluteFile());
    }

    /**
     * Add JVM arguments.
     *
     * @throws IOException on I/O error
     */
    private void addJvmArgs() throws IOException {
        try {
            MemoryChecker.getMemoryInfoFromServer();
        } catch (ParseException ex) {
            Logger.getLogger(Runner.class.getName()).log(Level.SEVERE, null, ex);
        }
        int minMemory = config.getMinMemory();
        int maxMemory = config.getMaxMemory();
        int permGen = config.getPermGen();

        if (minMemory <= 0) {
            minMemory = 1024;
        }

        if (maxMemory <= 0) {
            maxMemory = 1024;
        }

        if (permGen <= 0) {
            permGen = 128;
        }

        if (permGen <= 64) {
            permGen = 64;
        }

        minMemory = MemoryChecker.checkMinMemory(minMemory, instance);
        maxMemory = MemoryChecker.checkMaxMemory(maxMemory, instance);
        permGen = MemoryChecker.checkpermGen(permGen, instance);

        int currentfreeMemory = getCurrentFreeMemory();
        int memoryMB = (int) (currentfreeMemory / (1024 * 1024));
        if (currentfreeMemory > 0 && memoryMB < minMemory) {
            memoryMB = ((memoryMB / 2) / 256) * 256;
            minMemory = memoryMB;
            if (memoryMB <= 512) {
                minMemory = 128;
            }
        }

        if (minMemory > maxMemory) {
            maxMemory = minMemory;
        }

        builder.setMinMemory(minMemory);
        builder.setMaxMemory(maxMemory);
        builder.setPermGen(permGen);

        String rawJvmPath = config.getJvmPath();
        if (!Strings.isNullOrEmpty(rawJvmPath)) {
            builder.tryJvmPath(new File(rawJvmPath));
        }

        String rawJvmArgs = config.getJvmArgs();
        if (!Strings.isNullOrEmpty(rawJvmArgs)) {
            List<String> flags = builder.getFlags();

            for (String arg : JavaProcessBuilder.splitArgs(rawJvmArgs)) {
                flags.add(arg);
            }
        }
    }

    private int getCurrentFreeMemory() {
        long currnetAmmount = -1;
        OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        for (Method method : operatingSystemMXBean.getClass().getDeclaredMethods()) {
            method.setAccessible(true);
            if (method.getName().startsWith("getFreePhysicalMemorySize")
                    && Modifier.isPublic(method.getModifiers())) {

                try {
                    currnetAmmount = (Long) method.invoke(operatingSystemMXBean);
                } catch (Exception e) {
                }
            } // if
        } // for

        int memoryMB = (int) (currnetAmmount / (1024 * 1024));
        return memoryMB;
    }

    /**
     * Add arguments for the application.
     *
     * @throws JsonProcessingException on error
     */
    private void addJarArgs() throws JsonProcessingException {
        List<String> args = builder.getArgs();

        String[] rawArgs = versionManifest.getMinecraftArguments().split(" +");
        StrSubstitutor substitutor = new StrSubstitutor(getCommandSubstitutions());
        for (String arg : rawArgs) {
            args.add(substitutor.replace(arg));
        }
    }

    /**
     * Add window arguments.
     */
    private void addWindowArgs() {
        List<String> args = builder.getArgs();
        int width = config.getWindowWidth();
        int height = config.getWidowHeight();

        if (width >= 10) {
            args.add("--width");
            args.add(String.valueOf(width));
            args.add("--height");
            args.add(String.valueOf(height));
        }
    }

    /**
     * Build the list of command substitutions.
     *
     * @return the map of substitutions
     * @throws JsonProcessingException on error
     */
    private Map<String, String> getCommandSubstitutions() throws JsonProcessingException {
        Map<String, String> map = new HashMap<String, String>();

        map.put("version_name", versionManifest.getId());

        map.put("auth_access_token", session.getAccessToken());
        map.put("auth_session", session.getSessionToken());
        map.put("auth_player_name", session.getName());
        map.put("auth_uuid", session.getUuid());

        map.put("profile_name", session.getName());
        map.put("user_type", session.getUserType().getName());
        map.put("user_properties", mapper.writeValueAsString(session.getUserProperties()));

        map.put("game_directory", instance.getContentDir().getAbsolutePath());
        map.put("game_assets", virtualAssetsDir.getAbsolutePath());
        map.put("assets_root", launcher.getAssets().getDir().getAbsolutePath());
        map.put("assets_index_name", versionManifest.getAssetsIndex());

        return map;
    }

    @Override
    public double getProgress() {
        return progress.getProgress();
    }

    @Override
    public String getStatus() {
        return progress.getStatus();
    }

}
