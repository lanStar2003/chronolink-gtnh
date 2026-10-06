package dev.chronolink.v2;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

/** Isolated native registry/container tests under the REAL LaunchClassLoader expected by FML.
 * No world, server, window, third-party mod lifecycle, EULA acceptance or fake external API classes.
 */
public final class ForgeTestBootstrap {
    public static void main(String[] args)throws Exception {
        if(!(ForgeTestBootstrap.class.getClassLoader() instanceof LaunchClassLoader)) {
            String[] paths=System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator));
            ArrayList<URL> urls=new ArrayList<URL>();for(String path:paths)urls.add(new File(path).toURI().toURL());
            Launch.classLoader=new LaunchClassLoader(urls.toArray(new URL[urls.size()]));
            Launch.blackboard=new HashMap<String,Object>();Launch.blackboard.put("fml.deobfuscatedEnvironment",Boolean.TRUE);
            Launch.minecraftHome=new File("build/native-contract-home");
            if(!Launch.minecraftHome.isDirectory()&&!Launch.minecraftHome.mkdirs())throw new IllegalStateException("Cannot create test directory");
            Thread.currentThread().setContextClassLoader(Launch.classLoader);
            try{Class.forName(ForgeTestBootstrap.class.getName(),true,Launch.classLoader).getMethod("main",String[].class).invoke(null,(Object)args);}
            catch(InvocationTargetException ex){Throwable cause=ex.getCause();if(cause instanceof Error)throw (Error)cause;if(cause instanceof Exception)throw (Exception)cause;throw ex;}
            return;
        }
        Properties version=new Properties();try(InputStream in=ForgeTestBootstrap.class.getClassLoader().getResourceAsStream("fmlversion.properties")){
            if(in==null)throw new IllegalStateException("Real FML version metadata missing");version.load(in);
        }
        String[] keys={"major.number","minor.number","revision.number","build.number","mcversion","mcpversion"};Object[] data=new Object[8];
        for(int i=0;i<keys.length;i++){data[i]=version.getProperty("fmlbuild."+keys[i]);if(data[i]==null)throw new IllegalStateException("Missing native FML property "+keys[i]);}
        data[6]=Launch.minecraftHome;data[7]=new ArrayList<String>();
        cpw.mods.fml.common.Loader.injectData(data);
        V2DataTests.main(args);
    }
}
