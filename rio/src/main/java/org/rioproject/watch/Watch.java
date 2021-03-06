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
import net.jini.config.EmptyConfiguration;
import org.rioproject.jmx.JMXUtil;
import org.rioproject.resources.util.ThrowableUtil;

import javax.management.openmbean.*;
import java.beans.IntrospectionException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Watch provides a mechanism to collect information and associate it to a
 * WatchDataSource
 */
public class Watch implements WatchMBean {
    /** The WatchDataSource associated with this Watch. */
    protected WatchDataSource watchDataSource;
    /** The identifier for this watch. */
    protected String id;
    /** The WatchDataSourceImpl, this may be null if not created by the Watch */
    protected WatchDataSourceImpl localRef;
    /** Use for configuration and logger */
    protected static final String COMPONENT = "org.rioproject.watch";
    /** A Logger */
    protected static final Logger logger = Logger.getLogger(COMPONENT);
    /** Default View class */
    public static final String DEFAULT_VIEW = COMPONENT+".DefaultCalculableView";
    /** The view */
    private String view = DEFAULT_VIEW;
    /** The configuration*/
    private Configuration config;

    /**
     * Creates new Watch, creates and exports a WatchDataSourceImpl
     * 
     * @param id The identifier for this watch
     */
    public Watch(String id) {
        this(id, EmptyConfiguration.INSTANCE);
    }

    /**
     * Creates a new Watch, creates and exports a WatchDataSourceImpl if the
     * WatchDataSource is null using the Configuration object provided
     * 
     * @param id The identifier for this watch
     * @param config Configuration object used for constructing a
     * WatchDataSource
     */
    public Watch(String id, Configuration config) {
        if(id == null)
            throw new IllegalArgumentException("id is null");
        if(id.equals(""))
            throw new IllegalArgumentException("id must not be an empty string");
        if(config==null)
            throw new IllegalArgumentException("config is null");
        this.id = id;
        this.config = config;
        try {
            WatchDataSource wds =
                (WatchDataSource) config.getEntry(COMPONENT,
                                                  "watchDataSource",
                                                  WatchDataSource.class,
                                                  null);
            if(wds==null)
                wds = new WatchDataSourceImpl(); 

            doSetWatchDataSource(wds);

        } catch(Throwable t) {
            logger.log(Level.WARNING,
                       "Creating WatchDataSourceImpl for Watch [" + id + "]",
                       t);
        }
    }

    /**
     * Creates new Watch
     * 
     * @param id the identifier for this watch
     * @param watchDataSource the watch data source associated with this watch
     */
    public Watch(WatchDataSource watchDataSource, String id) {
        if(id == null)
            throw new IllegalArgumentException("id is null");
        if(watchDataSource==null)
            throw new IllegalArgumentException("watchDataSource is null");
        this.id = id;
        doSetWatchDataSource(watchDataSource);
        this.watchDataSource = watchDataSource;
    }

    /**
     * Getter for property watchDataSource.
     * 
     * @return Value of property watchDataSource.
     */
    public WatchDataSource getWatchDataSource() {
        return watchDataSource;
    }

    /**
     * Sets the WatchDataSource for the Watch.
     * 
     * @param wds The new WatchDataSource. The WatchDataSource will
     * have the id, configuration and view properties injected, and will also
     * be initialized.
     */
    public void setWatchDataSource(WatchDataSource wds) {
        if(wds == null) {
            if(logger.isLoggable(Level.FINEST))
                logger.finest("WatchDataSource is null for Watch=" + id);
            return;
        }
        doSetWatchDataSource(wds);
        this.watchDataSource = wds;
    }

    /*
     * Setup WatchDataSource
     */
    void doSetWatchDataSource(WatchDataSource wds) {
        this.watchDataSource = wds;
        if(localRef!=null) {
            localRef.unexport(true);
            localRef = null;
        }        

        try {
            if(config!=null)
                watchDataSource.setConfiguration(config);
            watchDataSource.setID(getId());
            watchDataSource.setView(getView());
            if(wds instanceof WatchDataSourceImpl) {
                localRef = (WatchDataSourceImpl)wds;
            }
            watchDataSource.initialize();
            if(localRef!=null) {
                watchDataSource = localRef.getProxy();
            }
        } catch(RemoteException e) {
            logger.log(Level.WARNING, "Setting WatchDataSource properties", e);
            watchDataSource=null;
            localRef = null;
        }
    }

    /**
     * Get the view for the Watch
     *
     * @return The view for the Watch
     */
    public String getView() {
        return (view);
    }

    /**
     * Set the view for the Watch
     * 
     * @param viewClass Fully qualified classname, suitable for use with
     * Class.forName(), of the class to use to visualize the Watch
     */
    public void setView(String viewClass) {
        if(viewClass == null)
            throw new IllegalArgumentException("viewClass is null");
        view = viewClass;
        /* Try direct reference first */
        if(localRef != null)
            localRef.setView(viewClass);
        else {
            if(watchDataSource != null) {
                try {
                    watchDataSource.setView(viewClass);
                } catch(RemoteException e) {
                    logger.log(Level.WARNING,
                               "Setting View class for Watch ["+getId()+"]",
                               e);
                }
            } else {
                logger.warning("WatchDataSource is null for Watch " + getId());
            }
        }
    }

