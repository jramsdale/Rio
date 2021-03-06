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
package org.rioproject.monitor;

import org.rioproject.associations.AssociationMatchFilter;
import org.rioproject.associations.AssociationDescriptor;
import org.rioproject.associations.AssociationType;
import org.rioproject.core.ServiceElement;
import org.rioproject.jsb.ServiceElementUtil;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility to match Associations to ServiceElements.
 *
 * @author Dennis Reedy
 */
public class AssociationMatcher {
    /** The Logger */
    static final Logger logger =
        Logger.getLogger(ProvisionMonitorImpl.LOGGER+".provision");

    /**
     * This method verifies whether the InstantiatorResource can support any
     * declared service colocation requirements
     *
     * @param sElem The ServiceElement
     * @param ir The InstantiatorResource
     *
     * @return Return true if the provided InstantiatorResource meets service
     * colocation requirements
     */
    static boolean meetsColocationRequirements(ServiceElement sElem,
                                               InstantiatorResource ir) {
        boolean provisionable = true;
        AssociationDescriptor[] aDescs = sElem.getAssociationDescriptors();
        for (AssociationDescriptor aDesc : aDescs) {
            boolean ok = false;
            if (aDesc.getAssociationType()==AssociationType.COLOCATED) {
                if (matches(aDesc, ir.getServiceElements())) {
                    break;
                }
            } else {
                ok = true;
            }
            if (!ok) {
                provisionable = false;
                break;
            }
        }
        return(provisionable);
    }

    /**
     * This method verifies whether the InstantiatorResource can support any
     * declared service opposed requirements
     *
     * @param sElem The ServiceElement
     * @param ir The InstantiatorResource
     * 
     * @return Return true if the provided InstantiatorResource meets service
     *         opposed requirements
     */
    static boolean meetsOpposedRequirements(ServiceElement sElem,
                                            InstantiatorResource ir) {
        return(meetsAssociatedRequirements(sElem,
                                           AssociationType.OPPOSED,
                                           ir,
                                           null));
    }
    /**
     * This method verifies whether the InstantiatorResource can support any
     * declared service isolation requirements
     *
     * @param sElem The ServiceElement
     * @param ir The InstantiatorResource
     * @param known An array of InstantiatorResource instances that contain the
     * ServiceElement, may be null
     *
     * @return Return true if the provided InstantiatorResource meets service
     *         isolation requirements
     */
    static boolean meetsIsolatedRequirements(ServiceElement sElem,
                                            InstantiatorResource ir,
                                            InstantiatorResource[] known) {
        return(meetsAssociatedRequirements(sElem,
                                           AssociationType.ISOLATED,
                                           ir,
                                           known));

    }

    /**
     * This method verifies whether the InstantiatorResource can support any
     * declared service associated requirements
     *
     * @param sElem The ServiceElement
     * @param type The AssociationType type
     * @param ir The InstantiatorResource
     * @param known An array of InstantiatorResource instances that contain the
     * ServiceElement, may be null
     *
     * @return Return true if the provided InstantiatorResource meets service
     *         declared requirements
     */
    private static boolean meetsAssociatedRequirements(ServiceElement sElem,
                                                       AssociationType type,
                                                       InstantiatorResource ir,
                                                       InstantiatorResource[] known) {
        boolean provisionable = true;
        StringBuffer errorLog = new StringBuffer();

        AssociationDescriptor[] aDescs =
            ServiceElementUtil.getAssociationDescriptors(sElem, type);

        /* Check in process elements, to see if they match any of the service's
         * opposed requirements */
        for (AssociationDescriptor aDesc : aDescs) {
            if (matches(aDesc, ir.getServiceElementsInprocess(sElem))) {
                provisionable = false;
                break;
            }
        }

        /* Check running elements, to see if they match any of the service's
         * opposed requirements */
        if (provisionable) {
            for (AssociationDescriptor aDesc : aDescs) {
                if (matches(aDesc, ir.getServiceElements())) {
                    provisionable = false;
                    break;
                }
            }
        }
        if (!provisionable) {
            String provType = sElem.getProvisionType().toString();
            errorLog.append("Do not allocate ")
                .append(provType)
                .append(" service " + "[")
                .append(sElem.getName())
                .append("] to ")
                .append(ir.getName())
                .append(" at [")
                .append(ir.getHostAddress())
                .append("], ");
            errorLog.append(type.toString()).append(
                ", services detected: ");
            for (int i = 0; i < aDescs.length; i++) {
                if (i > 0)
                    errorLog.append(", ");
                errorLog.append("[")
                    .append(i + 1)
                    .append("] ")
                    .append(aDescs[i].getName());
            }
        }

        /* Check if any in process or running elements have an opposed or
         * isolated requirement to the service that needs to be provisioned */
        if (provisionable) {
            StringBuffer b = new StringBuffer();
            int found = getCount(sElem, ir.getServiceElements(), type, b);
            if(found==0) {
                found = getCount(sElem,
                                 ir.getServiceElementsInprocess(sElem),
                                 type,
                                 b);
            }
            if (found > 0) {
                String provType = sElem.getProvisionType().toString();
                provisionable = false;
                errorLog.append("Do not allocate ")
                    .append(provType)
                    .append(" service " + "[")
                    .append(sElem.getName())
                    .append("] to ")
                    .append(ir.getName())
                    .append(" at [")
                    .append(ir.getHostAddress())
                    .append("], ")
                    .append("id ")
                    .append("[")
                    .append(ir.getInstantiatorUuid())
                    .append("], ")
                    .append(found);
                errorLog.append(" service(s) have ")
                    .append(type.toString())
                    .append(" associations: ");
                errorLog.append("{")
                    .append(b.toString())
                    .append("}");
            }
        }

        if(provisionable && known!=null && aDescs.length>0) {
            if(inKnownSet(ir, known))
                provisionable = false;
        }
        
        if (!provisionable && logger.isLoggable(Level.FINER))
            logger.finer(errorLog.toString());

        return (provisionable);
    }

