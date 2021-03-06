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
package org.rioproject.jsb;

import com.sun.jini.config.Config;
import com.sun.jini.proxy.BasicProxyTrustVerifier;
import com.sun.jini.reliableLog.LogException;
import com.sun.jini.start.ServiceProxyAccessor;
import net.jini.activation.ActivationExporter;
import net.jini.activation.ActivationGroup;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.config.NoSuchEntryException;
import net.jini.core.entry.Entry;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lookup.ServiceID;
import net.jini.discovery.DiscoveryManagement;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lookup.JoinManager;
import net.jini.lookup.entry.*;
import net.jini.lookup.ui.AdminUI;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;
import org.rioproject.associations.AssociationManagement;
import org.rioproject.bean.BeanAdapter;
import org.rioproject.boot.BootUtil;
import org.rioproject.boot.ServiceClassLoader;
import org.rioproject.config.ConfigHelper;
import org.rioproject.config.Constants;
import org.rioproject.config.ExporterConfig;
import org.rioproject.core.ServiceElement;
import org.rioproject.core.jsb.*;
import org.rioproject.entry.ApplianceInfo;
import org.rioproject.entry.OperationalStringEntry;
import org.rioproject.entry.StandardServiceType;
import org.rioproject.entry.UIDescriptorFactory;
import org.rioproject.event.DispatchEventHandler;
import org.rioproject.event.EventDescriptor;
import org.rioproject.event.EventHandler;
import org.rioproject.event.EventProducer;
import org.rioproject.fdh.HeartbeatClient;
import org.rioproject.jmx.JMXUtil;
import org.rioproject.log.ServiceLogEvent;
import org.rioproject.log.ServiceLogEventHandler;
import org.rioproject.resources.persistence.PersistentStore;
import org.rioproject.resources.servicecore.*;
import org.rioproject.resources.serviceui.UIComponentFactory;
import org.rioproject.sla.SLAThresholdEvent;
import org.rioproject.sla.SLAThresholdEventAdapter;
import org.rioproject.system.ComputeResource;
import org.rioproject.system.ComputeResourceObserver;
import org.rioproject.watch.WatchRegistry;

import javax.management.*;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The ServiceBeanAdapter implements the ServiceBean interface and provides the
 * necessary service infrastructure to make a service developer succesful.
 * Extend this class to take advantage of the Rio ServiceBean capabilities
 *
 * @author Dennis Reedy
 */
