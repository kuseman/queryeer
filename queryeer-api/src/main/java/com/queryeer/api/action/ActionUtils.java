package com.queryeer.api.action;

import java.util.List;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/** Utils for building menus from {@link Action}'s */
public class ActionUtils
{
    /** Constructs a {@link JMenuItem} from provided {@link Action} */
    public static JMenuItem buildMenuItem(Action action)
    {
        JMenuItem item;
        Object obj = action.getValue(Constants.SUB_ACTIONS);
        if (obj != null)
        {
            item = new JMenu(action);

            @SuppressWarnings("unchecked")
            List<Action> subActions = (List<Action>) obj;
            for (Action subAction : subActions)
            {
                ((JMenu) item).add(buildMenuItem(subAction));
            }
        }
        else
        {
            item = new JMenuItem(action);
        }
        return item;
    }
}
