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
package org.rioproject.monitor;

import net.jini.export.Exporter;
import net.jini.id.UuidFactory;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;
import org.rioproject.core.OperationalString;
import org.rioproject.core.OperationalStringException;
import org.rioproject.core.OperationalStringManager;
import org.rioproject.core.ServiceProvisionListener;
import org.rioproject.monitor.ProvisionMonitor.PeerInfo;
import org.rioproject.resources.persistence.SnapshotHandler;
import org.rioproject.resources.servicecore.ServiceAdmin;
import org.rioproject.resources.servicecore.ServiceAdminImpl;

import java.net.URL;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The ProvisionMonitorAdminImpl class implements the ServiceAdmin interface providing 
 * administrative support.
 *
 * @author Dennis Reedy
 */
public class ProvisionMonitorAdminImpl extends ServiceAdminImpl 
                                       implements ProvisionMonitorAdmin,
                                                  ServerProxyTrust {    
    /** A Logger */
    static Logger logger = Logger.getLogger("org.rioproject.monitor");
    /** Reference to the backend */
    ProvisionMonitorImpl backend;
    ProvisionMonitorAdmin remoteRef;
    
    /**
     * Create a ProvisionMonitorAdminImpl
     * 
     * @param service Concrete implementation of a ServiceBeanAdapter
     * @param exporter The Exporter to export this object
     */
    public ProvisionMonitorAdminImpl(ProvisionMonitorImpl service, 
                                     Exporter exporter) {
        this(service, exporter, null);
    }

    /**
     * Create a ProvisionMonitorAdminImpl
     * 
     * @param service Concrete implementation of a ServiceBeanAdapter
     * @param exporter The Exporter to export this object
     * @param snapshotHandler The service's snapshot handler used for persistence
     */
    public ProvisionMonitorAdminImpl(ProvisionMonitorImpl service, 
                                     Exporter exporter, 
                                     SnapshotHandler snapshotHandler) {
        
        super(service, exporter, snapshotHandler);
        backend = service;
    }

    /**
     * Override parent's method to return <code>TrustVerifier</code> which can
     * be used to verify that the given proxy to this service can be trusted
     *
     * @return TrustVerifier The TrustVerifier used to verify the proxy
     *
     */
    public TrustVerifier getProxyVerifier() {
        if (logger.isLoggable(Level.FINEST))
            logger.entering(this.getClass().getName(), "getProxyVerifier");
        return (new ProvisionMonitorAdminProxy.Verifier(remoteRef));
    }

    /**
     * Override parents getServiceAdmin method
     */
    public ServiceAdmin getServiceAdmin() throws RemoteException {
        if(adminProxy==null) {
            remoteRef = (ProvisionMonitorAdmin)exporter.export(this);
            adminProxy = 
                ProvisionMonitorAdminProxy.getInstance(remoteRef, 
                                                       UuidFactory.generate());
        }
        return(adminProxy);
    }

    /* (non-Javadoc)
     * @see org.rioproject.monitor.DeployAdmin#deploy
     */
    public Map<String, Throwable> deploy(URL opStringURL)
                               throws OperationalStringException {
        return(backend.deploy(opStringURL, null));
    }
    
    /* (non-Javadoc)
     * @see org.rioproject.monitor.DeployAdmin#deploy
     */
    public Map<String, Throwable> deploy(URL opStringURL,
                                         ServiceProvisionListener listener)
                               throws OperationalStringException {
        return(backend.deploy(opStringURL, listener));
    }

     /* (non-Javadoc)
    * @see org.rioproject.monitor.DeployAdmin#deploy
    */
    public Map<String, Throwable> deploy(String location)
    throws OperationalStringException {
        return(backend.deploy(location, null));
    }

    /* (non-Javadoc)
    * @see org.rioproject.monitor.DeployAdmin#deploy
    */
    public Map<String, Throwable> deploy(String location,
                                         ServiceProvisionListener listener)
    throws OperationalStringException {
        return(backend.deploy(location, listener));
    }

    /* (non-Javadoc)
    * @see org.rioproject.monitor.DeployAdmin#deploy
    */
    public Map<String, Throwable> deploy(OperationalString opString)
                               throws OperationalStringException {
        return(backend.deploy(opString, null));
    }
    
    /* (non-Javadoc)
     * @see org.rioproject.monitor.DeployAdmin#deploy
     */
    public Map<String, Throwable> deploy(OperationalString opString,
                                         ServiceProvisionListener listener)
                               throws OperationalStringException {
        return(backend.deploy(opString, listener));
    }

    /* (non-Javadoc)
     * @see org.rioproject.monitor.DeployAdmin#undeploy(java.lang.String, boolean)
     */
    public boolean undeploy(String opStringName)  throws OperationalStringException {
        return(backend.undeploy(opStringName, true));
    }

    /* (non-Javadoc)
     * @see org.rioproject.monitor.DeployAdmin#hasDeployed(java.lang.String)
     */
    public boolean hasDeployed(String opStringName) {
        return(backend.hasDeployed(opStringName));
    }

    /* (non-Javadoc)
     * @see org.rioproject.monitor.DeployAdmin#getOperationalStringManagers()
     */
    public OperationalStringManager[] getOperationalStringManagers()  {
        return(backend.getOperationalStringManagers());
    }

    /* (non-Javadoc)
     * @see org.rioproject.monitor.DeployAdmin#getOperationalStringManager(java.lang.String)
     */
    public OperationalStringManager getOperationalStringManager(String name) 
        throws OperationalStringException {
        return(backend.getOperationalStringManager(name));
    }

    /* (non-Javadoc)
     * @see org.rioproject.monitor.ProvisionMonitorAdmin#getBackupInfo()
     */
    public PeerInfo[] getBackupInfo() {
        return(backend.getBackupInfo());
    }
    
}
