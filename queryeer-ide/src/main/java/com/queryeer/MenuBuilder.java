package com.queryeer;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;

import org.apache.commons.lang3.StringUtils;

class MenuBuilder
{
    /** Get or create a menu based on path spec */
    static JMenu getOrCreate(JMenuBar menuBar, String pathSpec)
    {
        /*
         * Path layout - name:order/subName:order/subSubName:order
         * 
         */
        String[] pathParts = pathSpec.split("/");

        JMenu current = null;
        for (int i = 0; i < pathParts.length; i++)
        {
            String[] part = pathParts[i].split(":");
            String name = part[0];
            int order = part.length == 1 ? Integer.MAX_VALUE
                    : Integer.parseInt(part[1]);

            // Root menu bar
            if (i == 0)
            {
                current = getOrCreateTopMenu(menuBar, name, order);
            }
            else
            {
                current = getOrCreateSubMenu(current, name, order);
            }
        }

        return current;
    }

    private static JMenu getOrCreateTopMenu(JMenuBar menuBar, String name, int order)
    {
        // First find the top menu from the bar
        for (int i = 0; i < menuBar.getMenuCount(); i++)
        {
            JMenu current = menuBar.getMenu(i);
            if (StringUtils.equalsAnyIgnoreCase((String) current.getClientProperty(QueryeerView.NAME), name))
            {
                return current;
            }

            int menuOrder = (int) current.getClientProperty(QueryeerView.ORDER);
            boolean last = i == menuBar.getMenuCount() - 1;
            if (order < menuOrder
                    || last)
            {
                JMenu menu = new JMenu(name);
                menu.putClientProperty(QueryeerView.ORDER, order);
                menu.putClientProperty(QueryeerView.NAME, name);
                menuBar.add(menu, i + (last ? 1
                        : 0));
                return menu;
            }
        }

        throw new IllegalArgumentException("No menu items");
    }

    private static JMenu getOrCreateSubMenu(JMenu parentMenu, String name, int order)
    {
        // First find the top menu from the bar
        for (int i = 0; i < parentMenu.getMenuComponentCount(); i++)
        {
            JComponent comp = (JComponent) parentMenu.getMenuComponent(i);
            if (StringUtils.equalsAnyIgnoreCase((String) comp.getClientProperty(QueryeerView.NAME), name)
                    && comp instanceof JMenu)
            {
                return (JMenu) comp;
            }

            int menuOrder = (int) comp.getClientProperty(QueryeerView.ORDER);
            boolean last = i == parentMenu.getMenuComponentCount() - 1;
            if (order < menuOrder
                    || last)
            {
                JMenu menu = new JMenu(name);
                menu.putClientProperty(QueryeerView.ORDER, order);
                menu.putClientProperty(QueryeerView.NAME, name);
                parentMenu.insert(menu, i + (last ? 1
                        : 0));
                return menu;
            }
        }

        JMenu menu = new JMenu(name);
        menu.putClientProperty(QueryeerView.ORDER, order);
        menu.putClientProperty(QueryeerView.NAME, name);
        parentMenu.add(menu);
        return menu;
    }
}
