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
package org.rioproject.core;

import net.jini.id.Uuid;
import org.rioproject.core.provision.DeploymentMap;
import org.rioproject.core.provision.ServiceStatement;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.Map;

/**
 * The <tt>OperationalStringManager</tt> defines the semantics for a service that can
 * manage OperationalString objects
 * 
 * @see org.rioproject.core.OperationalString
 * @see org.rioproject.core.ServiceElement
 *
 * @author Dennis Reedy
 */
public interface OperationalStringManager extends Remote {
    /**
     * Get the OperationalString the <tt>OperationalStringManager</tt> is managing
     * 
     * @return The OperationalString
     * @throws RemoteException If communication errors occur
     */
    OperationalString getOperationalString() throws RemoteException;

    /**
     * Whether the <tt>OperationalStringManager</tt> is the active managing
     * <tt>OperationalStringManager</tt> for the OperationalString. The managing
     * <tt>OperationalStringManager</tt> will actively respond to scenarios where
     * service's contained within this OperationalString need to be allocated,
     * updated, relocated, removed or added. If the <tt>OperationalStringManager</tt> is
     * not the managing <tt>OperationalStringManager</tt>, it will observe and record
     * OperationalString transitions but not act on them.
     * 
     * @return True if managing, false otherwise
     *
     * @throws RemoteException If communication errors happen
     */
    boolean isManaging() throws RemoteException;

    /**
     * Set the <tt>OperationalStringManager</tt> managing status based on the active
     * parameter
     * 
     * @param active If true, the <tt>OperationalStringManager</tt> is the active
     * managing <tt>OperationalStringManager</tt> for the OperationalString. The managing
     * <tt>OperationalStringManager</tt> will actively respond to scenarios where
     * service's contained within this OperationalString need to be allocated,
     * updated, relocated, removed or added. If the <tt>OperationalStringManager</tt> is
     * not the managing <tt>OperationalStringManager</tt>, it will observe and record
     * OperationalString transitions but not act on them.
     *
     * @throws RemoteException If communication errors happen
     */
    void setManaging(boolean active) throws RemoteException;

    /**
     * Get the deployment Date history
     * 
     * @return An array of Date objects documenting the date & time the
     * OperationalString has been deployed. If has never been deployed (has been
     * scheduled) then a zero-length array will be returned.
     *
     * @throws RemoteException If communication errors happen
     */
    Date[] getDeploymentDates() throws RemoteException;

    /**
     * Update the OperationalString that the <tt>OperationalStringManager</tt> is managing.
     * This involves updating ServiceElement instances, which may include the additon 
     * and or removal of ServiceElements in the OperationalString 
     * 
     * If the input OperationalString includes nested OperationalStrings, and the 
     * nested OperationalString is not deployed, the nested OperationalString will 
     * be deployed
     * 
     * If the nested OperationalString has been deployed and is referenced by only 
     * the OperationalString being updated, the nested OperationalString will be 
     * updated as well 
     * 
     * @param opstring The OperationalString to update
     * @return If there are errors updating the OperationalString a Map will
     * be returned with name value pairs associating the service and
     * corresponding exception attempting to update the OperationalString
     *
     * @throws OperationalStringException If there are problems updating the
     * the OperationalString
     * @throws RemoteException If communication errors happen
     */
    Map<String, Throwable> update(OperationalString opstring)
        throws OperationalStringException, RemoteException;

    /**
     * This method returns the ServiceElement object for a requested service
     * based on a service's proxy
     * 
     * @param proxy The proxy for the Service
     * 
     * @return The ServiceElement for the query. If there is no matching
     * ServiceElement, a null is returned
     *
     * @throws RemoteException If communication errors occur
     */
    ServiceElement getServiceElement(Object proxy) throws RemoteException;

    /**
     * This method returns the ServiceElement object for a requested service
     * based on the array of interface (or proxy) classes the service implements
     * as an array of String objects and an optional service name. The
     * <tt>OperationalStringManager</tt> will locate the ServiceElement by matching the
     * interface (or proxy) names provided and the name (if not null) of the
     * supplied OperationalString.
     * 
     * @param interfaces Array of interface (or proxy) classes the service
     * implements as an array of String objects
     * @param name The name of the Service
     *
     * @return The ServiceElement for the query. If there is no matching
     * ServiceElement, a null is returned
     *
     * @throws RemoteException If communication errors occur
     */
    ServiceElement getServiceElement(String[] interfaces, String name)
        throws RemoteException;

