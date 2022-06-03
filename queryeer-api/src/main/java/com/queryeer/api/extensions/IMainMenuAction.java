package com.queryeer.api.extensions;

import javax.swing.Action;

/** A action that is placed in the main menu */
public interface IMainMenuAction extends IExtensionAction
{
    /** Returns the menu path for where the item should be placed */
    String getMenuPath();

    /** Return a {@link IMainMenuAction} from provided components */
    static IMainMenuAction menuAction(final int order, final Action action, final String menuPath)
    {
        return new IMainMenuAction()
        {
            @Override
            public int order()
            {
                return order;
            }

            @Override
            public Action getAction()
            {
                return action;
            }

            @Override
            public String getMenuPath()
            {
                return menuPath;
            }
        };
    }
}
