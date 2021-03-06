/*
 * Copyright 2009 the original author or authors.
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
package org.rioproject.test

import java.util.logging.Logger
import net.jini.config.Configuration
import net.jini.core.entry.Entry
import net.jini.core.lookup.ServiceItem
import net.jini.core.lookup.ServiceRegistrar
import net.jini.core.lookup.ServiceTemplate
import net.jini.discovery.DiscoveryManagement
import net.jini.lease.LeaseRenewalManager
import net.jini.lookup.ServiceDiscoveryManager
import net.jini.lookup.entry.Name
import org.junit.Assert
import org.rioproject.config.Constants
import org.rioproject.config.GroovyConfig
import org.rioproject.core.OperationalString
import org.rioproject.core.OperationalStringManager
import org.rioproject.core.ServiceElement
import org.rioproject.core.provision.ServiceBeanInstantiator
import org.rioproject.cybernode.Cybernode
import org.rioproject.exec.JVMOptionChecker
import org.rioproject.exec.Util
import org.rioproject.monitor.DeployAdmin
import org.rioproject.monitor.ProvisionMonitor
import org.rioproject.opstring.OpStringLoader
import org.rioproject.opstring.OpStringManagerProxy
import org.rioproject.resources.client.DiscoveryManagementPool
import org.rioproject.resources.client.JiniClient
import org.rioproject.resources.util.PropertyHelper
import org.rioproject.tools.cli.CLI.StopHandler
import org.rioproject.tools.harvest.HarvesterAgent
import org.rioproject.tools.harvest.HarvesterBean
import org.rioproject.tools.webster.Webster
import org.rioproject.core.OperationalStringException
import org.rioproject.resolver.ResolverHelper
import org.rioproject.resolver.Artifact

/**
 * Simplifies the running of core Rio services
 */
class TestManager {
    static final String TEST_HOSTS = 'org.rioproject.test.hosts'
    static Logger logger = Logger.getLogger(TestManager.class.getPackage().name);
    List<Webster> websters = new ArrayList<Webster>()
    List<Process> processes = new ArrayList<Process>()
    JiniClient client
    String rioHome
    String groups
    String opStringToDeploy
    OperationalStringManager deployedOperationalStringManager
    ServiceDiscoveryManager serviceDiscoveryManager
    List<String> hostList = new ArrayList<String>()
    def config
    boolean createShutdownHook
    TestConfig testConfig

    /**
     * Create a TestManager without installing a shutdown hook
     */
    TestManager() {
        this(false)
    }

    /**
     * Create a TestManager
     *
     * @param createShutdownHook If true create a shutdown hook. When the JVM
     * is exiting, any service started by the TestManager will be shutdown.
     */
    TestManager(boolean createShutdownHook) {
        this.createShutdownHook = createShutdownHook
    }

    /**
     * Initialize the TestManager
     *
     * @param testConfig The configuration used to initialize
     *
     * @throws IllegalArgumentException if the testConfig is null
     */
    public void init(TestConfig testConfig) {
        if(testConfig==null)
            throw new IllegalArgumentException("testConfig cannot be null")
        this.testConfig = testConfig
        config = loadManagerConfig()
        if(config.manager.cleanLogs) {
            File logs = getCreatedLogsFile(testConfig.component)
            if(logs.exists()) {
                logs.eachLine { line ->
                    File f = new File((String)line)
                    if(f.exists())
                        f.delete()

                }
                logs.delete()
            }
        }
        rioHome = System.getProperty('RIO_HOME')
        if(rioHome==null)
            throw new IllegalStateException('The RIO_HOME system property must be set')
        groups = System.getProperty(Constants.GROUPS_PROPERTY_NAME)
        if(groups==null)
            throw new IllegalStateException("The ${Constants.GROUPS_PROPERTY_NAME} system "+
                                            "property must be set")
        logger.info "Using [${groups}] group for discovery"

        DiscoveryManagementPool discoPool = DiscoveryManagementPool.getInstance();
        if(config.manager.config) {
            def mgrConfig = [PropertyHelper.expandProperties(config.manager.config)]
            Configuration conf = new GroovyConfig((String[])mgrConfig, null)
            discoPool.setConfiguration(conf)
        }

        DiscoveryManagement dMgr = discoPool.getDiscoveryManager(null);
        serviceDiscoveryManager =
            new ServiceDiscoveryManager(dMgr, new LeaseRenewalManager())
        //String hosts = System.getProperty(TEST_HOSTS, '')

        if(createShutdownHook) {
            Runtime rt = Runtime.getRuntime();
            logger.info ":: Adding shutdown hook"

            Closure cl = {
                logger.info ":: Running shutdown hook, stop Rio services..."
                shutdown()
            }
            Thread shutdownHook = new Thread(cl, "RunPostShutdownHook")
            rt.addShutdownHook(shutdownHook)
        }

        startConfiguredServices()
    }

