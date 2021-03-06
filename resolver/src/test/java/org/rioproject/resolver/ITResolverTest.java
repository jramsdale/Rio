/*
 * Copyright 2010 to the original author or authors.
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
package org.rioproject.resolver;

import org.junit.Assert;
import org.junit.Test;
import org.rioproject.config.maven2.Repository;
import org.rioproject.resources.util.FileUtils;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Test maven resolver
 */
public class ITResolverTest {
    @Test
    public void testJskPlatformPom() throws ResolverException {
        File testRepo;
        File saveOrigSettings = null;
        Throwable thrown = null;
        try {
            saveOrigSettings = Utils.saveM2Settings();
            Utils.writeLocalM2RepoSettings();
            Resolver r = ResolverHelper.getInstance();
            File pom = new File("src/test/resources/jsk-platform-2.1.pom");
            Assert.assertTrue(pom.exists());
            testRepo = Repository.getLocalRepository();
            if(testRepo.exists())
                FileUtils.remove(testRepo);
            //File jskPlatformDir = new File(testRepo, "net/jini/jsk-platform/2.1");
            //Assert.assertFalse(jskPlatformDir.exists());
            String[] classPath = r.getClassPathFor("net.jini:jsk-platform:2.1");
            Assert.assertTrue(classPath.length>0);
            File jskPlatformJar = new File(testRepo, "net/jini/jsk-platform/2.1/jsk-platform-2.1.jar");
            Assert.assertTrue(jskPlatformJar.exists());
            StringBuilder sb = new StringBuilder();
            for(String s : classPath) {
                if(sb.length()>0)
                    sb.append(",");
                sb.append(s);
            }
            Assert.assertEquals(jskPlatformJar.getAbsolutePath(), sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
            thrown = e;
        } finally {
            try {
                Assert.assertNotNull(saveOrigSettings);
                FileUtils.copy(saveOrigSettings, Utils.getM2Settings());
            } catch (IOException e) {
                e.printStackTrace();
            }
            Assert.assertNull(thrown);
        }
    }

    @Test
    public void testGroovyResolution() throws ResolverException {
        File testRepo;
        File saveOrigSettings = null;
        Throwable thrown = null;
        try {
            saveOrigSettings = Utils.saveM2Settings();
            Utils.writeLocalM2RepoSettings();
            testRepo = Repository.getLocalRepository();
            if(testRepo.exists())
                FileUtils.remove(testRepo);
            Resolver r = ResolverHelper.getInstance();
            URL loc = r.getLocation("org.codehaus.groovy:groovy-all:1.6.2", null);
            Assert.assertNotNull(loc);
            File groovyJar = new File(testRepo, "org/codehaus/groovy/groovy-all/1.6.2/groovy-all-1.6.2.jar");
            Assert.assertTrue(groovyJar.exists());
            List<String> cp = getClassPathFor("org.codehaus.groovy:groovy-all:1.6.2", r);
        } catch (IOException e) {
            e.printStackTrace();
            thrown = e;
        } finally {
            try {
                Assert.assertNotNull(saveOrigSettings);
                FileUtils.copy(saveOrigSettings, Utils.getM2Settings());
            } catch (IOException e) {
                e.printStackTrace();
            }
            Assert.assertNull(thrown);
        }
    }

    @Test
    public void testWithSettings() throws ResolverException {
        File testRepo;
        File saveOrigSettings = null;
        try {
            saveOrigSettings = Utils.saveM2Settings();
            Utils.writeLocalM2RepoSettings();
            testRepo = Repository.getLocalRepository();
            if(testRepo.exists())
                FileUtils.remove(testRepo);
            Resolver r = ResolverHelper.getInstance();
            List<String> cp = getClassPathFor("com.sun.jini:outrigger:dl:2.1", r);
            Assert.assertTrue(cp.size()==1);
           
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                Assert.assertNotNull(saveOrigSettings);
                FileUtils.copy(saveOrigSettings, Utils.getM2Settings());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private List<String> getClassPathFor(String artifact, Resolver r) throws ResolverException {
        String[] cp = r.getClassPathFor(artifact);
        List<String> jars = new ArrayList<String>();
        String codebase = System.getProperty("user.home")+
                          File.separator+".m2"+
                          File.separator+"repository"+
                          File.separator;
        for(String jar : cp) {
            jars.add(jar.substring(codebase.length()));
        }
        System.out.println("classpath ("+artifact+"): "+jars);
        return jars;
    }


}
