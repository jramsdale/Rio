/*
 * Copyright 2008 the original author or authors.
 * Copyright 2005 Sun Microsystems, Inc.
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
package org.rioproject.cybernode;

import net.jini.config.Configuration;
import org.rioproject.core.provision.ServiceStatementManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Environment class provides methods to query and setup the operational
 * environment required by the Cybernode
 *
 * @author Dennis Reedy
 */
public class Environment {
    /** Logger */
    static Logger logger = Logger.getLogger(CybernodeImpl.getConfigComponent());

    /*
     * Setup the default environment
     */
    static void setupDefaultEnvironment() throws IOException {
        String rioHomeDirectory = getRioHomeDirectory();
        File logDir = new File(rioHomeDirectory+"logs");
        checkAccess(logDir, false);
    }

    /**
     * Get the ServiceStatementManager
     * 
     * @param config The Configuration object 
     * 
     * @return A ServiceStatementManager based on the environment
     */
    static ServiceStatementManager getServiceStatementManager(Configuration config) {
        ServiceStatementManager defaultServiceStatementManager = 
            new TransientServiceStatementManager(config);
        ServiceStatementManager serviceStatementManager;
        try {
            serviceStatementManager = 
                (ServiceStatementManager)config.getEntry(
                                                  CybernodeImpl.getConfigComponent(), 
                                                  "serviceStatementManager", 
                                                  ServiceStatementManager.class, 
                                                  defaultServiceStatementManager,
                                                  config);
        } catch(Throwable t) {
            logger.log(Level.WARNING, 
                       "Exception getting ServiceStatementManager", 
                       t);
            serviceStatementManager = defaultServiceStatementManager;
        }

        if(logger.isLoggable(Level.FINE))
            logger.log(Level.FINE, 
                       "Using ServiceStatementManager : "+
                       serviceStatementManager.getClass().getName());
        return(serviceStatementManager);
    }

    /*
     * Setup the provisionRoot directory
     * 
     * @param provisionEnabled Whether provisioning has been enabled
     * @param config The Configuration object 
     * 
     * @return The root directory to provision software to
     */
    public static String setupProvisionRoot(boolean provisionEnabled, 
                                            Configuration config) throws IOException {
        String provisionRoot = getRioHomeDirectory()+
                                   "system"+
                                   File.separator+
                                   "external";
        try {
            provisionRoot = 
                (String)config.getEntry(CybernodeImpl.getConfigComponent(), 
                                        "provisionRoot", 
                                        String.class, 
                                        provisionRoot);
        } catch(Throwable t) {
            logger.log(Level.WARNING, "Exception getting provisionRoot", t);
        }
        if(provisionEnabled) {
            File provisionDir = new File(provisionRoot);
            checkAccess(provisionDir);
            provisionRoot = provisionDir.getCanonicalPath();
        }
        return(provisionRoot);
    }
    
    /*
     * Setup the native directories
     * 
     * @param config The Configuration object 
     * 
     * @return A space delimited String of directory names to load native 
     * libraries from
     */
    static String setupNativeLibraryDirectories(Configuration config) 
    throws IOException {
        String nativeLibPath = System.getProperty("RIO_NATIVE_DIR");
        if(nativeLibPath==null) {
            logger.config("The RIO_NATIVE_DIR system property has not " +
                          "been defined. The setup for native " +
                          "library directories and the creation of " +
                          "NativeLibrarySupport objects is skipped.");
            return null;
        }

        List<String> nativeDirs = new ArrayList<String>();
        nativeDirs.addAll(Arrays.asList(toStringArray(nativeLibPath)));
        try {
            String configuredNativeDirs =
                (String)config.getEntry(CybernodeImpl.getConfigComponent(),
                                          "nativeLibDirectory", 
                                          String.class,
                                          nativeLibPath);
            if(!configuredNativeDirs.equals(nativeLibPath)) {
                String[] dirs = toStringArray(configuredNativeDirs);
                for(String dir : dirs) {
                    if(!nativeDirs.contains(dir))
                        nativeDirs.add(dir);
                }
            }
        } catch(Throwable t) {
            logger.log(Level.WARNING,
                       "Exception getting configured nativeLibDirectories",
                       t);
        }
        StringBuffer buffer = new StringBuffer();
        String[] dirs = nativeDirs.toArray(new String[nativeDirs.size()]);
        for(int i=0; i<dirs.length; i++) {
            File nativeDirectory = new File(dirs[i]);
            if(i>0)
                buffer.append(" ");
            buffer.append(nativeDirectory.getCanonicalPath());
        }
        return(buffer.toString());
    }
    