    /**
     * This method will add a ServiceElement to an OperationalString. Based on
     * the attributes of the ServiceElement, ServiceBean instances will be
     * provisioned to available compute resources based on the capability of the
     * compute resource to meet the operational criteria criteria of the
     * ServiceElement
     * 
     * @param sElem The ServiceElement to add
     * @throws OperationalStringException If the service described by the
     * ServiceElement already exists, or there are problems adding the
     * ServiceElement
     * @throws RemoteException If communication errors occur
     */
    void addServiceElement(ServiceElement sElem)
        throws OperationalStringException, RemoteException;

    /**
     * This method will add a ServiceElement to an OperationalString. Based on
     * the attributes of the ServiceElement, ServiceBean instances will be
     * provisioned to available compute resources based on the capability of the
     * compute resource to meet the operational criteria criteria of the
     * ServiceElement
     *
     * @param sElem The ServiceElement to add
     * @param listener If not <code>null</code>, the
     * {@link org.rioproject.core.ServiceProvisionListener} will be
     * notified on the result of the attempt to instantiate each service
     * instance. 
     * @throws OperationalStringException If the service described by the
     * ServiceElement already exists, or there are problems adding the
     * ServiceElement
     * @throws RemoteException If communication errors occur
     */
    void addServiceElement(ServiceElement sElem, ServiceProvisionListener listener)
        throws OperationalStringException, RemoteException;

    /**
     * This method will modify the ServiceElement attributes of a ServiceElement
     * in the <tt>OperationalStringManager</tt>. The
     * <tt>OperationalStringManager</tt> will locate deployed ServiceElement
     * instances, and apply the attributes to those running instances.
     * If the ServiceElement is not found it will be added and deployed.
     * 
     * @param sElem The ServiceElement to update
     * @throws OperationalStringException If there are problems updating the
     * ServiceElement
     * @throws RemoteException If communication errors occur
     */
    void update(ServiceElement sElem)
        throws OperationalStringException, RemoteException;

    /**
     * This method will remove a ServiceElement from an OperationalString and
     * optionally terminate all service instances that have been provisioned
     * from the ServiceElement description
     * 
     * @param sElem The ServiceElement to remove
     * @param destroy If true, destroy all services upon removal, otherwise
     * just remove
     * @throws OperationalStringException If the ServiceElement is null or not
     * being managed by the <tt>OperationalStringManager</tt>
     * @throws RemoteException If communication errors occur
     */
    void removeServiceElement(ServiceElement sElem, boolean destroy)
        throws OperationalStringException, RemoteException;

    /**
     * Get the ServiceBeanInstance objects for a ServiceElement
     * 
     * @param sElem The ServiceElement
     *
     * @return An array of ServiceBeanInstance objects found for the
     * ServiceElement. If there are no ServiceBeanInstances, a zero-length
     * array is returned. A new array is allocated each time.
     *
     * @throws OperationalStringException If the ServiceElement is unknown to the
     * <tt>OperationalStringManager</tt>
     * @throws RemoteException If communication errors occur
     */
    ServiceBeanInstance[] getServiceBeanInstances(ServiceElement sElem)
        throws OperationalStringException, RemoteException;

    /**
     * Relocate (move) a ServiceBean instance to another
     * {@link org.rioproject.core.provision.ServiceBeanInstantiator}. If the
     * relocating request cannot be carried out, the request will not be
     * submitted for future processing.
     *
     * @param instance The ServiceBeanInstance to relocate
     * @param listener If not <code>null</code>, the
     * {@link org.rioproject.core.ServiceProvisionListener} will be
     * notified on the result of the attempt to relocate the service instance.
     * @param uuid The Uuid of the
     * {@link org.rioproject.core.provision.ServiceBeanInstantiator} (Cybernode)
     * to relocate to. If this parameter is null, the <tt>OperationalStringManager</tt>
     * will determine a suitable compute resource
     *
     * @throws OperationalStringException If the ServiceElement is not being
     * managed by the <tt>OperationalStringManager</tt>, or the service requesting
     * relocation is not a
     * {@link org.rioproject.core.ServiceElement.ProvisionType#DYNAMIC} service
     * @throws NullPointerException if the instance is <code>null</code>
     * @throws RemoteException If communication errors occur
     */
    void relocate(ServiceBeanInstance instance,
                  ServiceProvisionListener listener,
                  Uuid uuid)
        throws OperationalStringException, RemoteException;

