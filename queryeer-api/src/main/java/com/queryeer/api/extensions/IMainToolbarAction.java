package com.queryeer.api.extensions;

import javax.swing.Action;

/** A action that is placed in the main toolbar of Queryeer */
public interface IMainToolbarAction extends IExtensionAction
{
    /** Return a {@link IMainToolbarAction} from provided components */
    static IMainToolbarAction toolbarAction(final int order, final Action action)
    {
        return new IMainToolbarAction()
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
        };
    }
}