    /**
     * Determine if the {@link InstantiatorResource} is in the set of known
     * InstantiatorResources. Used to check isolated associations.
     *
     * @param candidate The InstantiatorResource to check
     * @param knownOnes Array of known InstantiatorResource instances
     * @return If the candidate has a host address that is in the known set,
     * return true, otherwise return false
     */
    static boolean inKnownSet(InstantiatorResource candidate,
                              InstantiatorResource[] knownOnes) {
        boolean inKnownSet = false;
        if(candidate!=null && knownOnes!=null) {
            for (InstantiatorResource knownOne : knownOnes) {
                if (candidate.getHostAddress().equals(knownOne.getHostAddress())) {
                    inKnownSet = true;
                    break;
                }
            }
        }
        return(inKnownSet);
    }

    /**
     * Determine if the Association's AssociationMatchFilter matches a
     * ServiceElement
     *
     * @param descriptor The AssociationDescriptor
     * @param elems Array of ServiceElement instances
     * @return True if the AssociationFilter matches a ServiceElement
     */
    static boolean matches(AssociationDescriptor descriptor,
                           ServiceElement[] elems) {
        boolean matches = false;
        AssociationMatchFilter filter = getAssociationMatchFilter(descriptor);
        for (ServiceElement elem : elems) {
            if (filter.check(descriptor, elem)) {
                matches = true;
                break;
            }
        }
        return (matches);
    }

    /*
     * Get the AssociationMatchFilter for an AssociationDescriptor
     */
    private static AssociationMatchFilter
        getAssociationMatchFilter(AssociationDescriptor descriptor) {
        AssociationMatchFilter defaultFilter = new DefaultAssociationMatchFilter();
        AssociationMatchFilter filter;
        try {
            String matchFilter = descriptor.getAssociationMatchFilter();
            if(matchFilter!=null) {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                filter = (AssociationMatchFilter)cl.loadClass(matchFilter).newInstance();
            } else {
                filter = defaultFilter;
            }
        } catch(Exception e) {
            filter = defaultFilter;
            logger.log(Level.WARNING,
                       "Getting AssociationMatchFilter for association " +
                       "["+descriptor.toString()+"]",
                       e);
        }
        return(filter);
    }

    /*
     * Get matching ServiceElement count
     */
    private static int getCount(ServiceElement sElem,
                                ServiceElement[] elems,
                                AssociationType type,
                                StringBuffer b) {
        int found = 0;
        for (ServiceElement elem : elems) {
            AssociationDescriptor[] ads =
                ServiceElementUtil.getAssociationDescriptors(elem,
                                                             type);
            for (AssociationDescriptor ad : ads) {
                if (matches(ad, new ServiceElement[]{sElem})) {
                    if (found > 0)
                        b.append(", ");
                    found++;
                    b.append(elem.getName());         
                }
            }
        }
        return(found);
    }

    /**
     * Default association match filter, used during colocation and opposed
     * matching
     */
    static class DefaultAssociationMatchFilter implements AssociationMatchFilter {

        public boolean check(AssociationDescriptor descriptor,
                             ServiceElement element) {
            return(ServiceElementUtil.matchesServiceElement(
                       element,
                       descriptor.getName(),
                       descriptor.getInterfaceNames(),
                       descriptor.getOperationalStringName()));
        }
    }
}