    /**
     * Increment (increase) the number of instances by one. This will cause the 
     * provisioning of a ServiceBean to an available compute resource which meets 
     * the operational criteria specified by the ServiceElement.
     * 
     * If the increment request cannot be accomplished (no available compute 
     * resources that meet operational requirements of the service), and the 
     * {@link org.rioproject.core.ServiceProvisionListener} parameter is not null, 
     * the {@link org.rioproject.core.ServiceProvisionListener} will be notified of 
     * the result. Additionally if the increment request cannot be carried
     * out, the request will be submitted for future processing
     * 
     * @param sElem The ServiceElement instance to increment. This parameter
     * is used to match a ServiceElement being managed by the
     * <tt>OperationalStringManager</tt>
     * @param permanent If the increment request should be considered permanent. If 
     * set to false, the number of service instances may vary over time
     * @param listener If not null, the ServiceProvisionListener will be
     * notified on the result of the attempt to increment the amount of service
     * instances.
     * @throws OperationalStringException If the ServiceElement is not being
     * managed by the <tt>OperationalStringManager</tt> (or the <tt>OperationalStringManager</tt>
     * is not the managing <tt>OperationalStringManager</tt> for the OperationalString)
     * @throws RemoteException If communication errors occur
     */
    void increment(ServiceElement sElem,
                   boolean permanent,
                   ServiceProvisionListener listener)
        throws OperationalStringException, RemoteException;

    /**
     * Decrement (decrease the number of) and remove a specific ServiceBean
     * instance from the OperationalString.
     * 
     * @param instance The ServiceBeanInstance
     * @param mandate The mandate parameter is processed as follows:
     * <ul
     * <li>If set to <tt>true</tt>, and the number of running services
     * is equal to the number of services to maintain, the number of services to
     * maintain will be decremented by 1.
     * <li>If set to <tt>false</tt>, and the number of service instances running
     * is greater then the number of services to maintain, the number
     * of services to maintain will be decremented by 1.
     * <li>If set to <tt>false</tt>, and the number of running service is
     * equal to the number of services to maintain, the decrement will not be allowed.
     * </ul>
     * @param destroy If true, destroy the ServiceBean upon decrementing, otherwise
     * just remove the service instance from being a managed service.
     * @throws OperationalStringException If the ServiceElement is not being
     * managed by the <tt>OperationalStringManager</tt>
     * @throws RemoteException If communication errors occur
     */
    void decrement(ServiceBeanInstance instance,
                   boolean mandate,
                   boolean destroy)
        throws OperationalStringException, RemoteException;

    /**
     * Get the number of pending service provision requests. This method will only 
     * take action if the ServiceElement provision type is
     * {@link org.rioproject.core.ServiceElement.ProvisionType#DYNAMIC}.
     * Any other provisiontype will be ignored
     *  
     * @param sElem The ServiceElement instance to query. This parameter
     * is used to match a ServiceElement being managed by the
     * <tt>OperationalStringManager</tt>
     * 
     * @throws RemoteException If communication errors occur
     * 
     * @return The number of pending requests. If the ServiceElement 
     * provision type is not
     * {@link org.rioproject.core.ServiceElement.ProvisionType#DYNAMIC}
     * <tt>OperationalStringManager</tt> is not
     * the managing <tt>OperationalStringManager</tt>, -1 will be returned
     */
    int getPendingCount(ServiceElement sElem) throws RemoteException;

    /**
     * Trim (remove) service provision requests which are pending allocation. This
     * method will only take action if the ServiceElement provision type is
     * {@link org.rioproject.core.ServiceElement.ProvisionType#DYNAMIC}. Any other
     * provision type will be ignored
     * 
     * @param sElem The {@link org.rioproject.core.ServiceElement} instance to
     * trim. This parameter is used to match a <tt>ServiceElement</tt> being
     * managed by the <tt>OperationalStringManager</tt>
     * @param trimUp The number of pending requests to trim. The number of
     * pending requests to trim will be determined as follows :
     * <ul>
     * <li>If the trimUp value is -1, then trim the all pending requests 
     * <li>If the trimUp value is not -1, the number of pending requests to trim will
     * be calaculated as follows:
     * <br>
     * <code>Math.min((planned-actual), trimUp)</code>
     * <br>
     * Where planned is the 
     * property obtained from the managed ServiceElement, actual is the number of 
     * active (deployed) services, and trimUp is the input value.
     * </ul> 
     * 
     * @throws OperationalStringException If the ServiceElement is not being
     * managed by the <tt>OperationalStringManager</tt>
     * @throws RemoteException If communication errors occur
     * @return The number of pending requests that were trimmed. The value will be
     * the new managed ServiceElement planned property. If the ServiceElement 
     * provision type is not
     * {@link org.rioproject.core.ServiceElement.ProvisionType#DYNAMIC}, -1 will
     * be returned
     */
    int trim(ServiceElement sElem, int trimUp)
        throws OperationalStringException, RemoteException;