    private void startConfiguredServices() {
        int lookupCount = testConfig.getNumLookups()-countLookups();
        for(int i=0; i<lookupCount; i++)
            startReggie();

        int monitorCount = testConfig.getNumMonitors()-countMonitors();
        if(monitorCount>0) {
            for(int i=0; i<monitorCount; i++)
                startProvisionMonitor();
            /*
            * Need to get an instance of DiscoveryManagement and set
            * it to the OpStringManagerProxy utility in order to
            * discover ProvisionManager instances.
            *
            * This is required if test clients use association
            * strategies, specifically the utilization strategy
            */
            OpStringManagerProxy.setDiscoveryManagement(
                getServiceDiscoveryManager().getDiscoveryManager());
        }

        int cybernodeCount = testConfig.getNumCybernodes()-countCybernodes();
        for(int i=0; i<cybernodeCount; i++)
            startCybernode();

        postInit();

        if(testConfig.getOpString()!=null) {
            setOpStringToDeploy(testConfig.getOpString());
            if(testConfig.autoDeploy()) {
                OperationalStringManager mgr = deploy();
                setDeployedOperationalStringManager(mgr);
            }
        }
    }

    /**
     * Lifecycle notification by RioTestRunner. Called after RioTestRunner has
     * asked the TestManager to start Lookups, Monitors and Cybernodes. Only
     * called once and guaranteed to be called after init().
     *
     * This implementation is empty, utilities that extend the TestManager may
     * choose to provide specific behavior.
     */
    def postInit() {
    }

    /**
     * Starts a Cybernode using the <tt>config/start-cybernode.groovy</tt>
     * start configuration.
     *
     * @return The started Cybernode
     */
    Cybernode startCybernode() {
        def starter = config.manager.cybernodeStarter
        Cybernode cybernode = null
        if(starterConfigOk(starter)) {
            String cybernodeStarter = "${PropertyHelper.expandProperties(starter)}"
            exec(cybernodeStarter)
            cybernode =  (Cybernode)waitForService(Cybernode.class)
        } else {
            println "[ERROR]: No cybernodeStarter declared in test-config"
        }
        return cybernode
    }

    /**
     * Starts a Cybernode using the starter configuration obtain from the TestConfig.
     *
     * @param hostIndex The index of the host to start on
     *
     * @return The started Cybernode
     */
    Cybernode startCybernode(int hostIndex) {
        def starter = config.manager.cybernodeStarter
        Cybernode cybernode = null
        if(starterConfigOk(starter)) {
            String cybernodeStarter = "${PropertyHelper.expandProperties(starter)}"
            exec(cybernodeStarter)
            cybernode =  (Cybernode)waitForService(Cybernode.class)
        } else {
            println "[ERROR]: No cybernodeStarter declared in test-config"
        }
        return cybernode
    }

    /**
     * Starts a ProvisionMonitor using the starter configuration obtain from the TestConfig.
     *
     * @return The started ProvisionMonitor
     */
    ProvisionMonitor startProvisionMonitor() {
        def starter = config.manager.monitorStarter
        ProvisionMonitor monitor = null
        if(starterConfigOk(starter)) {
            String monitorStarter = "${PropertyHelper.expandProperties(starter)}"
            exec(monitorStarter)
            monitor = (ProvisionMonitor)waitForService(ProvisionMonitor.class)
        } else {
            println "[ERROR]: No monitorStarter declared in test-config"
        }
        return monitor
    }