public abstract class ServiceBeanAdapter extends ServiceProvider
        implements
        ServiceBean,
        ServiceProxyAccessor,
        ServerProxyTrust,
        ProxyAccessor,
        ServiceBeanAdapterMBean,
        MBeanRegistration,
        NotificationEmitter {
    /** Logger name */
    static final String LOGGER = "org.rioproject.jsb";
    /**
     * A ServiceBeanContext provides the ServiceBean with necessary context
     * required to obtain information about it's environment, attributes and
     * ServiceBeanManager instance
     */
    protected ServiceBeanContext context;
    /** The Uuid for the ServiceBean */
    protected Uuid uuid;
    /** The serviceID for the ServiceBean */
    protected ServiceID serviceID;
    /** The ServiceAdmin implementation for this ServiceBeanAdapter */
    protected ServiceAdminImpl admin;
    /**
     * Joiner utility that contains a JoinManager and provides general utility
     * to set the ServiceProvider's attribute collection
     */
    protected Joiner joiner = new Joiner();
    /** ServiceBean Remote Reference */
    private Remote serviceBeanRemoteRef;
    /**
     * The proxy object for the service that will be registered into the lookup
     * service
     */
    protected Object proxy;
    /** The Exporter for the ServiceBean */
    private Exporter exporter;
    /** A reference to the compute resource for this ServiceBean */
    protected ComputeResource computeResource;
    /**
     * Observe the ComputeResource object associated with this ServiceBean. As
     * this object changes the ComputeResourceUtilizationEntry will be changed
     * to reflect a change in the quantitative and/or qualitative mechanisms
     * reflected by the ComputeResource object
     */
    protected ComputeResourceObserver computeResourceObserver;
    /** Thread that will do snapshots */
    protected SnapshotThread snapshotter;
    /** Manages persistence of the ServiceBeanContext */
    protected ServiceBeanContextManager contextMgr;
    /**
     * PersistentStore the ServiceBeanContextManager uses to manage the state
     * of the ServiceBeanContext
     */
    protected PersistentStore store;
    /** The state of the ServiceBean */
    protected int state = 0;
    /** Indicates the ServiceBean is being shutdown */
    private boolean inShutdown = false;
    /**
     * The ServiceElementChangeManager listens for updates to the
     * ServiceElement by the ServiceBeanManager
     */
    private ServiceElementChangeManager sElemChangeMgr;
    /** EventHandler for dispatching SLAThresholdEvents */
    private DispatchEventHandler slaEventHandler;
    /**
     * The Landlord which will manage leases being used for monitoring this
     * service
     */
    private LandlordLessor monitorLandlord;
    /** The ServiceBeanState component, managing the state of the
     * ServiceBean */
    protected ServiceBeanState jsbState = new ServiceBeanState();
    /**
     * The activation id for this service. This attribute will be valid if and
     * only if the object has been registered with the activation system, and
     * the Service that extends this class sets the ActivationID provided by
     * the activation system as part of its constructor.
     */
    protected ActivationID activationID;
    /**
     * Component name we use to find items in the configuration. The value is
     * set to the package name of the concrete implementation of this class.
     * If the class has no package name, the component is the name of the class
     */
    protected String serviceBeanComponent;
    /** Logger */
    static final Logger logger = Logger.getLogger(LOGGER);
    /** The associated ActivationSystem, or null if not activatable */
    private ActivationSystem activationSystem;
    /** The HeartbeatClient, which will manage sending heartbeat
     * announcements */
    private HeartbeatClient heartbeatClient;
    /** Our login context, for logging out */
    private LoginContext loginContext;
    /** Indicates the maximum amount of time to wait for unexport attempts */
    private long maxUnexportDelay;
    /** Length of time to sleep between unexport attempts */
    private long unexportRetryDelay;
    /** When this service was created */
    long started;
    /* ObjectName used to register and unregister from mbean server */
    protected ObjectName objectName;
    /* MBean server we are registered to */
    protected MBeanServer mbeanServer;
    protected final List<MBeanNotificationInfo> mbeanNoticationInfoList =
        new ArrayList<MBeanNotificationInfo>();
    protected SLAThresholdEventAdapter slaThresholdEventAdapter;

    /**
     * Construct a ServiceBeanAdapter
     */
    public ServiceBeanAdapter() {
        super();
        started = System.currentTimeMillis();
        if(getClass().getPackage()!=null)
            serviceBeanComponent = getClass().getPackage().getName();
        else
            serviceBeanComponent = getClass().getName();
        if(logger.isLoggable(Level.FINEST))
            logger.finest("Set configuration component name as : "+
                          serviceBeanComponent);
    }

    /**
     * The start method provides the capability for a ServiceBean to initialize
     * itself and make it ready to accept inbound communications, returning an
     * Object which can be used to communicate with the ServiceBean. It is the
     * responsibility of the ServiceBean to initiate appropriate startup logic.
     * If the ServiceBean has started itself, subsequent invocations of this
     * method will not re-start the ServiceBean, but return the Object created
     * during the initial start
     * 
     * @param context The ServiceBeanContext containing ServiceBean
     * initialization attributes
     * @return An Object that can be used to communicate to the ServiceBean
     * @throws Exception If any errors or unexpected conditions occur
     */
    public Object start(final ServiceBeanContext context) throws Exception {
        if (context == null)
            throw new NullPointerException("ServiceBeanContext is null");
        try {
            Configuration config = context.getConfiguration();
            try {
                loginContext =
                    (LoginContext)Config.getNonNullEntry(config,
                                                         serviceBeanComponent,
                                                         "loginContext",
                                                         LoginContext.class);
            } catch (NoSuchEntryException e) {
                // leave null
            }

            PrivilegedExceptionAction<Object> doStart =
                new PrivilegedExceptionAction<Object>() {
                public Object run() throws Exception {
                    return (doStart(context));
                }
            };
            if (loginContext != null) {
                loginContext.login();
                try {
                    if(context instanceof JSBContext)
                        ((JSBContext)context).setSubject(
                                                     loginContext.getSubject());
                    Subject.doAsPrivileged(loginContext.getSubject(),
                                           doStart,
                                           null);
                } catch (PrivilegedActionException e) {
                    throw e.getCause();
                }
            } else {
                doStart.run();
            }

        } catch(Throwable t) {
            Throwable cause = getRootCause(t);
            jsbState.setState(ServiceBeanState.ABORTED);
            destroy(true);
            if(cause instanceof Exception) {
                throw (Exception)cause;
            } else {
                throw new Exception(cause);
            }
        }
        return (proxy);
    }

    /*
     * Get the root case
     */
    private Throwable getRootCause(Throwable thrown) {
        Throwable cause = thrown;
        Throwable t = cause;
        while(t != null) {
            t = cause.getCause();
            if(t != null)
                cause = t;
        }
        return (cause);
    }

    /*
     * Perform the start after optionally establishing a LoginContext
     * 
     * @param context The ServiceBeanContext
     * @return An Object that can be used to communicate with the service
     * @throws Exception
     */
    private Object doStart(ServiceBeanContext context) throws Exception {
        if (jsbState.getState() < ServiceBeanState.STARTED) {
            jsbState.setState(ServiceBeanState.STARTING);

            BeanAdapter.invokeLifecycleInjectors(this, context);
            
            /* Set the Configuration for this ServiceProvider */
            //setConfiguration(context.getConfiguration());
            
            /* Set the WatchRegistry for this ServiceProvider */
            setWatchRegistry(context.getWatchRegistry());
            /* Set the event table for this ServiceProvider */
            if(context instanceof JSBContext)
                setEventTable(((JSBContext)context).getEventTable());
            else
                logger.warning("Cannot set EventTable, " +
                               "context not a JSBContext");
            exporter = getExporter(context.getConfiguration());
            serviceBeanRemoteRef = exportDo(exporter);
            /* Initialize the ServiceBean */
            proxy = null;
            if (jsbState.getState() < ServiceBeanState.INITIALIZED) {
                initialize(context);
                jsbState.setState(ServiceBeanState.INITIALIZED);
            } else {
                getServiceProxy();
            }
            jsbState.setState(ServiceBeanState.STARTED);
        } else {
            computeResourceObserver.setSource(proxy);
        }
        return (proxy);
    }

    /**
     * The initialize method is invoked to initialize the ServiceBean. This
     * method is invoked only once. The ServiceBeanAdapter initializes
     * required infrastructure elements in order to prepare the ServiceBean
     * for processing.
     * 
     * @param context The ServiceBeanContext to initialize the ServiceBean with
     * @throws Exception If something unexpected happens
     */
    public void initialize(ServiceBeanContext context) throws Exception {
        initialize(context, null);
    }

    /**
     * The initialize method is invoked to initialize the ServiceBean. This
     * method is invoked only once. The ServiceBeanAdapter initializes required
     * infrastructure elements in order to prepare the ServiceBean for
     * processing.
     * 
     * @param context The ServiceBeanContext to initialize the ServiceBean. If
     * this parameter is null a NullPointerException is thrown
     * @param store A PersistentStore which will be used as a basis to create
     * a ServiceBeanContextManager used to manage the state of the
     * ServiceBeanContext. If this parameter is null, a
     * ServiceBeanContextManager will not be created
     * @throws Exception If something unexpected happens
     */
    public void initialize(ServiceBeanContext context, PersistentStore store)
            throws Exception {
        if (context == null)
            throw new NullPointerException("ServiceBeanContext is null");
        if (store != null) {
            this.store = store;
            ServiceBeanManager jsbManager = context.getServiceBeanManager();
            contextMgr = new ServiceBeanContextManager(context);
            snapshotter = new SnapshotThread(this.getClass().getName());
            ServiceBeanContext restoredContext =
                contextMgr.restoreContext(store);
            this.context =
                (restoredContext == null ? context : restoredContext);
            ((JSBContext) this.context).setServiceBeanManager(jsbManager);
            snapshotter.start();
        } else {
            this.context = context;
        }
        /* Get the max unexport delay value */
        maxUnexportDelay = Config.getLongEntry(context.getConfiguration(),
                                               serviceBeanComponent,
                                               "maxUnexportDelay",
                                               5*1000,  /* default is 5 seconds */
                                               1,        /* min is 1 second */
                                               60*1000); /* max is 60 seconds */
        unexportRetryDelay = Config.getLongEntry(context.getConfiguration(),
                                                 serviceBeanComponent,
                                                 "unexportRetryDelay",
                                                 1000, /* default is 1 second */
                                                 1,    /* min is 1 second */
                                                 Long.MAX_VALUE);
        Configuration config = context.getConfiguration();
        /*
         * Create the LandlordLessor for clients that desire to monitor this
         * service
         */
        monitorLandlord = new LandlordLessor(config);
        /*
         * Create an EventDescriptor for the SLAThresholdEvent and add it as an
         * attribute. Create a DispatchEventHandler to handle the sending of
         * SLAThresholdEvents
         */
        EventDescriptor slaEventDesc = SLAThresholdEvent.getEventDescriptor();
        slaEventHandler = new DispatchEventHandler(slaEventDesc, config);
        getEventTable().put(slaEventDesc.eventID, slaEventHandler);
        addAttribute(slaEventDesc);


        /*
         * If the ServiceLogEventHandler has not had it's source service set,
         * create the eventDescriptor for the ServiceLogEvent and add as an
         * attribute
         */
        for(Handler h : Logger.getLogger("").getHandlers()) {
            if(h instanceof ServiceLogEventHandler) {
                ServiceLogEventHandler s = (ServiceLogEventHandler)h;
                if(s.getSource()==null) {
                    EventDescriptor serviceLogEventDescriptor = ServiceLogEvent.getEventDescriptor();
                    EventHandler serviceLogEventHandler =
                        new DispatchEventHandler(serviceLogEventDescriptor, config);
                    getEventTable().put(serviceLogEventDescriptor.eventID, serviceLogEventHandler);
                    s.setEventHandler(serviceLogEventHandler);
                    s.setSource((EventProducer)getExportedProxy());
                    addAttribute(serviceLogEventDescriptor);
                }
                break;
            }
        }

        this.computeResource =
            context.getComputeResourceManager().getComputeResource();
        /*
        * Create the ComputeResource observer object that will listen for
        * changes to the ComputeResource component
        */
        computeResourceObserver =
            new ComputeResourceObserver(computeResource,
                                        context,
                                        slaEventHandler);
        computeResourceObserver.setSource(getServiceProxy());
        /*
         * Create a ServiceElementChangeManager to listen for updates to the
         * ServiceElement object by the ServiceBeanManager
         */
        sElemChangeMgr = new ServiceElementChangeManager();
        context.getServiceBeanManager().addListener(sElemChangeMgr);

        initializeJMX(context);
    }

    /**
     * Get the ComputeResourceObserver
     *
     * @return ComputeResourceObserver The ComputeResourceObserver for the
     * service bean 
     */
    protected ComputeResourceObserver getComputeResourceObserver() {
        return(computeResourceObserver);
    }

    /**
     * Called from initialize() to prepare JMX resources such as registering with
     * MBeanServer
     *
     * @param context The ServiceBeanContext
     * @throws Exception If errors occur
     */
    protected void initializeJMX(ServiceBeanContext context) throws Exception {
        ObjectName objectName = createObjectName(context);
        MBeanServer mbeanServer =
            org.rioproject.jmx.MBeanServerFactory.getMBeanServer();
        registerMBean(objectName, mbeanServer);

        //translate events to notifications
        slaThresholdEventAdapter =
            new SLAThresholdEventAdapter(objectName,
                                         getNotificationBroadcasterSupport());
        register(SLAThresholdEvent.getEventDescriptor(),
                 slaThresholdEventAdapter,
                 null,
                 Long.MAX_VALUE);
        //register notification info
        mbeanNoticationInfoList.add(
            slaThresholdEventAdapter.getNotificationInfo());
        if(context.getServiceElement().forkService() &&
           System.getProperty(Constants.SERVICE_BEAN_EXEC_NAME)!=null) {
            addAttributes(JMXUtil.getJMXConnectionEntries(context.getConfiguration()));
        }
    }

    /**
     * Register the service using the ObjectName to the MBeanServer
     *
     * @param oName The ObjectName to register
     * @param mbeanServer The MBeanServer to use
     *
     * @throws NotCompliantMBeanException If the bean is not compliant
     * @throws MBeanRegistrationException If the bean is already registered
     * @throws InstanceAlreadyExistsException If the instance already exists
     */
    protected void registerMBean(ObjectName oName,
                                 MBeanServer mbeanServer)
    throws NotCompliantMBeanException,
           MBeanRegistrationException,
           InstanceAlreadyExistsException {        
        mbeanServer.registerMBean(this, oName);
    }

    /**
     * Called from destroy() (or if the service bean is aborted during start)
     * to cleanup JMX resources and unregister from MBeanServer
     */
    protected void cleanJMX() {
        if (mbeanServer != null && objectName != null) {
            try {
                mbeanServer.unregisterMBean(objectName);
            } catch (InstanceNotFoundException e) {
                logger.warning(e.toString());
            } catch (MBeanRegistrationException e) {
                logger.warning(e.toString());
            } finally {
                objectName = null;
            }
        }
    }

    /**
     * Create JMX ObjectName used for MBeanServer registration
     *
     * @param context The ServiceBeanContext to use
     * @return ObjectName used for registeration
     * @throws MalformedObjectNameException If there are errors creating the
     * JMX object name
     */
    protected ObjectName createObjectName(ServiceBeanContext context)
        throws MalformedObjectNameException {
        return JMXUtil.getObjectName(context,
                                     serviceBeanComponent,
                                     context.getServiceElement().getName());
    }

    /**
     * Save registered objectName and MBeanServer as members
     *
     * @see javax.management.MBeanRegistration#preRegister(javax.management.MBeanServer, javax.management.ObjectName)
     */
    public ObjectName preRegister(MBeanServer mBeanServer,
                                  ObjectName objectName) throws Exception {
        this.objectName = objectName;
        this.mbeanServer = mBeanServer;
        return objectName;
    }

    /**
     * Implemented as part of the contract for a
     * {@link javax.management.MBeanRegistration}, empty implementation
     */
    public void postRegister(Boolean aBoolean) {
    }

    /**
     * Implemented as part of the contract for a
     * {@link javax.management.MBeanRegistration}, empty implementation
     */
    public void preDeregister() throws Exception {
    }

    /**
     * Called after unregistering from MBeanServer. Unreference JMX resources.
     *
     * @see javax.management.MBeanRegistration#postDeregister()
     */
    public void postDeregister() {
        mbeanServer = null;
        objectName = null;
    }

    /**
     * Get the ServiceBeanContext
     * 
     * @return The ServiceBeanContext for the ServiceBean
     */
    public ServiceBeanContext getServiceBeanContext() {
        return (context);
    }

    /**
     * Get the AssociationManagement object
     * 
     * @return The AssociationManagement object
     * 
     * @deprecated Use ServiceBeanContext.getAssociationManagement() instead
     */
    public AssociationManagement getAssociationManagement() {
        return (context.getAssociationManagement());
    }

    /**
     * Get the Watch Ui (User Interface) UIDescriptor. This method will use 
     * watch-ui.jar as the JAR which contains the 
     * org.rioproject.watch.AccumulatorViewer class. If the 
     * org.rioproject.watch.AccumulatorViewer class is in a different JAR or
     * the Watch UI is a different class, this method should overriden
     * 
     * @return The Entry object describing the AccumulatorViewer UIDescriptor
     * @throws IOException If errors occur creating the Entry
     */
    protected Entry getWatchUI() throws IOException {
        Entry uiDescriptor = null;
        if(context.getExportCodebase()!=null) {
            uiDescriptor =
                UIDescriptorFactory.getUIDescriptor(
                    AdminUI.ROLE,
                    new UIComponentFactory(new URL(context.getExportCodebase()
                                                   + "watch-ui.jar"),
                                           "org.rioproject.watch.AccumulatorViewer"));
        }
        return uiDescriptor;

    }

    /**
     * Get the Object created by the configured Exporter
     * 
     * @return The Object used to communicate to this service
     */
    protected Remote getExportedProxy() {
        return (serviceBeanRemoteRef);
    }

    /**
     * Create the Object (the proxy) to communicate with the ServiceBean. This
     * method is called by the getServiceProxy() method if the proxy is
     * <code>null</code> or the ServiceBean has not been started. The default
     * semantic is to return the Remote Object created by the exporter.
     *
     * If a different Object (proxy) is required, concrete implementations of
     * the ServiceBeanAdapter must override this method and set the desired
     * Object, or declare the {@link org.rioproject.bean.CreateProxy}
     * annotation or declare the &quot;createProxy(arg)&quot; method
     *
     * @return The proxy to use
     */
    protected Object createProxy() {
        Remote remoteRef = getExportedProxy();
        Object customProxy = BeanAdapter.getCustomProxy(this, remoteRef);
        return customProxy==null?remoteRef:customProxy;
    }

    /**
     * Get the Object (the proxy) to communicate with the ServiceBean. If the
     * proxy is <tt>null</tt>, the <tt>createProxy</tt> method will be called
     * to return the proxy to communicate to the ServiceBean.
     *
     * The proxy attribute will only be created <b><u>iff </u> </b> the
     * proxy attribute is null.
     * 
     * @return The Object used to communicate to this service
     */
    public Object getServiceProxy() {
        if (this.proxy == null) {
            this.proxy = createProxy();
        }
        return (proxy);
    }

    /**
     * @see net.jini.export.ProxyAccessor#getProxy()
     */
    public Object getProxy() {
        return(getExportedProxy());
    }

    /**
     * Returns a <code>TrustVerifier</code> which can be used to verify that a
     * given proxy to this service can be trusted
     */
    public TrustVerifier getProxyVerifier() {
        if (logger.isLoggable(Level.FINEST))
            logger.entering(this.getClass().getName(), "getProxyVerifier");        
        return (new BasicProxyTrustVerifier(serviceBeanRemoteRef));
    }

    /**
     * Get the DiscoveryManagement instance for this service. If the
     * DiscoveryManagement attribute is null, this method will create a
     * DiscoveryManager instance. If the DiscoveryManagement attribute is not
     * null, this method will return the DiscoveryManagement instance that has
     * already been created
     * 
     * @return A DiscoveryManagement instance created from the
     * ServiceBeanContext
     * 
     * @throws java.io.IOException because construction of the class
     * DiscoveryManagement implementation may initiate the discovery process,
     * which can throw an IOException when socket allocation occurs.
     * 
     * @deprecated Use ServiceBeanContext.getDiscoveryManager
     */
    public DiscoveryManagement getDiscoveryManager() throws IOException {
        return (context.getDiscoveryManagement());
    }

    /**
     * Get the EventHandler that has been created to handle the sending of
     * SLAThresholdEvent event objects to registered EventConsumer clients
     * 
     * @return The EventHandler that has been created to handle
     * the sending of SLAThresholdEvent events
     */
    public EventHandler getSLAEventHandler() {
        return (slaEventHandler);
    }

    /**
     * Add an attribute to the Collection of attributes the Joiner utility
     * maintains. If the ServiceBean is advertised, the new attribute will be
     * added to the collection of attributes for the ServiceBean
     * 
     * @param attribute Entry to add
     */
    public void addAttribute(Entry attribute) {
        if (attribute == null) {
            if(logger.isLoggable(Level.FINEST))
                logger.finest("attribute is null");
            return;
        }
        joiner.addAttribute(attribute);
        if (jsbState.getState() == ServiceBeanState.ADVERTISED) {
            JoinManager jMgr = getJoinManager();
            if (jMgr != null)
                jMgr.addAttributes(new Entry[]{attribute});
            else
                throw new NullPointerException("JoinManager is null");
        }
    }

    /**
     * Add attributes to the Collection of attributes the Joiner utility
     * maintains. If the ServiceBean is advertised, the new attributes will be
     * added to the collection of attributes for the ServiceBean
     * 
     * @param attributes Array of Entry attributes
     */
    public void addAttributes(Entry[] attributes) {
        if (attributes == null)
            throw new NullPointerException("attributes are null");
        for (Entry attribute : attributes)
            addAttribute(attribute);
    }

    /**
     * @see org.rioproject.jsb.ServiceBeanAdapterMBean#advertise()
     */
    public void advertise() throws IOException {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST,
                       "["+context.getServiceElement().getName()+"] "+
                       "ServiceBeanAdapter.advertise()");
        }
        if (jsbState.getState() == ServiceBeanState.ADVERTISED) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST,
                           "Already advertised "+
                           "["+context.getServiceElement().getName()+"]");
            }
            return;
        }
        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST,
                       "["+context.getServiceElement().getName()+"] "+
                       "verify transition");
        jsbState.verifyTransition(ServiceBeanState.ADVERTISED);

        ArrayList<Entry> attrList = new ArrayList<Entry>();

        /* 1. Add a UIDescriptor for the AccumulatorViewer */
        Entry watchUI = getWatchUI();
        if(watchUI!=null)
            attrList.add(watchUI);
        /*
         * Add attributes from the ServiceBeanContext to the collection of
         * attributes the Joiner utility maintains 
         */
        /* 2. Create and add OperationalStringEntry */
        String opStringName =
            context.getServiceElement().getOperationalStringName();
        if (opStringName != null)
            attrList.add(new OperationalStringEntry(opStringName));
        /* 3. Create and add ApplianceInfo */
        ApplianceInfo aInfo = new ApplianceInfo();
        aInfo.initialize(computeResource.getAddress());
        attrList.add(aInfo);
        /* 4. Create and add Host */
        Host host = new Host(computeResource.getAddress().getHostAddress());
        attrList.add(host);
        /* 5. Create and add Name */
        Name name = new Name(context.getServiceElement().getName());
        attrList.add(name);
        String comment =
            context.getServiceElement().getServiceBeanConfig().getComment();
        /* 6. Create and add Comment */
        if (comment != null)
            attrList.add(new Comment(comment));
        /* 7. Create and add StandardServiceType */
        StandardServiceType sType = new StandardServiceType();
        sType.name = name.name;
        if(comment!=null)
            sType.description = comment;
        attrList.add(sType);

        /*
         * 8. If we have an artifact, get the version number from it and create
         * the ServiceInfo
         */
        ServiceInfo sInfo = getServiceInfo();
        if(sInfo!=null)
            attrList.add(sInfo);

        /* Get any attribute added to the context*/
        if(context instanceof JSBContext) {
            attrList.addAll(((JSBContext)context).getAttributes());
        }

        if(logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST,
                       "["+context.getServiceElement().getName()+"] " +
                       "do the join");
        LeaseRenewalManager lrm = null;
        /*
         * The advertise call may be invoked via the MBeanServer. If it is, the
         * context classloader will not be the classloader which loaded this
         * bean. If the context classloader is not a ServiceClassLoader, then
         * set the current context classloader to be the classloader which
         * loaded this class. This is needed to load the configuration file
         */
        final Thread currentThread = Thread.currentThread();
        final ClassLoader cCL = AccessController.doPrivileged(
            new PrivilegedAction<ClassLoader>() {
                public ClassLoader run() {
                    return (currentThread.getContextClassLoader());
                }
            });
        boolean swapCLs = !(cCL instanceof ServiceClassLoader);
        try {
            final ClassLoader myCL = AccessController.doPrivileged(
                new PrivilegedAction<ClassLoader>() {
                    public ClassLoader run() {
                        return (getClass().getClassLoader());
                    }
                });
            if(swapCLs) {
                AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                    public ClassLoader run() {
                        currentThread.setContextClassLoader(myCL);
                        return (null);
                    }
                });
            }
            lrm = new LeaseRenewalManager(context.getConfiguration());
        } catch(Exception e) {
            logger.log(Level.WARNING,
                       "Creating LeaseRenewalManager",
                       e);
            lrm = new LeaseRenewalManager();
        } finally {
            if(swapCLs) {
                AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                    public ClassLoader run() {
                        currentThread.setContextClassLoader(cCL);
                        return (null);
                    }
                });
            }
        }

        if(serviceID==null) {
            /* Get the Uuid*/
            getUuid();            
            /* Create the ServiceID */
            if (logger.isLoggable(Level.FINE))
                logger.fine("Create ServiceID from UUID " + uuid.toString());
            serviceID = new ServiceID(uuid.getMostSignificantBits(),
                                      uuid.getLeastSignificantBits());

        }
        joiner.asyncJoin(getServiceProxy(),
                         serviceID,
                         attrList.toArray(new Entry[attrList.size()]),
                         context.getDiscoveryManagement(),
                         lrm);
        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST,
                       "["+context.getServiceElement().getName()+
                       "] set state to ADVERTISED");
        jsbState.setState(ServiceBeanState.ADVERTISED);
    }

    protected ServiceInfo getServiceInfo() {
        ServiceInfo sInfo = null;
        String artifact;
        if(context.getServiceElement().getExportBundles()!=null &&
           context.getServiceElement().getExportBundles().length>0) {
            artifact = context.getServiceElement().getExportBundles()[0].getArtifact();
        } else {
            artifact = context.getServiceElement().getComponentBundle().getArtifact();
        }
        if(artifact!=null) {
            String version = getVersionFromArtifact(artifact);
            if(version!=null)
                sInfo = new ServiceInfo(context.getServiceElement().getName(),
                                        "",
                                        "",
                                        version,
                                        "","");

        }
        return sInfo;
    }

    private String getVersionFromArtifact(String a) {
        String version = null;
        String[] parts = a.split(":");
        if(parts.length<3 )
            return null;
        if (parts.length > 3) {
            version = parts[3];
        } else {
            if (parts.length > 2)
                version = parts[2];
        }
        return version;
    }


    /**
     * @see org.rioproject.jsb.ServiceBeanAdapterMBean#unadvertise
     */
    public void unadvertise() {
        if (jsbState.getState() != ServiceBeanState.ADVERTISED
            || jsbState.getState() == ServiceBeanState.UNADVERTISED
            || jsbState.isAborted())
            return;
        try {
            if(joiner != null)
                joiner.terminate();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Terminating Joiner", t);
        } finally {
            jsbState.setState(ServiceBeanState.UNADVERTISED);
        }
    }

    /**
     * The stop method informs the ServiceBean to unexport itself from any
     * underlying distributed Object communication mechanisms making it
     * incapable of accepting inbound communications
     *
     * @param force If true, unexports the ServiceBean even if there are
     * pending or in-progress calls; if false, only unexports the ServiceBean
     * if there are no pending or in-progress calls.
     *
     * If the <code>force</code> parameters is <code>false</code>, unexporting
     * the ServiceBean will be governed by the following configuration
     * properties:
     * <ul>
     * <li><tt>maxUnexportDelay</tt> Indicates the maximum amount of time to
     * wait for unexport attempts
     * <li><tt>unexportRetryDelay</tt> Length of time to sleep between unexport
     * attempts
     * </ul>
     *
     * @throws IllegalStateException If the state transition is illegal
     */
    public void stop(boolean force) {
        if(jsbState.getState()!=ServiceBeanState.ABORTED)
            jsbState.verifyTransition(ServiceBeanState.STOPPED);

        /* Unregister before unexporting */
        if (activationID != null) {
            try {
                activationSystem.unregisterObject(activationID);
            } catch (Exception e) {
                logger.log(Level.WARNING,
                           "Exception calling ActivationSystem.unregisterObject",
                           e);
            }
        }
        UnexportTask unexportTask = new UnexportTask(exporter, force);
        unexportTask.start();
        try {
            unexportTask.join();
        } catch(InterruptedException e) {
            logger.warning("UnexportTask interrupted");
        } finally {
            proxy = null;
            if(jsbState.getState()!=ServiceBeanState.ABORTED)
                jsbState.setState(ServiceBeanState.STOPPED);
        }
        /*
         * Unexport the Landlordlessor maintaining any leases for monitoring
         * clients
         */
        if(monitorLandlord!=null)
            monitorLandlord.stop(true);
        /* Inform the activation system to make this service inactive */
        if (activationID != null) {
            try {
                ActivationGroup.inactive(activationID, exporter);
            } catch (ActivationException e) {
                logger.log(Level.INFO, "ActivationGroup.inactive failed, "
                                       + e.getLocalizedMessage() + ", service is stopped");
            } catch (RemoteException e) {
                logger.log(Level.WARNING,
                           "Calling ActivationGroup.inactive",
                           e);
            }
        }
    }

    /**
     * Provide a concrete implementation of getAdmin
     * 
     * @return  A ServiceAdminProxy instance to administer the ServiceBean
     */
    public Object getAdmin() {
        Object adminProxy = null;
        try {
            if(admin == null) {
                Exporter adminExporter = getAdminExporter();
                if (contextMgr != null)
                    admin =
                        new ServiceAdminImpl(this,
                                             adminExporter,
                                             contextMgr.
                                               getContextAttributeLogHandler());
                else
                    admin = new ServiceAdminImpl(this, adminExporter);
            }
            admin.setServiceBeanContext(getServiceBeanContext());
            adminProxy = admin.getServiceAdmin();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Getting ServiceAdminImpl", t);
        }
        return (adminProxy);
    }

    /**
     * Close down all WatchDataSource instances, unexporting them from the
     * runtime
     */
    protected void destroyWatches() {
        WatchRegistry wr = getWatchRegistry();
        if(wr!=null)
            wr.closeAll();
    }

    /**
     * @see org.rioproject.jsb.ServiceBeanAdapterMBean#destroy
     */
    public void destroy() {
        destroy(false);
    }

    /**
     * @see org.rioproject.jsb.ServiceBeanAdapterMBean#destroy(boolean)
     */
    public void destroy(boolean force) {
        if (inShutdown)
            return;
        inShutdown = true;
        if (snapshotter != null)
            snapshotter.interrupt();
        if (computeResourceObserver != null) {
            computeResourceObserver.setIgnore(true);
            computeResourceObserver.disconnect();
        }
        /* Unregister the ServiceElement observer */
        if(sElemChangeMgr!=null) {
            context.getServiceBeanManager().removeListener(sElemChangeMgr);
            sElemChangeMgr = null;
        }

        if (slaEventHandler != null) {
            slaEventHandler.terminate();
            slaEventHandler = null;
        }

        /*
         * Close down all WatchDataSource instances, unexporting them from the
         * runtime
         */
        destroyWatches();

        /* No longer discoverable */
        try {
            unadvertise();
        } catch (IllegalStateException e) {
            logger.log(Level.WARNING, "Unadvertising ServiceBean", e);
        }

        /* Terminate AssociationManagement */        
        if(context!=null && context.getAssociationManagement()!=null)
            context.getAssociationManagement().terminate();

        /* If any PlatformCapability instances were added, remove them */
        if(context instanceof JSBContext)
            ((JSBContext)context).removePlatformCapabilities();


        /* If we're sending heartbeats, stop them */
        if (heartbeatClient != null) {
            if(logger.isLoggable(Level.FINEST))
                logger.finest("Terminating HeartbeatClient");
            heartbeatClient.terminate();
        }

        /* Terminate DiscoveryManagement */
        try {
            if(context!=null && context.getDiscoveryManagement()!=null)
                context.getDiscoveryManagement().terminate();
        } catch (Throwable t) {
            logger.log(Level.WARNING,
                       "DiscoveryManagement termination",
                       t);
        }

        /* Unexport the ServiceAdmin */
        if (admin != null) {
            try {
                admin.unexport(true);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Unexporting ServiceAdminImpl", e);
            }
        }

        /* Unexport the service */
        try {
            if(System.getProperty("StaticCybernode")==null)
                stop(force);
        } catch (IllegalStateException e) {
            logger.log(Level.WARNING, "Stopping ServiceBean", e);
        }

        /* Logout */
        if (loginContext != null) {
            try {
                loginContext.logout();
            } catch (LoginException e) {
                logger.log(Level.WARNING, "logout failed", e);
            }
        }

        /* cleanup JMX */
        cleanJMX();

        /* Discard */
        if(context!=null) {
            ServiceBeanManager serviceBeanManager = context.getServiceBeanManager();
            if(serviceBeanManager != null) {
                DiscardManager discardMgr = serviceBeanManager.getDiscardManager();
                if (discardMgr != null) {
                    discardMgr.discard();
                } else {
                    logger.warning("DiscardManager is null, unable to discard");
                }
            } else {
                logger.warning("ServiceBeanManager is null, unable to discard");
            }
            context = null;
        }
        admin = null;
        serviceBeanRemoteRef = null;
        proxy = null;
    }

    /**
     * @see MonitorableService#monitor
     */
    public Lease monitor(long duration) throws LeaseDeniedException,
                                               RemoteException {
        if (duration <= 0)
            throw new LeaseDeniedException("lease duration of [" + duration
                                           + "] is invalid");
        String phonyResource = this.getClass().getName() + ":"
                               + System.currentTimeMillis();
        ServiceResource serviceResource = new ServiceResource(phonyResource);
        Lease lease = monitorLandlord.newLease(serviceResource, duration);
        return (lease);
    }

    /**
     * @see MonitorableService#startHeartbeat
     */
    public void startHeartbeat(String[] configArgs)
            throws ConfigurationException, RemoteException {
        if (heartbeatClient == null)
            heartbeatClient = new HeartbeatClient(uuid);
        heartbeatClient.addHeartbeatServer(configArgs);
    }

    /**
     * @see MonitorableService#ping
     */
    public void ping() throws RemoteException {
    }

    /**
     * Get the Uuid
     */
    public Uuid getUuid() {
        if (uuid != null)
            return (uuid);
        ServiceBeanManager mgr = context.getServiceBeanManager();
            uuid = mgr.getServiceID();
        if (uuid == null) {
            if (logger.isLoggable(Level.FINE))
                logger.fine("UUID is unknown, generate new UUID");
            uuid = UuidFactory.generate();
            if (mgr instanceof JSBManager) {
            ((JSBManager)mgr).setServiceID(uuid);
        } else {
            if (logger.isLoggable(Level.FINE))
                logger.fine("ServiceBeanManager is not a JSBManager, " +
                            "cannot set UUID");
        }
        }
        return (uuid);
    }

    /**
     * Get the Uuid of the Cybernode
     *
     * @return The Uuid of the Cybernode
     */
    public Uuid getServiceBeanInstantiatorUuid() {
        return(context.getServiceBeanManager()
                      .getServiceBeanInstance()
                      .getServiceBeanInstantiatorID());
    }
    /**
     * Get the JoinManager created by the Joiner utility
     * 
     * @return The JoinManager created by the Joiner. May be null
     */
    public JoinManager getJoinManager() {
        return (joiner.getJoinManager());
    }

    /**
     * Get the configured Exporter
     *
     * @param config The configuration to obtain
     * @return An Exporter which can be used to export the service. If no
     * Exporter has been configured, a new BasicJeriExporter with
     * <ul>
     * <li>A TcpServerEndpoint created on a random port,
     * <li>a BasicILFactory,
     * <li>distributed garbage collection turned off,
     * <li>keep alive on.
     * </ul>
     * If activatable, the same default will be used but wrapped in an
     * ActivationExporter and created with the service's ActivationID
     * @throws Exception If there are errors getting the Exporter
     */
    protected Exporter getExporter(Configuration config) throws Exception {
        if(config==null)
            throw new NullPointerException("config is null");
        Exporter exporter;
        String address =
            BootUtil.getHostAddressFromProperty(Constants.RMI_HOST_ADDRESS);
        final Exporter defaultExporter =
            new BasicJeriExporter(TcpServerEndpoint.getInstance(address, 0),
                                  new BasicILFactory(),
                                  false,
                                  true);
        if(isActivatable() && activationID != null) {
            ProxyPreparer activationIdPreparer =
                (ProxyPreparer)Config.getNonNullEntry(config,
                                                      serviceBeanComponent,
                                                      "activationIdPreparer",
                                                      ProxyPreparer.class,
                                                      new BasicProxyPreparer());
            ProxyPreparer activationSystemPreparer =
                (ProxyPreparer)Config.getNonNullEntry(config,
                                                      serviceBeanComponent,
                                                      "activationSystemPreparer",
                                                      ProxyPreparer.class,
                                                      new BasicProxyPreparer());
            this.activationID =
                (ActivationID)activationIdPreparer.prepareProxy(activationID);
            activationSystem =
                (ActivationSystem)activationSystemPreparer.prepareProxy(
                    ActivationGroup.getSystem());
            exporter =
                (Exporter)Config.getNonNullEntry(config,
                                                 serviceBeanComponent,
                                                 "serverExporter",
                                                 Exporter.class,
                                                 new ActivationExporter(activationID,
                                                                        defaultExporter),
                                                 activationID);
        } else {
            exporter = ExporterConfig.getExporter(config,
                                                  serviceBeanComponent,
                                                  "serverExporter",
                                                  defaultExporter);
        }
        return(exporter);
    }

    /**
     * This method exports the remote object making it available to receive
     * incoming calls
     *
     * @param exporter The Exporter to use, must not be null
     *
     * @return Remote The remote object used to accept incoming calls
     *
     * @throws Exception If errors occur
     */
    protected Remote exportDo(Exporter exporter) throws Exception {
        if(exporter==null)
            throw new NullPointerException("exporter is null");
        return(exporter.export(this));
    }

    /**
     * @see org.rioproject.jsb.ServiceBeanAdapterMBean#getStarted
     */
    public Date getStarted() {
        return(new Date(getStartTime()));
    }

    public long getStartTime() {
        return(started);
    }

    /**
     * @see org.rioproject.jsb.ServiceBeanAdapterMBean#getLookupGroups
     */
    public String[] getLookupGroups() {
        return (admin.getLookupGroups());
    }

    /**
     * @see org.rioproject.jsb.ServiceBeanAdapterMBean#setLookupGroups
     */
    public void setLookupGroups(String[] groups) {
        admin.setLookupGroups(groups);
    }

    /**
     * Get the Exporter to export the ServiceAdmin
     * 
     * @return The Exporter obtained from the Configuration matching the
     * package name of the concrete implementation of this class with the name 
     * adminExporter
     *
     * @throws ConfigurationException If there are errors reading the
     * configuration
     * @throws UnknownHostException If the host is unknown
     */
    protected Exporter getAdminExporter() throws
                                          ConfigurationException,
                                          UnknownHostException {
        String address =
            BootUtil.getHostAddressFromProperty(Constants.RMI_HOST_ADDRESS);
        Exporter basicExporter =
            new BasicJeriExporter(TcpServerEndpoint.getInstance(address, 0),
                                  new BasicILFactory(),
                                  false,
                                  true);
        Exporter adminExporter =
            ExporterConfig.getExporter(context.getConfiguration(),
                                       serviceBeanComponent,
                                       "adminExporter",
                                       basicExporter);
        if(logger.isLoggable(Level.FINER))
            logger.finer("["+context.getServiceElement().getName()+"] " +
                         "using admin exporter: "+adminExporter.toString());
        return(adminExporter);
    }

    /**
     * Checks the constructor to see if it matches the signature of
     * java.rmi.activation.Activatable.
     * 
     * @return If the Constructor for the concrete implementation of
     * this class has a signature which matches a signature in the
     * java.rmi.activation.Activatable class return true, otherwise returns
     * false
     */
    private boolean isActivatable() {
        try {
            this.getClass().getConstructor(
                ActivationID.class, MarshalledObject.class);
            return (true);
        } catch (NoSuchMethodException e) {
            return (false);
        }
    }

    public void removeNotificationListener(NotificationListener listener,
                                           NotificationFilter filter,
                                           Object object)
            throws ListenerNotFoundException {
        getNotificationBroadcasterSupport().removeNotificationListener(
            listener, filter, object);
    }

    public void addNotificationListener(NotificationListener listener,
                                        NotificationFilter filter,
                                        Object object)
        throws IllegalArgumentException {
        getNotificationBroadcasterSupport().addNotificationListener(
            listener, filter, object);
    }

    public void removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException {
        getNotificationBroadcasterSupport().removeNotificationListener(listener);
    }

    public MBeanNotificationInfo[] getNotificationInfo() {
        return (mbeanNoticationInfoList.toArray(
            new MBeanNotificationInfo[mbeanNoticationInfoList.size()]));
    }

    public NotificationBroadcasterSupport getNotificationBroadcasterSupport() {
        return context.getServiceBeanManager().getNotificationBroadcasterSupport();
    }

    /**
     * Class which will handle the unexport in a Thread
     */
    private class UnexportTask extends Thread {
        Exporter exporter;
        boolean force;

        UnexportTask(Exporter exporter, boolean force) {
            super("UnexportTask");
            this.exporter = exporter;
            this.force = force;
        }

        public void run() {
            boolean unexported = false;
            long start = System.currentTimeMillis();
            if(!force) {
                if(logger.isLoggable(Level.FINEST))
                    logger.finest("Unexporting "+
                                  context.getServiceElement().getName()+", " +
                                  "maxUnexportDelay="+
                                  maxUnexportDelay+", " +
                                  "unexportRetryDelay="+unexportRetryDelay);
                /*
                final long end_time = start + maxUnexportDelay;
                while(!unexported && (System.currentTimeMillis() < end_time)) {
                    unexported = unexportDo(false);
                    if (!unexported) {
                        Thread.yield();
                    }
                }
                */
                long now = System.currentTimeMillis();
                long end_time = now+maxUnexportDelay;
                if(end_time < 0) {
                    // overflow
                    end_time = Long.MAX_VALUE;
                }

                //boolean unexported = false;
                while((!unexported) && (now < end_time)) {
                    /* wait for any pending operations to complete */
                    unexported = exporter.unexport(false);

                    if(!unexported) {
                        try {
                            /* Sleep for a finite time instead of yield.
                             * In most VMs yield is a no-op so if
                             * unexport(false) is not working (say because
                             * there is a blocking query in progress) a
                             * yield here results in a very tight loop
                             * (plus there may be no other runnable threads)
                             */
                            final long sleepTime =
                                Math.min(unexportRetryDelay, end_time-now);

                            /* sleepTime must > 0, unexportRetryDelay is
                             * > 0 and if now >= end_time we would have
                             * fallen out of the loop
                             */
                            sleep(sleepTime);
                            now = System.currentTimeMillis();
                        } catch(InterruptedException e) {
                            // should never happen, but if it does break
                            // and fall through to force = true case
                            logger.log(Level.INFO,
                                       "exception encountered "+
                                       "unexport retry delay sleep, "+
                                       ", continuing",
                                       e);
                            break;
                        }
                    }
                }

            }
            if(!unexported) {
                unexported = unexportDo(true);
            }
            if(logger.isLoggable(Level.FINEST)) {
                long end = System.currentTimeMillis();
                logger.finest("Unexported "+
                              context.getServiceElement().getName()+", " +
                              "["+unexported+"] time alloted : "+(end-start));
            }
        }

        boolean unexportDo(boolean force) {
            boolean result = exporter==null;
            try {
                if(exporter!=null)
                    result = exporter.unexport(force);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Unexporting ServiceBean", e);
            }
            return(result);
        }
    }

    /**
     * The ServiceElementChangeManager listens for changes made to the
     * ServiceElement by the ServiceBeanManager
     */
    class ServiceElementChangeManager implements ServiceElementChangeListener {

        /**
         * @see org.rioproject.core.jsb.ServiceElementChangeListener#changed
         */
        public void changed(ServiceElement preElem, ServiceElement postElem) {

            /* --- Check for different ServiceUI --- */
            /* Get the current attribute collection from JoinManager, and extract 
             * ServiceUI entries
             */
            if(joiner.getJoinManager()!=null) {
                ArrayList<Entry> suiList = new ArrayList<Entry>();
                Entry[] current = joiner.getJoinManager().getAttributes();
                for (Entry aCurrent : current) {
                    if (aCurrent instanceof UIDescriptor)
                        suiList.add(aCurrent);
                }
                Entry[] suiEntries = suiList.toArray(new Entry[suiList.size()]);
                if(ServiceElementUtil.hasDifferentServiceUIs(
                                                     suiEntries,
                                                     postElem,
                                                     context.getExportCodebase())) {
                    try {
                        /* Using the current attribute collection, remove all 
                         * ServiceUI entries, then add the new entries and modify 
                         * the service's attribute set with the changed serviceUIs
                         */
                        ArrayList<Entry> eList = new ArrayList<Entry>();
                        for (Entry aCurrent : current) {
                            if (!(aCurrent instanceof
                                UIDescriptor)) {
                                eList.add(aCurrent);
                            }
                        }
                        /* Add the Watch UI */
                        try {
                            eList.add(getWatchUI());
                        } catch(Exception e1) {
                            logger.log(Level.WARNING,
                                       "Getting Watch UI",
                                       e1);
                        }
                        String[] args =
                            ConfigHelper.getConfigArgs(
                                postElem.getServiceBeanConfig().getConfigArgs());
                        Configuration config =
                            ConfigurationProvider.getInstance(args);
                        Entry[] serviceUIs =
                            (Entry[])config.getEntry(serviceBeanComponent,
                                                     "serviceUIs",
                                                     Entry[].class,
                                                     new Entry[0],
                                                     context.getExportCodebase());
                        /* Add configured seviceUIs */
                        eList.addAll(Arrays.asList(serviceUIs));

                        Entry[] attrs = eList.toArray(new Entry[eList.size()]);
                        /* Check again, the service may have gone to an 
                         * unadvertised state */
                        if(joiner.getJoinManager()!=null)
                            joiner.getJoinManager().setAttributes(attrs);
                    } catch (ConfigurationException e) {
                        logger.log(Level.WARNING,
                                   "Getting or Applying modified ServiceUIs",
                                   e);
                    } catch (IOException e) {
                        logger.log(Level.WARNING,
                                   "Getting or Applying modified ServiceUIs",
                                   e);
                    }
                }
            }
        }
    }

    /**
     * A Thread that will perform snapshots. Snapshots, done in a separate
     * thread so it will not hang up in progress remote calls
     */
    public class SnapshotThread extends Thread {
        SnapshotThread(String name) {
            super(name + ":SnapshotThread");
            setDaemon(true);
        }

        /**
         * Signal this thread that it should take a snapshot
         */
        public synchronized void takeSnapshot() {
            notifyAll();
        }

        public void run() {
            while (!isInterrupted()) {
                synchronized (this) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                try {
                    store.snapshot();
                } catch (InterruptedIOException e) {
                    // Someone wants us dead
                    return;
                } catch (Exception e) {
                    if (e instanceof LogException
                        && ((LogException) e).detail instanceof
                            InterruptedIOException)
                        return;
                    /*
                     * If taking the snapshot fails for any reason, then one of
                     * the following must be done: -- output the problem to a
                     * file and exit -- output the problem to a file and
                     * continue -- set an "I have a problem" attribute and then
                     * send a notification this issue will be addressed at a
                     * later time
                     */
                    logger.log(Level.WARNING, "Snapshotting ServiceBean", e);
                }
            }
        }
    }
}