    /**
     * Update a ServiceBeanInstance
     * 
     * @param instance The ServiceBeanInstance to update. The configuration
     * and properties of the instance are updated, ensuring if the instance
     * is (re-)allocated or relocated, the settings are applied to the new i
     * nstance.
     * @throws OperationalStringException If the ServiceElement is not being
     * managed by the <tt>OperationalStringManager</tt>
     * @throws RemoteException If communication errors occur
     */
    void update(ServiceBeanInstance instance)
        throws OperationalStringException, RemoteException;

    /**
     * Redeploy an OperationalString, ServiceElement or ServiceBeanInstance. This
     * method will terminate then reallocate services based on the following criteria:
     * 
     * <ul>
     * <li>If both the ServiceElement and ServiceBeanInstance parameters are null, the
     * OperationalString will be redeployed. All services in the OperationalString 
     * will be terminated then redeployed
     * <li>If the ServiceElement parameter is not null, this method will terminate
     * then reallocate all deployed services identified by the ServiceElement
     * <li>If the ServiceBeanInstance is not null and the ServiceElement is not null,
     * this method will terminate and then reallocate the service identified by the
     * ServiceBeanInstance
     * </ul>
     * 
     * @param sElem If not null, the ServiceElement to redeploy
     * @param instance If not null, and the sElem param is not null, the 
     * ServiceBeanInstance to redeploy
     * @param clean If set to true, the service will be allocated using the
     * ServiceElement configuration, not the ServiceBeanInstance configuration
     * @param delay The amount of time (in milliseconds) to wait until the
     * redeployment is performed. A value > 0 will result in scheduling the
     * redeployment
     * @param listener If not null, the ServiceProvisionListener will be
     * notified as each service is redeployed
     * 
     * @throws OperationalStringException If there are errors redeploying
     * @throws RemoteException If communication errors occur
     */
    void redeploy(ServiceElement sElem,
                  ServiceBeanInstance instance,
                  boolean clean,
                  long delay,
                  ServiceProvisionListener listener)
        throws OperationalStringException, RemoteException;

    /**
     * Redeploy an OperationalString, ServiceElement or ServiceBeanInstance. This
     * method will terminate then reallocate services based on the following criteria:
     *
     * <ul>
     * <li>If both the ServiceElement and ServiceBeanInstance parameters are null, the
     * OperationalString will be redeployed. All services in the OperationalString
     * will be terminated then redeployed
     * <li>If the ServiceElement parameter is not null, this method will terminate
     * then reallocate all deployed services identified by the ServiceElement
     * <li>If the ServiceBeanInstance is not null and the ServiceElement is not null,
     * this method will terminate and then reallocate the service identified by the
     * ServiceBeanInstance
     * </ul>
     *
     * @param sElem If not null, the ServiceElement to redeploy
     * @param instance If not null, and the sElem param is not null, the
     * ServiceBeanInstance to redeploy
     * @param clean If set to true, the service will be allocated using the
     * ServiceElement configuration, not the ServiceBeanInstance configuration
     * @param sticky If set to true, the service(s) will be re-allocated to the
     * same {@link org.rioproject.core.provision.ServiceBeanInstantiator} that
     * had instantiated the service. If the service has requirements that cannot be
     * met by the compute resource hosting the
     * {@link org.rioproject.core.provision.ServiceBeanInstantiator}, the
     * service will be allocated on the next available compute resource that
     * meets the requirements of the service.
     * @param delay The amount of time (in milliseconds) to wait until the
     * redeployment is performed. A value > 0 will result in scheduling the
     * redeployment
     * @param listener If not null, the ServiceProvisionListener will be
     * notified as each service is redeployed
     *
     * @throws OperationalStringException If there are errors redeploying
     * @throws RemoteException If communication errors occur
     */
    void redeploy(ServiceElement sElem,
                  ServiceBeanInstance instance,
                  boolean clean,
                  boolean sticky,
                  long delay,
                  ServiceProvisionListener listener)
        throws OperationalStringException, RemoteException;    

    /**
     * Get the {@link org.rioproject.core.provision.ServiceStatement}s for
     * all {@link ServiceElement}s in the OperationalString
     *
     * @return An array of <tt>ServiceStatement</tt> instances.
     *
     * @throws RemoteException If communication errors occur
     */
    ServiceStatement[] getServiceStatements() throws RemoteException;

    /**
     * Get the {@link DeploymentMap} for services in this OperationalString
     *
     * @return A {@link DeploymentMap} for services in this OperationalString
     *
     * @throws RemoteException If communication errors occur
     */
    DeploymentMap getDeploymentMap() throws RemoteException;
}