    /**
     * Starts a ProvisionMonitor using the starter configuration obtain from the TestConfig.
     *
     * @param hostIndex The index of the host to start on
     *
     * @return The started ProvisionMonitor
     */
    ProvisionMonitor startProvisionMonitor(int hostIndex) {
        def starter = config.manager.monitorStarter
        ProvisionMonitor monitor = null
        if(starterConfigOk(starter)) {
            String monitorStarter = "${PropertyHelper.expandProperties(starter)}"
            exec(monitorStarter)
            monitor = (ProvisionMonitor)waitForService(ProvisionMonitor.class)
        } else {
            println "[ERROR]: No monitorStarter declared in test-config"
        }
        return monitor
    }

    /**
     * Starts a Webster.
     *
     * @param port The port to use
     * @param dirs Directories to serve
     *
     * @return The started Webster
     */
    Webster startWebster(int port, String dirs) {
        Webster webster = new Webster(port, dirs);
        websters.add(webster);
        return webster;
    }

    /**
     * Starts a ServiceRegistrar using the starter configuration obtain from the TestConfig.
     *
     * @return The started ServiceRegistrar
     */
    ServiceRegistrar startReggie() {
        def starter = config.manager.reggieStarter
        ServiceRegistrar reggie = null
        if(starterConfigOk(starter)) {
            String reggieStarter = "${PropertyHelper.expandProperties(starter)}"
            exec(reggieStarter)
            reggie = (ServiceRegistrar)waitForService(ServiceRegistrar.class)
        } else {
            println "[ERROR]: No reggieStarter declared in test-config"
        }
        return reggie
    }

    /**
     * Deploys the configured OperationalString
     *
     * @return The OperationalStringManager that is managing the OperationalString
     *
     * @throws OperationalStringException if there are problems deploying
     */
    OperationalStringManager deploy() {
        Assert.assertNotNull "The OperationalString to deploy has not been "+
                             "set. Check to make sure a corresponding test "+
                             "configuration exists for the class being tested "+
                             "and set the <test-class-name>.opstring property",
                             opStringToDeploy
        URL opStringURL
        if(Artifact.isArtifact(opStringToDeploy)) {
            opStringURL = ResolverHelper.getInstance().getLocation(opStringToDeploy, "oar")
            if(opStringURL==null)
                throw new OperationalStringException("Artifact "+opStringToDeploy+" not resolvable");
        } else {
            opStringURL = new File(opStringToDeploy).toURI().toURL()
        }
        return deploy(opStringURL)
    }

    /**
     * Deploys an OperationalString
     *
     * @param opstring The OperationalString file object
     *
     * @return The OperationalStringManager that is managing the OperationalString
     */
    OperationalStringManager deploy(File opstring) {
        return deploy(opstring.toURL())
    }

    /**
     * Deploys an OperationalString
     *
     * @param opstring The OperationalString file object
     * @param monitor The ProvisionMonitor to deploy to
     *
     * @return The OperationalStringManager that is managing the OperationalString
     */
    OperationalStringManager deploy(File opstring, ProvisionMonitor monitor) {
        return deploy(opstring.toURL(), monitor)
    }

    /**
     * Deploys an OperationalString
     *
     * @param opstring The URL pointing to the OperationalString
     *
     * @return The OperationalStringManager that is managing the OperationalString
     */
    OperationalStringManager deploy(URL opstring) {
        ProvisionMonitor monitor =
        (ProvisionMonitor)waitForService(ProvisionMonitor.class)
        return deploy(opstring, monitor)
    }

    /**
     * Deploys an OperationalString
     *
     * @param opstring The URL pointing to the OperationalString
     * @param monitor The ProvisionMonitor to deploy to
     *
     * @return The OperationalStringManager that is managing the OperationalString
     */
    OperationalStringManager deploy(URL opstring, ProvisionMonitor monitor) {
        OpStringLoader loader = new OpStringLoader(getClass().classLoader)
        OperationalString[] opstrings = loader.parseOperationalString(opstring)
        return deploy(opstrings[0], monitor)        
    }