    /**
     * Setup the recordRoot directory
     *
     * @param config The Configuration to use
     *
     * @return File object for the record root directory
     *
     * @throws IOException if there are errors accessing the file system
     */
    public static File setupRecordRoot(Configuration config) throws IOException {
        String recordDir =
                getRioHomeDirectory()+
                        "logs"+
                        File.separator+
                        "records";
        try {
            recordDir = (String)config.getEntry(CybernodeImpl.getConfigComponent(), 
                                                "recordDirectory", 
                                                String.class, 
                                                recordDir);
        } catch(Throwable t) {
            logger.log(Level.WARNING, "Exception getting recordDirectory", t);
        }
        File recordRoot = new File(recordDir);        
        checkAccess(recordRoot);
        return(recordRoot);
    }

    /**
     * Get (and if needed create) the Rio home directory. If the environment variable
     * <pre>org.rioproject.home</pre> is not set, the Rio home directory will default 
     * to the <pre>.rio</pre> directory in the user's home directory 
     * 
     * @return The path to the Rio home directory
     */
    static String getRioHomeDirectory() {
        String rioHome;
        if(System.getProperty("org.rioproject.home")!=null) {
            rioHome = System.getProperty("org.rioproject.home");
        } else {
            if(System.getProperty("RIO_HOME")!=null) {
                rioHome = System.getProperty("RIO_HOME");
            } else {
                rioHome = System.getenv("RIO_HOME");
            }
            if(rioHome==null)
                rioHome = System.getProperty("user.home")+File.separator+".rio";
        }
        System.setProperty("org.rioproject.home", rioHome);
        File rioPath = new File(rioHome);
        if(!rioPath.exists()) {
            if(rioPath.mkdir()) {
                if(logger.isLoggable(Level.FINE))
                    logger.log(Level.FINE, 
                               "Created home directory ["+rioHome+"]");
            }
        }
        if(!rioHome.endsWith(File.separator))
            rioHome = rioHome+File.separator;
        return(rioHome);
    }

    /**
     * Verify read and write access to a directory
     * 
     * @param directory Directory File object
     * 
     * @throws IOException if read or write access is not permitted
     */
    static void checkAccess(File directory) throws IOException {
        checkAccess(directory, true);        
    }
    
    /**
     * Verify read and write access to a directory
     * 
     * @param directory Directory File object
     * @param isWriteable Check if directory is writeable
     * 
     * @throws IOException if read or write access is not permitted
     */
    static void checkAccess(File directory, boolean isWriteable) throws IOException {
        if(!directory.exists()) {
            if(directory.mkdirs()) {
                if(logger.isLoggable(Level.FINE))
                    logger.log(Level.FINE, 
                               "Created directory "+
                               "["+directory.getCanonicalPath()+"]");
            } else {
                throw new IOException("Could not create directory " +
                                      "["+directory.getCanonicalPath()+"], " +
                                      "make sure you have the proper " +
                                      "permissions to create, read & write " +
                                      "to the file system");
            }
        }
        // Find if we can write to the record root directory
        if(!directory.canRead())
            throw new IOException("Cant read from : "+directory.getCanonicalPath());
        if(isWriteable) {
            if(!directory.canWrite())
                throw new IOException("Cant write to : "+directory.getCanonicalPath());
        }
    }

    static String[] toStringArray(String s) {
        StringTokenizer tok = new StringTokenizer(s, File.pathSeparator+" ");
        List<String> sList = new ArrayList<String>();
        while(tok.hasMoreTokens())
            sList.add(tok.nextToken());
        return sList.toArray(new String[sList.size()]);
    }
}
