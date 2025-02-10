package com.queryeer.output;

import static java.util.Arrays.asList;

import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.kordamp.ikonli.fontawesome.FontAwesome;

import com.queryeer.IconFactory;
import com.queryeer.api.extensions.IExtensionAction;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputToolbarActionFactory;

/** Factory that adds a Clear button to all {@link IOutputComponent IOutputComponents}. Calls {@link IOutputComponent#clearState()} */
class ClearOutputToolbarActionFactory implements IOutputToolbarActionFactory
{
    @Override
    public List<IExtensionAction> create(final IOutputComponent outputcomponent)
    {
        return asList(new IExtensionAction()
        {
            private Action action;

            @Override
            public int order()
            {
                return 10;
            }

            @Override
            public Action getAction()
            {
                if (action == null)
                {
                    action = new AbstractAction()
                    {
                        {
                            putValue(SMALL_ICON, IconFactory.of(FontAwesome.REMOVE));
                            putValue(SHORT_DESCRIPTION, "Clear");
                        }

                        @Override
                        public void actionPerformed(ActionEvent e)
                        {
                            outputcomponent.clearState();
                        }
                    };
                }
                return action;
            }
        });
    }
}