    /**
     * Deploys an OperationalString
     *
     * @param opstring The OperationalString object to deploy
     * @param monitor The ProvisionMonitor to deploy to
     *
     * @return The OperationalStringManager that is managing the OperationalString
     */
    OperationalStringManager deploy(OperationalString opstring, ProvisionMonitor monitor) {
        DeployAdmin dAdmin = (DeployAdmin)monitor.admin
        dAdmin.deploy(opstring)
        OperationalStringManager mgr = dAdmin.getOperationalStringManager(opstring.name)
        return mgr
    }

    /**
     * Undeploys an OperationalString
     *
     * @param name The name of a deployed OperationalString
     */
    def undeploy(String name) {
        ServiceItem[] items = getServiceItems(ProvisionMonitor.class)
        if(items.length==0) {
            println "No ProvisionMonitor instances discovered, cannot undeploy ${name}"
            return
        }
        undeploy(name, (ProvisionMonitor)items[0].service)
    }

    /**
     * Undeploys an OperationalString
     *
     * @param name The name of a deployed OperationalString
     * @param monitor The ProvisionMonitor instance to perform the undeployment
     */
    def undeploy(String name, ProvisionMonitor monitor) {
        if(monitor!=null) {
            DeployAdmin dAdmin = (DeployAdmin)monitor.admin
            dAdmin.undeploy(name)
        } else {
            println "Cannot undeploy ${name}, ProvisionMonitor provided is null"
        }
    }

    /**
     * Undeploy all deployed OperationalString
     *
     * @param monitor The ProvisionMonitor instance to perform the undeployment
     */
    def undeployAll(ProvisionMonitor monitor) {
        DeployAdmin deployAdmin = (DeployAdmin) monitor.getAdmin();
        OperationalStringManager[] opStringMgrs =
                deployAdmin.getOperationalStringManagers();
        for (OperationalStringManager mgr : opStringMgrs) {
            String opStringName = mgr.getOperationalString().name
            deployAdmin.undeploy(opStringName);
        }
    }

    /**
     * Get the OperationalStringManager for an OperationalString that was configured to be autoDeployed
     *
     * @return The OperationalStringManager for the automatically deployed OperationalString
     */
    OperationalStringManager getOperationalStringManager() {
        Assert.assertNotNull "The deployed OperationalStringManager has not "+
                             "been set. This is most likely due to not having "+
                             "the declared opstring in your test case "+
                             "configuration set with autoDeploy=true",
                              deployedOperationalStringManager

        ProvisionMonitor monitor = (ProvisionMonitor)waitForService(ProvisionMonitor.class)
        DeployAdmin dAdmin = (DeployAdmin)monitor.admin
        String name = deployedOperationalStringManager.getOperationalString().getName()
        return dAdmin.getOperationalStringManager(name)
    }

    /**
     * Get the OperationalStringManager for a deployment
     *
     * @name The name of the deployment
     *
     * @return The OperationalStringManager for the deployment
     */
    OperationalStringManager getOperationalStringManager(String name) {
        ProvisionMonitor monitor = (ProvisionMonitor)waitForService(ProvisionMonitor.class)
        DeployAdmin dAdmin = (DeployAdmin)monitor.admin
        return dAdmin.getOperationalStringManager(name)
    }

    /**
     * Stop a Cybernode
     *
     * @param service The Cybernode service proxy
     */
    def stopCybernode(service) {
        stopService(service, "Cybernode")
    }

    /**
     * Stop a ProvisionMonitor
     *
     * @param service The ProvisionMonitor service proxy
     */
    def stopProvisionMonitor(service) {
        stopService(service, "Monitor") 
    }

    /**
     * Stop a service
     *
     * @param service The service proxy
     * @param name The name of the service to stop
     */
    def stopService(service, String name) {
        if(service==null)
            throw new IllegalArgumentException("service proxy is null for ${name}")

        StopHandler stopHandler = new StopHandler();
        stopHandler.destroyService(service, name, System.out)
    }

    /**
     * Shutdown all started services
     */
    def shutdown() {

        for(Webster w : websters)
            w.terminate();
        /* Make sure all services are terminated */
        for(Process p : processes) {
            p.destroy()
        }
    }

