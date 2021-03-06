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

import net.jini.config.Configuration;

/**
 * A Watch for capturing elapsed time
 */
public class StopWatch extends ThresholdWatch implements StopWatchMBean {
    public static final String VIEW = "org.rioproject.watch.ResponseTimeCalculableView";
    /** Holds value of property startTime. */
    private long startTime;

    /**
     * Creates new Stop Watch
     * 
     * @param id the identifier for this watch
     */
    public StopWatch(String id) {
        super(id);
        setView(VIEW);
    }
    
    /**
     * Creates new StopWatch, creates and exports a
     * WatchDataSourceImpl if the WatchDataSource is null using the
     * Configuration object provided
     *
     * @param id The identifier for this watch
     * @param config Configuration object used for constructing a WatchDataSource
     */
    public StopWatch(String id, Configuration config) {
        super(id, config);
        setView(VIEW);
    }

    /**
     * Creates new Stop Watch
     * 
     * @param watchDataSource the watch data source associated with this watch
     * @param id the identifier for this watch
     */
    public StopWatch(WatchDataSource watchDataSource, String id) {
        super(watchDataSource, id);
        setView(VIEW);
    }

    /**
     * @see org.rioproject.watch.StopWatchMBean#startTiming
     */
    public void startTiming() {
        startTime = System.currentTimeMillis();
    }

    /**
     * @see org.rioproject.watch.StopWatchMBean#stopTiming
     */
    public void stopTiming() {
        long now = System.currentTimeMillis();
        setElapsedTime(now - startTime, now);
    }

    /**
     * @see org.rioproject.watch.StopWatchMBean#setElapsedTime(long)
     */
    public void setElapsedTime(long elapsed) {
        setElapsedTime(elapsed, System.currentTimeMillis());
    }

    /**
     * @see org.rioproject.watch.StopWatchMBean#setElapsedTime(long, long)
     */
    public void setElapsedTime(long elapsed, long now) {
        addWatchRecord(new Calculable(id, (double)elapsed, now));
    }

    /**
     * @see org.rioproject.watch.StopWatchMBean#getStartTime
     */
    public long getStartTime() {
        return (startTime);
    }

    /**
     * @see org.rioproject.watch.StopWatchMBean#setStartTime(long)
     */
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
}
