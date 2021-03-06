/*
 * Copyright to the original author or authors.
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
package org.rioproject.tools.ui;

import net.jini.config.Configuration;
import net.jini.core.lookup.ServiceItem;
import net.jini.discovery.DiscoveryManagement;
import org.rioproject.monitor.ProvisionMonitor;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Properties;

/**
 * Container for utilities
 */
public class UtilitiesPanel extends JPanel {
    private ProvisionFailureEventTable provisionFailureEventTable;

    public UtilitiesPanel(CybernodeUtilizationPanel cup,
                          ColorManager colorManager,
                          Configuration config,
                          Properties props) {
        super(new BorderLayout());

        provisionFailureEventTable = new ProvisionFailureEventTable(colorManager,
                                                                    config,
                                                                    props);

        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Utilization", cup);
        JLabel label = makeTabLabel("Service Notifications",
                                    provisionFailureEventTable);
        tabs.addTab(null, provisionFailureEventTable);
        tabs.setTabComponentAt(1, label);
        add(tabs, BorderLayout.CENTER);
    }

    Properties getOptions() {
        Properties props = new Properties();
        props.put(Constants.AUTO_REMOVE_PROVISION_FAILURE_EVENTS,
                  Boolean.toString(provisionFailureEventTable.getAutoRemove()));
        return props;
    }

    void setDiscoveryManagement(DiscoveryManagement dMgr) {
        provisionFailureEventTable.setDiscoveryManagement(dMgr);
    }

    private JLabel makeTabLabel(String name, NotificationUtility... comps) {
        NotificationNode nn = new NotificationNode(comps);
        for(NotificationUtility nu : comps)
            nu.subscribe(nn);
        JLabel l = new TabLabel(name, nn);
        nn.setLabel(l);
        return l;
    }

    void addService(ServiceItem item) {
        if(item.service instanceof ProvisionMonitor)
            provisionFailureEventTable.addService(item);
    }

    void stopNotifications() {
        provisionFailureEventTable.terminate();
    }

    class TabLabel extends JLabel {
        private NotificationNode node;
        Dimension originalSize;

        TabLabel(String s, NotificationNode node) {
            super(s);
            this.node = node;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if(node.getCount()>0) {
                String countString = Integer.toString(node.getCount());
                g.setColor(new Color(227, 6, 19));

                int y = g.getFontMetrics().getAscent();
                int startX = g.getFontMetrics().stringWidth(getText());
                int countW = g.getFontMetrics().stringWidth(countString)+8;

                Shape shape = new RoundRectangle2D.Double(startX+4,
                                                          2,
                                                          countW,
                                                          (MacUIHelper.isMacOS()?getHeight()-4:getHeight()-2),
                                                          16,
                                                          16);
                Graphics2D g2d = (Graphics2D)g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                     RenderingHints.VALUE_ANTIALIAS_ON );
                g2d.fill(shape);
                g.setColor(Color.white);
                Font currentFont = g.getFont();
                g.setFont(Constants.NOTIFY_COUNT_FONT);
                g.drawString(countString, startX+8, y+1);
                g.setFont(currentFont);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            if(node.getCount()>0) {
                FontMetrics fm = getGraphics().getFontMetrics(getFont());
                String countString = Integer.toString(node.getCount());
                int countLength = fm.stringWidth(countString)+12;
                int textLength = fm.stringWidth(getText())+countLength;
                if(originalSize==null)
                    originalSize = super.getPreferredSize();
                Dimension d = super.getPreferredSize();
                d.width = textLength;
                return d;
            } else {
                if(originalSize==null)
                    originalSize = super.getPreferredSize();
                return originalSize;
            }
        }
    }

    class NotificationNode implements NotificationUtilityListener {
        JLabel label;
        NotificationUtility[] utility;

        NotificationNode(NotificationUtility... utility) {
            this.utility = utility;
        }

        void setLabel(JLabel label) {
            this.label = label;
        }

        int getCount() {
            int count = 0;
            for(NotificationUtility nu : utility) {
                count += nu.getTotalItemCount();
            }
            return count;
        }

        public void notify(NotificationUtility nu) {
            //getCount();            
            if(label!=null)
                label.repaint();
        }
    }
}