    /**
     * Determine if the Harvester needs to run. This is based on whether the test configuration
     * indicates that harvesting should occur
     */
    def maybeRunHarvester() {
        if(testConfig.runHarvester()) {
            ProvisionMonitor monitor
            ServiceItem[] items = getServiceItems(ProvisionMonitor.class)
            if(items.length==0) {
                println "===> No discovered ProvisionMonitor instances, "+
                        "cannot deploy HarvesterAgents"
                return
            }
            monitor = (ProvisionMonitor)items[0].service
            String opstring
            if(config.manager.harvesterOpString)
                opstring = "${PropertyHelper.expandProperties(config.manager.harvesterOpString)}"
            else
                opstring = "${rioHome}/src/test/resources/harvester.groovy"
            URL opStringUrl
            try {
                opStringUrl = new URL(opstring)
            } catch (MalformedURLException e) {
                File opstringFile = new File(opstring)
                if(!opstringFile.exists())
                    println "Cannot load [${opstringFile}], "+
                            "Unable to deploy Harvester support."
                opStringUrl = opstringFile.toURI().toURL()
            }
            OpStringLoader loader = new OpStringLoader(getClass().classLoader)
            OperationalString[] opstrings = loader.parseOperationalString(opStringUrl)
            Assert.assertEquals "Expected only 1 OperationalString", 1, opstrings.length
            for(ServiceElement elem : opstrings[0].services)
                elem.getServiceBeanConfig().addInitParameter(HarvesterAgent.PREFIX,
                                                             testConfig.getComponent())            
            deploy(opstrings[0], monitor)
            /* Count the number of physical machines*/
            List<String> hosts = new ArrayList<String>()
            for(ServiceBeanInstantiator sbi : monitor.getServiceBeanInstantiators()) {
                String s = sbi.getInetAddress().toString()
                if(!hosts.contains(s))
                    hosts.add(s)
            }
            def h = getHarvester(serviceDiscoveryManager.discoveryManager)
            h.harvestDir = "${PropertyHelper.expandProperties(config.manager.harvestDir)}"
            println "Harvester:: Number of physical machines = ${hosts.size()}"
            long timeout = 1000*60
            long duration = 0
            while(h.agentsHandledCount<hosts.size()) {
                Thread.sleep(1000)
                duration += 1000
                if(duration >= timeout)
                    break
            }
            println "Number of HarvesterAgents handled = ${h.agentsHandledCount}"
            h.unadvertise()
        }
    }

    /**
     * Get a local Harvester instance
     *
     * @param dMgr The DiscoveryManagement to use
     *
     * @return A {@link org.rioproject.tools.harvest.Harvester} instance
     */
    def getHarvester(DiscoveryManagement dMgr) {
        return new HarvesterBean(dMgr)
    }
    
    private void exec(String starter) {
        String classpath = "${PropertyHelper.expandProperties(config.manager.execClassPath)}"
        String jvmOptions = "${PropertyHelper.expandProperties(config.manager.jvmOptions)}"
        if(config.manager.inheritOptions)
            jvmOptions = JVMOptionChecker.getJVMInputArgs(jvmOptions)
        jvmOptions = jvmOptions+' -D'+Constants.RIO_TEST_EXEC_DIR+'='+System.getProperty("user.dir")
        String logFile = null
        String mainClass = "${config.manager.mainClass}"
        if(config.manager.log.size()>0) {
            String service = starter.substring(starter.lastIndexOf("-")+1)
            service = service.substring(0, service.indexOf("."))
            logFile = Util.replace("${config.manager.log}", '${service}', service)
            logFile = "${PropertyHelper.expandProperties(logFile)}"
            File f = new File(logFile)
            if(f.exists()) {
                int i=1
                /* Get extension */
                String ext = f.getName()
                ext = ext.substring(ext.lastIndexOf("."))
                File dir = f.getParentFile()
                while(f.exists()) {
                    f = new File(dir, service+"-${i++}$ext")
                }
                logFile = f.canonicalPath
            } else {
                File parent = f.getParentFile()
                if(!parent.exists())
                    parent.mkdirs()
            }
            File createdLogsFile = getAndCreateCreatedLogsFile(testConfig.component)
            createdLogsFile.append(logFile+'\n')
            logger.info "Output will be sent to [${logFile}]"
        }                

        String cmdLine = getJava()+' '+jvmOptions+' -cp '+classpath+' '+mainClass+' '+starter

        //if(logger.isLoggable(Level.FINE))
            logger.info "Exec command line: ${cmdLine}"
        //else
        //    logger.info "Exec starter ${starter}"
        Process process = Runtime.runtime.exec(cmdLine)
        if(logFile) {
            def fos= new FileOutputStream(logFile)
            process.consumeProcessOutputStream(fos)
            process.consumeProcessErrorStream(fos)
        } else {
            process.consumeProcessOutputStream(System.out)
            process.consumeProcessErrorStream(System.err)
        }
        processes.add(process)
    }

