/*
 * Copyright 2008 the original author or authors.
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
package org.rioproject.cybernode.exec;

import com.sun.jini.start.LifeCycle;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.core.lookup.ServiceID;
import net.jini.export.Exporter;
import net.jini.id.Uuid;
import org.rioproject.rmi.RegistryUtil;
import org.rioproject.config.Constants;
import org.rioproject.config.ExporterConfig;
import org.rioproject.core.JSBInstantiationException;
import org.rioproject.core.OperationalStringManager;
import org.rioproject.core.ServiceBeanInstance;
import org.rioproject.core.ServiceElement;
import org.rioproject.core.jsb.ServiceBeanContext;
import org.rioproject.core.provision.ServiceRecord;
import org.rioproject.cybernode.Environment;
import org.rioproject.cybernode.JSBContainer;
import org.rioproject.cybernode.ServiceBeanContainerListener;
import org.rioproject.fdh.FaultDetectionListener;
import org.rioproject.fdh.JMXFaultDetectionHandler;
import org.rioproject.jmx.JMXConnectionUtil;
import org.rioproject.jsb.ServiceBeanActivation;
import org.rioproject.jsb.ServiceElementUtil;
import org.rioproject.opstring.OpStringManagerProxy;
import org.rioproject.resources.util.ThrowableUtil;
import org.rioproject.system.ComputeResource;
import org.rioproject.system.ComputeResourceUtilization;
import org.rioproject.system.SystemCapabilities;
import org.rioproject.system.SystemWatchID;
import org.rioproject.system.capability.PlatformCapability;
import org.rioproject.system.measurable.MeasurableCapability;
import org.rioproject.system.measurable.cpu.CPU;
import org.rioproject.system.measurable.memory.Memory;
import org.rioproject.watch.WatchDataSource;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides support to create a ServiceBean in it's own JVM.
 *
 * @author Dennis Reedy
 */
