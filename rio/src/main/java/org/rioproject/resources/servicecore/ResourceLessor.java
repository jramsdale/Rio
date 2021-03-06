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
package org.rioproject.resources.servicecore;

import com.sun.jini.landlord.LeasedResource;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.id.Uuid;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract class to manage the service's leased resources.
 *
 * @author Dennis Reedy
 */
public abstract class ResourceLessor {
    /** A hash of resources to cookies */
    private final Map<Uuid, LeasedResource> resources =
        new ConcurrentHashMap<Uuid, LeasedResource>();
    /** A Thread which will clean up stale leases */
    private Thread reaper = null;
    /** A LinkedList of LeaseListener objects */
    private List<LeaseListener> listeners = new LinkedList<LeaseListener>();
    /** Component for getting the Logger */
    static final String COMPONENT_NAME = "org.rioproject.resources.servicecore";
    /** The Logger */
    Logger logger = Logger.getLogger(COMPONENT_NAME);

    /**
     * Check to make sure that the LeasedResource lease has not expired yet <br>
     *
     * @param resource The LeasedResource
     *
     * @return Returns true if the lease on the passed resource has not expired
     * yet
     */
    public boolean ensure(LeasedResource resource) {
        return(resource.getExpiration() > currentTime());
    }

    /**
     * Create a new lease <br>
     * 
     * @param resource to be leased
     * @param duration Time requested for <code>Lease</code>
     *
     * @throws LeaseDeniedException If the lease has been denied
     * @return A new Lease
     */
    public abstract Lease newLease(LeasedResource resource, long duration)
        throws LeaseDeniedException;

    /**
     * Remove a leased resource from the list of managed leases. <br>
     * 
     * @param resource ServiceResource to remove
     *
     * @return true if the lease was removed
     */
    public boolean remove(ServiceResource resource) {
        return (remove(resource.getCookie()));
    }

    /**
     * Remove all leased resources .<br>
     */
    public void removeAll() {
        LeasedResource[] resources = getLeasedResources();
        for (LeasedResource resource : resources)
            remove(resource.getCookie());
    }

    /**
     * Remove a leased resource from the list of managed leases. <br>
     * 
     * @param cookie Object to remove
     *
     * @return boolean True if removed false if not removed
     */
    boolean remove(Uuid cookie) {
        LeasedResource resource;
        boolean removed = false;
        synchronized(resources) {
            resource = resources.remove(cookie);
        }
        if(resource != null) {
            notifyLeaseRemoval(resource);
            removed = true;
        }
        return (removed);
    }

    /**
     * Add a LeaseListener <br>
     * 
     * @param listener the LeaseListener to add
     */
    public void addLeaseListener(LeaseListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a LeaseListener <br>
     * 
     * @param listener the LeaseListener to remove
     */
    public void removeLeaseListener(LeaseListener listener) {
        listeners.remove(listener);
    }

    /**
     * @return the total number of service resources that have been leased
     */
    public int total() {
        int size;
        synchronized(resources) {
            size = resources.size();
        }
        return (size);
    }

    /**
     * Add a LeasedResource for a new Lease or renewing a Lease
     *
     * @param resource The resource to add or update
     *
     * @throws IllegalArgumentException if the resource is null
     */
    protected void addLeasedResource(LeasedResource resource) {
        if(resource == null)
            throw new IllegalArgumentException("resource is null");
        synchronized(this) {
            if(reaper==null) {
                reaper = new LeaseReaper();
                reaper.setDaemon(true);
                reaper.start();
            }
        }
        synchronized(resources) {
            resources.put(resource.getCookie(), resource);
        }
    }

    /**
     * Get a LeasedResource
     *
     * @param cookie The Uuid of the LeasedResource to get
     *
     * @return The LeasedResource for the Uuid
     *
     * @throws IllegalArgumentException if the cookie is null
     */
    protected LeasedResource getLeasedResource(Uuid cookie) {
        if(cookie == null)
            throw new IllegalArgumentException("cookie is null");
        LeasedResource resource;
        synchronized(resources) {
            resource = resources.get(cookie);
        }
        return(resource);
    }
    /**
     * This method returns a snapshot of the LeasedResource objects that
     * this ResourceLessor is managing
     *
     * @return An array of LeasedResource elements. If no LeasedResource
     * objects are found return a zero-length array. A new array is returned
     * each time
     */
    protected LeasedResource[] getLeasedResources() {
        LeasedResource[] leasedResources;
        synchronized(resources) {
            Collection<LeasedResource> c = resources.values();
            leasedResources = c.toArray(new LeasedResource[c.size()]);
        }
        return (leasedResources);
    }

    /**
     * Notify LeaseListener instances of a new registration
     * 
     * @param resource The LeasedResource
     */
    protected void notifyLeaseRegistration(LeasedResource resource) {
        for (LeaseListener listener : listeners)
            listener.register(resource);
    }

    /**
     * Notify LeaseListener instances of a lease renewal
     * 
     * @param resource The LeasedResource
     */
    protected void notifyLeaseRenewal(LeasedResource resource) {
        for (LeaseListener listener : listeners)
            listener.renewed(resource);
    }

    /**
     * Notify LeaseListener instances of a lease expiration
     * 
     * @param resource The LeasedResource
     */
    protected void notifyLeaseExpiration(ServiceResource resource) {
        for (LeaseListener listener : listeners)
            listener.expired(resource);
    }

    /**
     * Notify LeaseListener instances of a lease removal
     * 
     * @param resource The LeasedResource
     */
    protected void notifyLeaseRemoval(LeasedResource resource) {
        for (LeaseListener listener : listeners)
            listener.removed(resource);
    }

    /**
     * Stop and clean up all resources
     */
    protected void stop() {
        if(reaper!=null)
            reaper.interrupt();
        reaper = null;
        removeAll();
    }

    /**
     * Method that provides some notion of the current time in milliseconds
     * since the beginning of the epoch. Default implementation calls
     * System.currentTimeMillis()
     *
     * @return The current time
     */
    protected long currentTime() {
        return (System.currentTimeMillis());
    }
    
    /**
     * Clean up leases that have not been renewed. Check every 30 seconds for
     * stale leases
     */
    protected class LeaseReaper extends Thread {
        public LeaseReaper() {
            super("LeaseReaper");
        }
        
        public void run() {
            while (reaper != null || !isInterrupted()) {
                try {
                    Thread.sleep(30 * 1000L);
                } catch(InterruptedException e) {
                    break;
                }
                Set<Entry<Uuid,LeasedResource>> mapEntries;
                synchronized(resources) {
                    mapEntries = resources.entrySet();
                }
                for (Entry<Uuid, LeasedResource> entry : mapEntries) {
                    LeasedResource lr = entry.getValue();
                    if (!ensure(lr)) {
                        if (logger.isLoggable(Level.FINE))
                            logger.log(Level.FINE,
                                       "Lease expired for resource {0}, cookie {1}",
                                       new Object[]{
                                           ((ServiceResource) lr).getResource(),
                                           lr.getCookie()});
                        remove(lr.getCookie());
                        notifyLeaseExpiration((ServiceResource)lr);
                    }
                }
            }
        }
    }
}