    /**
     * Wait for a deployment to complete. This means to wait for all services
     * declared to activate and join the network
     *
     * @param mgr The OperationalStringManager to connect with to
     * wait for the deployment to complete
     *
     * @throws TimeoutException if the time waiting for the deployment exceeds
     * {@link ServiceMonitor#MAX_TIMEOUT}
     */
    public void waitForDeployment(OperationalStringManager mgr) {
        OperationalString opstring  = mgr.getOperationalString()
        Map<ServiceElement, Integer> deploy = new HashMap<ServiceElement, Integer>()
        int total = 0
        for (ServiceElement elem: opstring.services) {
            deploy.put(elem, 0)
            total += elem.planned
        }
        int deployed = 0
        long sleptFor = 0
        while (deployed < total && sleptFor<ServiceMonitor.MAX_TIMEOUT) {
            deployed = 0
            for (Map.Entry<ServiceElement, Integer> entry: deploy.entrySet()) {
                int numDeployed = entry.value
                ServiceElement elem = entry.key
                if (numDeployed < elem.planned) {
                    logger.info "Waiting for service ${elem.name} to be deployed. " +
                                "Planned [${elem.planned}], deployed [${numDeployed}]"
                    numDeployed = mgr.getServiceBeanInstances(elem).length
                    deploy.put(elem, numDeployed)
                } else {
                    deployed += elem.planned
                    logger.info "Service ${elem.name} is deployed. " +
                                "Planned [${elem.planned}], deployed [${numDeployed}]"
                }
            }
            if(sleptFor==ServiceMonitor.MAX_TIMEOUT)
                break;
            if (deployed < total) {
                Thread.sleep(1000)
                sleptFor += 1000
            }
        }

        if(sleptFor>=ServiceMonitor.MAX_TIMEOUT && deployed < total)
            throw new TimeoutException("Timeout waiting for service to be deployed");
    }

    private int countLookups() {
        return countServices(ServiceRegistrar.class)
    }

    private int countMonitors() {
        return countServices(ProvisionMonitor.class)
    }

    private int countCybernodes() {
        return countServices(Cybernode.class)
    }

    private int countServices(Class serviceInterface) {
        long t0 = System.currentTimeMillis()
        ServiceItem[] items = getServiceItems(serviceInterface)
        logger.info "Discovered $items.length instances of ${serviceInterface.name}, "+
                    "elapsed time: ${(System.currentTimeMillis()-t0)} millis"
        return items.length
    }

    /**
     * Get all ServiceItems for a service
     *
     * @param type The service type
     *
     * @return An array of ServiceItem instances.
     */
    ServiceItem[] getServiceItems(Class type) {
        def classes = [type]
        ServiceTemplate template = new ServiceTemplate(null, (Class[])classes, null)
        ServiceItem[] items = serviceDiscoveryManager.lookup(template,
                                                             Integer.MAX_VALUE,
                                                             null)
        return items
    }

    /**
     * Get all service proxy instances
     *
     * @param serviceInterface The service type
     *
     * @return An array of service proxy instances.
     */
    def getServices(Class type) {
        return getServices(type, null)
    }

    /**
     * Get all service proxy instances
     *
     * @param serviceInterface The service type
     * @param name The service name to match. If null, return all services for the given type
     *
     * @return An array of service proxy instances.
     */
    def getServices(Class type, String name) {
        def classes = [type]
        def attrs = null
        if(name!=null)
            attrs = [new Name(name)]
        ServiceTemplate template = new ServiceTemplate(null, (Class[])classes, (Entry[])attrs)
        ServiceItem[] items = serviceDiscoveryManager.lookup(template,
                                                             Integer.MAX_VALUE,
                                                             null)
        def services = []
        for(int i=0; i<items.length; i++)
            services << items[i].service
        return services
    }

