package com.queryeer.output.table;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.kordamp.ikonli.fontawesome.FontAwesome;

import com.queryeer.IconFactory;
import com.queryeer.api.extensions.IExtensionAction;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputToolbarActionFactory;

/** Add search button for {@link TableOutputComponent} */
class FindTableToolbarActionFactory implements IOutputToolbarActionFactory
{
    @Override
    public List<IExtensionAction> create(final IOutputComponent outputcomponent)
    {
        if (!(outputcomponent instanceof TableOutputComponent))
        {
            return emptyList();
        }

        return asList(new IExtensionAction()
        {
            private Action action;

            @Override
            public int order()
            {
                return 20;
            }

            @Override
            public Action getAction()
            {
                if (action == null)
                {
                    action = new AbstractAction()
                    {
                        {
                            putValue(SMALL_ICON, IconFactory.of(FontAwesome.SEARCH));
                            putValue(SHORT_DESCRIPTION, "Find");
                        }

                        @Override
                        public void actionPerformed(ActionEvent e)
                        {
                            ((TableOutputComponent) outputcomponent).showFind();
                        }
                    };
                }
                return action;
            }
        });
    }
}
