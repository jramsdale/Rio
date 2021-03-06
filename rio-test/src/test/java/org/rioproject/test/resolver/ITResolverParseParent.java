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
package org.rioproject.test.resolver;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.rioproject.config.maven2.Repository;
import org.rioproject.resolver.ResolverException;
import org.rioproject.resources.util.FileUtils;
import org.rioproject.test.ProjectModuleResolver;
import org.sonatype.aether.installation.InstallationException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Test parsing pom with project.parent.version set
 */
public class ITResolverParseParent {
    @Test
    public void testVersionFromProjectParentVersion() throws IOException, ResolverException, InstallationException {
        Util.verifyAndInstall();
        ProjectModuleResolver r = new ProjectModuleResolver();
        String[] cp = r.getClassPathFor("org.rioproject.resolver.test.project:project-service:2.0");
        Assert.assertTrue("We should have 2 items in the classpath, we got: " + cp.length, cp.length == 2);
    }

    @After
    public void clean() {
        Util.cleanProjectFromRepository();
    }
}