    /**
     * Wait for service to come online
     *
     * @param type The service type
     *
     * @return The discovered service instance.
     *
     * @throws TimeoutException is the service is not discovered in 60 seconds
     */
    def waitForService(Class type) {
        return waitForService(type, null)
    }

    /**
     * Wait for service to come online
     *
     * @param serviceName The name of the service
     *
     * @return The discovered service instance.
     *
     * @throws TimeoutException is the service is not discovered in 60 seconds
     */
    def waitForService(String serviceName) {
        return waitForService(null, serviceName)
    }

    /**
     * Wait for service to come online
     *
     * @param type The service type
     * @param serviceName The name of the service
     *
     * @return The discovered service instance.
     *
     * @throws TimeoutException is the service is not discovered in 60 seconds
     */
    def waitForService(Class type, String name) {
        def classes = null
        if(type!=null)
            classes = [type]
        def attrs = null
        if(name!=null)
            attrs = [new Name(name)]
        ServiceTemplate template = new ServiceTemplate(null, (Class[])classes, (Entry[])attrs)
        def service
        StringBuffer sb = new StringBuffer()
        if(type!=null)
            sb.append(type.name)
        if(name!=null) {
            if(sb.length()>0)
                sb.append(", ")
            sb.append("name: ").append(name)
        }
        logger.info "Waiting for ${sb.toString()} to be discovered"
        long t0 = System.currentTimeMillis()
        ServiceItem serviceItem = serviceDiscoveryManager.lookup(template, null, 60000)
        if (serviceItem == null) {
            throw new TimeoutException("Unable to discover service ${type.name}")
        }
        service = serviceItem.service
        logger.info "${sb.toString()} has been discovered, elapsed time: "+
                    "${(System.currentTimeMillis()-t0)} millis"
        return service
    }

    private String getJava() {
        StringBuilder jvmBuilder = new StringBuilder();
        jvmBuilder.append(System.getProperty("java.home"));
        jvmBuilder.append(File.separator);
        jvmBuilder.append("bin");
        jvmBuilder.append(File.separator);
        jvmBuilder.append("java");
        jvmBuilder.append(" ");
        return jvmBuilder.toString();
    }

    private def loadManagerConfig() {
        String defaultManagerConfig =
            "jar:file:${Utils.getRioHome()}/lib/rio-test.jar!/default-manager-config.groovy"
        String mgrConfig = System.getProperty('org.rioproject.test.manager.config',
                                              defaultManagerConfig)
        logger.info "Using TestManager configuration ${mgrConfig}"
        URL url
        try {
            url = new URL(mgrConfig)
        } catch (MalformedURLException e) {
            File mgrConfigFile = new File(mgrConfig)
            if(!mgrConfigFile.exists())
                throw new RuntimeException(
                    "Cannot load [${mgrConfig}], it is not found in it's default "+
                    "location of [${defaultManagerConfig}], or "+
                    "your location of the file is incorrect. This file is needed "+
                    "by the Rio TestManager to initialize. Check the setting of the "+
                    "org.rioproject.test.manager.config system property, and/or copy this "+
                    "file from the Rio project distribution to the location "+
                    "mentioned above.", e)
            url = mgrConfigFile.toURI().toURL()
        }

        def config = new ConfigSlurper().parse(url)
        return config
    }

    private File getCreatedLogsFile(String testName) {
        File parent = new File(System.getProperty('java.io.tmpdir')+File.separator+".rio")
        File logs = new File(parent, "$testName-test-logs")
        return logs
    }

    private File getAndCreateCreatedLogsFile(String testName) {
        File dir = new File(System.getProperty('java.io.tmpdir')+File.separator+".rio")
        if(!dir.exists())
            dir.mkdirs()
        File logs = new File(dir, "$testName-test-logs")
        if(!logs.exists())
            logs.createNewFile()
        return logs
    }

    private boolean starterConfigOk(def starter) {
        return starter instanceof String
    }
}