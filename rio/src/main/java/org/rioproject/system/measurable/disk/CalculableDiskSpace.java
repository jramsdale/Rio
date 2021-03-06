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
package org.rioproject.system.measurable.disk;

import org.rioproject.watch.Calculable;

/**
 * A Calculable used to collect DiskSpace utilization
 *
 * @author Dennis Reedy
 */
public class CalculableDiskSpace extends Calculable {
    static final long serialVersionUID = 1L;
    private DiskSpaceUtilization diskSpaceUtilization;

    /** 
     * Creates new CalculableDiskSpace
     *
     * @param id the identifier for this Calculable record
     * @param diskSpaceUtilization The DiskSpaceUtilization
     * @param when the time when the recorded utilization was captured
     */
    public CalculableDiskSpace(String id,
                               DiskSpaceUtilization diskSpaceUtilization,
                               long when) {
        super(id, diskSpaceUtilization.getValue(), when);
        this.diskSpaceUtilization = diskSpaceUtilization;
    }

    /** 
     * Getter for property capacity.
     *
     * @return Value of property capacity.
     */
    public double getCapacity() {
        return(diskSpaceUtilization==null?-1:diskSpaceUtilization.getCapacity());
    }

    /**
     * Getter for property available.
     *
     * @return Value of property available.
     */
    public double getAvailable() {
        return(diskSpaceUtilization==null?-1:diskSpaceUtilization.getAvailable());
    }

    /**
     * Get the DiskSpaceUtilization
     *
     * @return DiskSpaceUtilization
     */
    public DiskSpaceUtilization getDiskSpaceUtilization() {
        return diskSpaceUtilization;
    }

    /**
     * Returns a string representation of the object.
     *
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("CalculableDiskSpace {").
            append("capacity: "+getCapacity()).append(", ").
            append("available: "+getAvailable()).append(", ").
            append("}");
        return sb.toString();
    }



    /**
     * Gets an archival representation for this Calculable
     *
     * @return a string representation in archive format
     */
    public String getArchiveRecord() {
        return(getId() + '|' + getValue() + '|' + getCapacity() +
            '|' + getAvailable() + '|' + getWhen());
    }
}
