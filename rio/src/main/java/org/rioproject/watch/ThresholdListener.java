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
package org.rioproject.watch;

/**
 * Listener for notification that a threshold has been crossed. A
 * ThresholdManager will notify a ThresholdListener if a Threshold has been
 * crossed. The ThresholdListener may take corrective actions to deal with the
 * threshold condition, and/or generate ThresholdEvent notifications to
 * interested EventConsumer instances
 */
public interface ThresholdListener {
    /**
     * Get the ID of the ThresholdWatch the ThresholdListener is associated to
     *
     * @return The identifier (ID) of the ThresholdWatch the
     * ThresholdListener is asscociated to
     */
    String getID();

    /**
     * Set the ThresholdManager and connect to the ThresholdManager
     *
     * @param thresholdManager The ThresholdManager to connect to
     */
    void setThresholdManager(ThresholdManager thresholdManager);

    /**
     * Notify for a threshold event
     *
     * @param calculable The current metric
     * @param thresholdValues The current thresholds
     * @param type The type of threshold event, breached or cleared
     */
    void notify(Calculable calculable, ThresholdValues thresholdValues, int type);
}