    /**
     * @see org.rioproject.watch.WatchMBean#getId
     */
    public String getId() {
        return (id);
    }

    /**
     * @see org.rioproject.watch.WatchMBean#getLastCalculableValue
     */
    public double getLastCalculableValue() {
        double value = 0;
        try {
            Calculable lastCalculable = watchDataSource.getLastCalculable();
            if(lastCalculable != null) {
                value = lastCalculable.getValue();
            }
        } catch(Exception e) {
            logger.log(Level.WARNING,
                       "Getting last calculable",
                       e);
        }
        return(value);
    }

    /**
     * @see org.rioproject.watch.WatchMBean#getCalculables
     */
    @SuppressWarnings("unchecked")
    public TabularData getCalculables(){
        try {
            Calculable[] calculables = watchDataSource.getCalculable();
            if (calculables == null || calculables.length == 0) {
                if(logger.isLoggable(Level.FINE))
                    logger.fine("No Calculables Available From Data Source:" +
                                getId());
                return null;
            }
            CompositeType type = JMXUtil.createCompositeType(
                    JMXUtil.toMap(calculables[0]),
                    "Calculable",
                    "Calculable"
            );
            TabularType tabularType = new TabularType(
                    "Calculables",
                    "Calculables",
                    type,
                    new String[]{"when"}
            );
            TabularDataSupport tabularDataSupport =
                    new TabularDataSupport(tabularType);
            for (Calculable calculable : calculables) {
                CompositeData compositeData =
                    new CompositeDataSupport(type,
                                             JMXUtil.toMap(calculable));
                tabularDataSupport.put(compositeData);
            }
            return tabularDataSupport;
        } catch (OpenDataException e) {
            logger.log(Level.WARNING, e.toString(), e);
        } catch (IntrospectionException e) {
            logger.log(Level.WARNING, e.toString(), e);
        } catch (IllegalAccessException e) {
            logger.log(Level.WARNING, e.toString(), e);
        } catch (InvocationTargetException e) {
            logger.log(Level.WARNING, e.toString(), e);
        } catch (RemoteException e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
        return null;
    }

    /**
     * @see org.rioproject.watch.WatchMBean#clear
     */
    public void clear(){
        try {
            watchDataSource.clear();
        } catch (RemoteException e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    /**
     * Add a watch record to the history
     * 
     * @param calc the Calculable record to be added
     */
    public void addWatchRecord(Calculable calc) {
        /* Try direct reference first */
        if(localRef != null) {
            localRef.addCalculable(calc);
            return;
        }
        /* Use proxy */
        if(watchDataSource == null) {
            logger.warning("WatchDataSource is null for Watch " + getId());
            return;
        }
        try {
            watchDataSource.addCalculable(calc);
        } catch(RemoteException e) {
            //logger.log(Level.WARNING,
            //           "WatchDataSource not available for Watch "+ getId(),
            //           e);
            Throwable cause = ThrowableUtil.getRootCause(e);
            logger.warning("WatchDataSource not available for Watch="+
                           getId()+", "+
                           cause.getClass().getName()+": "+cause.getMessage());
        }
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * 
     * @param obj the object to compare to this one
     * @return true if the objects are equal
     */
    public boolean equals(Object obj) {
        if(obj instanceof Watch) {
            if(getId() == null && ((Watch)obj).getId() == null)
                return (true);
            else if(getId() == null || ((Watch)obj).getId() == null)
                return (false);
            else
                return (getId().equals(((Watch)obj).getId()));
        }
        return (false);
    }

    /**
     * Returns a hash code value for the object.
     * 
     * @return a hash code value for the object.
     */
    public int hashCode() {
        int hc = 17;
        hc = 37*hc+(id != null? id.hashCode() : 0);
        return (hc);
    }

    /**
     * Returns a string representation of the object.
     * 
     * @return a string representation of the object.
     */
    public String toString() {
        return (id == null ? "null" : id);
    }

    // unit test
    public static void main(String[] args) {
        try {
            Watch w = new Watch("Test");
            w.addWatchRecord(new Calculable("test", 5.4));
            w.addWatchRecord(new Calculable("test1", 1.1));
            w.addWatchRecord(new Calculable("test2", 2.2));
            w.addWatchRecord(new Calculable("test", 3.2));
            w.addWatchRecord(new Calculable("test", 2.1));
            Thread.sleep(1000);
            System.out.println("Main: Ready to Interrupt");
            System.out.flush();
            Calculable c[] = w.getWatchDataSource().getCalculable();
            for(int i = 0; i < c.length; i++)
                System.out.println(i + "). " + c[i]);
            System.out.flush();

        } catch(Exception e) {
            e.printStackTrace();
        }
        System.out.flush();
        System.exit(0);
    }
}
