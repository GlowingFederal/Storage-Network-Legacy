package mrriegel.storagenetwork.legacy;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

/** Forge registries require their native LaunchClassLoader even for headless checks. */
public final class TerminalRegressionLauncher {
    public static void main(String[] args) throws Throwable {
        String[] paths = System.getProperty("java.class.path").split(File.pathSeparator);
        URL[] urls = new URL[paths.length];
        for (int i = 0; i < paths.length; i++) urls[i] = new File(paths[i]).toURI().toURL();
        Launch.blackboard = new HashMap<String, Object>();
        Launch.classLoader = new LaunchClassLoader(urls);
        Thread.currentThread().setContextClassLoader(Launch.classLoader);
        String fml = args[0].equals("1.8.9") ? "net.minecraftforge.fml" : "cpw.mods.fml";
        Class<?> loader = Launch.classLoader.loadClass(fml + ".common.Loader");
        loader.getMethod("injectData", Object[].class).invoke(null, new Object[] {
            new Object[] {"0", "0", "0", "0", args[0], "terminal-regression", new File("."), new ArrayList<String>()}});
        try {
            Launch.classLoader.loadClass("mrriegel.storagenetwork.legacy.TerminalRegression").getMethod("main", String[].class).invoke(null, new Object[] {new String[0]});
        } catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
}