public class ServiceBeanExec implements ServiceBeanExecutor,
                                        ServiceBeanContainerListener,
                                        FaultDetectionListener<ServiceID> {
    private JSBContainer container;
    private Exporter exporter;
    private String execBindName;
    private ServiceBeanContext context;
    private int cybernodeRegistryPort;
    private int createdRegistryPort;
    private Registry registry;
    private ServiceBeanExecListener listener;
    private JMXFaultDetectionHandler fdh;
    private ComputeResource computeResource;
    static final String CONFIG_COMPONENT = "org.rioproject.cybernode";
    private Logger logger = Logger.getLogger(CONFIG_COMPONENT);

    /**
     * Create a ServiceBeanExecutor launched from the ServiceStarter
     * framework
     *
     * @param configArgs Configuration arguments
     * @param lifeCycle The LifeCycle object that started the
     * ServiceBeanExecutor
     * 
     * @throws Exception if bootstrapping fails
     */
    public ServiceBeanExec(String[] configArgs, LifeCycle lifeCycle)
        throws Exception {
        String sPort = System.getProperty(Constants.REGISTRY_PORT, "0");
        if (Integer.parseInt(sPort) == 0)
            logger.warning("The RMI Registry port provided " +
                           "(or obtained by default) is 0. " +
                           "Because it is 0, the port will default to "+
                           Registry.REGISTRY_PORT+". This port may be " +
                           "taken by another RMI Registry instance. If there is " +
                           "an MBeanServer bound to that RMI Registry " +
                           "instance, the ServiceBeanExec will monitor the " +
                           "ability to connect to that MBeanServer. If that " +
                           "MBeanServer is terminated, the ServiceBeanExec " +
                           "will also terminate. Care should be taken to not " +
                           "use the default RMI Registry port.");
        cybernodeRegistryPort = Integer.parseInt(sPort);
        execBindName =
            System.getProperty(Constants.SERVICE_BEAN_EXEC_NAME, "ServiceBeanExec");
        bootstrap(configArgs);
        logger.info("Started ServiceBeanExecutor for "+execBindName);
    }

    private void bootstrap(String[] configArgs) throws Exception {
        ClassLoader cCL = Thread.currentThread().getContextClassLoader();
        Configuration config = ConfigurationProvider.getInstance(configArgs, cCL);
        container = new JSBContainer(config);
        computeResource = new ComputeResource(config);

        /* Setup persistent provisioning attributes */
        boolean provisionEnabled =
            (Boolean) config.getEntry("org.rioproject.cybernode",
                                      "provisionEnabled",
                                      Boolean.class,
                                      true);
        computeResource.setPersistentProvisioning(provisionEnabled);        
        String provisionRoot = Environment.setupProvisionRoot(provisionEnabled,
                                                              config);
        if(provisionEnabled) {
            if(logger.isLoggable(Level.FINE))
                logger.log(Level.FINE,
                           "Software provisioning has been enabled, "+
                           "using provision root ["+provisionRoot+"]");
        }
        computeResource.setPersistentProvisioningRoot(provisionRoot);

        MeasurableCapability[] mCaps = loadMeasurables(config);
        for(MeasurableCapability mCap : mCaps) {
            computeResource.addMeasurableCapability(mCap);
            mCap.start();
        }

        container.setComputeResource(computeResource);
        container.addListener(this);

        context = ServiceBeanActivation.getServiceBeanContext(
                                                  CONFIG_COMPONENT,
                                                  "Cybernode",
                                                  configArgs,
                                                  getClass().getClassLoader());

        registry = LocateRegistry.getRegistry(cybernodeRegistryPort);
        exporter = ExporterConfig.getExporter(config,
                                              "org.rioproject.cybernode",
                                              "exporter");

        createdRegistryPort = RegistryUtil.getRegistry(config);
        if(createdRegistryPort>0)
            System.setProperty(Constants.REGISTRY_PORT,
                               Integer.toString(createdRegistryPort));
        else
            throw new RuntimeException("Unable to create RMI Registry");
        
        JMXConnectionUtil.createJMXConnection(config);

        Remote proxy = exporter.export(this);
        registry.bind(execBindName, proxy);

        /* Setup FDH to make sure Cybernode doesnt orphan us */
        fdh = new JMXFaultDetectionHandler();
        fdh.setConfiguration(config);
        fdh.setJMXConnection(
            JMXConnectionUtil.getJMXServiceURL(cybernodeRegistryPort, "localhost"));
        fdh.register(this);
        fdh.monitor();
    }

    public int getRegistryPort() {
        return createdRegistryPort;
    }

    public void setUuid(Uuid uuid) {
        if(uuid==null)
            throw new IllegalArgumentException("uuid cannot be null");
        container.setUuid(uuid);
    }

    public void setServiceBeanExecListener(ServiceBeanExecListener listener) {
        this.listener = listener;
    }

    public void applyPlatformCapabilities(PlatformCapability[] pCaps) {
        if(pCaps==null)
            return;
        for(PlatformCapability pCap : pCaps) {
            logger.info("Adding ["+pCap.getName()+"] capability");
            computeResource.addPlatformCapability(pCap);
        }
    }

    public ServiceBeanInstance instantiate(ServiceElement sElem,
                                           OperationalStringManager opStringMgr)
        throws JSBInstantiationException {
        logger.info("Instantiating "+sElem.getName()+", " +
                    "service counter="+container.getServiceCounter());
        if (container.getServiceCounter() > 0)
            throw new JSBInstantiationException("ServiceBeanExecutor has " +
                                                "already instantiated a service");

        OperationalStringManager opMgr = opStringMgr;
        try {
            opMgr = OpStringManagerProxy.getProxy(sElem.getOperationalStringName(),
                                                  opStringMgr,
                                                  context.getDiscoveryManagement());
        } catch (Exception e) {
            logger.log(Level.WARNING,
                       "Unable to create proxy for " +
                       "OperationalStringManager, " +
                       "using provided OperationalStringManager",
                       ThrowableUtil.getRootCause(e));

        }

        /* Set up thread deadlock detection */
        ServiceElementUtil.setThreadDeadlockDetector(sElem, null);

        return container.activate(sElem, opMgr, null);
    }

    public void update(ServiceElement element,
                       OperationalStringManager opStringMgr)  {
        container.update(new ServiceElement[]{element}, opStringMgr);
    }

    public ComputeResourceUtilization getComputeResourceUtilization() {
        return computeResource.getComputeResourceUtilization();
    }

    public void serviceInstantiated(ServiceRecord record) {
        logger.info("Instantiated "+record.getServiceElement().getName());
        try {
            listener.serviceInstantiated(record);
        } catch (RemoteException e) {
            logger.log(Level.WARNING,
                       "Notifying Cybernode that the service is active",
                       e);
        }
    }

    public void serviceDiscarded(ServiceRecord record) {
        logger.info("Destroying ServiceBeanExecutor for "+execBindName);
        fdh.terminate();

        exporter.unexport(true);
        try {
            registry.unbind(execBindName);
        } catch (Exception e) {
            logger.warning(e.getClass().getName()+": "+e.getMessage()+", " +
                           "Unbinding from RMI Registry");
        }
        if(record!=null) {
            try {
                listener.serviceDiscarded(record);
            } catch (RemoteException e) {
                logger.warning(e.getClass().getName()+": "+e.getMessage()+", " +
                               "Notifying Cybernode that we are exiting");
            }
        }
        
        /* Perform the system exit in a thread, allowing the method to return */
        new Thread(new Runnable() {
            public void run() {
                System.exit(0);
            }
        }).start();
    }

    public void serviceFailure(Object service, ServiceID serviceID) {
        logger.warning("Parent Cybernode has orphaned us, exiting");
        container.terminate();
        serviceDiscarded(null);
    }

    public WatchDataSource[] fetch() {
        List<WatchDataSource> watchDataSources = new ArrayList<WatchDataSource>();
        for(MeasurableCapability mCap : computeResource.getMeasurableCapabilities()) {
            watchDataSources.add(mCap.getWatchDataSource());
        }
        return watchDataSources.toArray(new WatchDataSource[watchDataSources.size()]);
    }

    public WatchDataSource fetch(String id) {
        WatchDataSource watchDataSource = null;
        for(MeasurableCapability mCap : computeResource.getMeasurableCapabilities()) {
            if(mCap.getId().equals(id)) {
                watchDataSource = mCap.getWatchDataSource();
                break;
            }
        }
        return watchDataSource;
    }

    private MeasurableCapability[] loadMeasurables(Configuration config) {
        List<MeasurableCapability> measurables = new ArrayList<MeasurableCapability>();
        /* Create the Memory MeasurableCapability */
        try {
            MeasurableCapability memory =
                (MeasurableCapability)config.getEntry(SystemCapabilities.COMPONENT,
                                                      "memory",
                                                      MeasurableCapability.class,
                                                      new Memory(config),
                                                      config);
            if(memory.isEnabled())
                measurables.add(memory);
        } catch(ConfigurationException e) {
            logger.log(Level.WARNING, "Loading Memory MeasurableCapability", e);
        }

        try {
            MeasurableCapability cpu =
                (MeasurableCapability)config.getEntry(SystemCapabilities.COMPONENT+"cpu",
                                                      "jvm",
                                                      MeasurableCapability.class,
                                                      new CPU(config,
                                                              SystemWatchID.PROC_CPU,
                                                              true),
                                                      config);
            if(cpu.isEnabled())
                measurables.add(cpu);
        } catch(ConfigurationException e) {
            logger.log(Level.WARNING, "Loading CPU MeasurableCapability", e);
        } catch (RuntimeException e) {
            logger.warning("JVM CPU monitoring not supported");
        }
        return measurables.toArray(new MeasurableCapability[measurables.size()]);
    }
}
