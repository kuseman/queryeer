package com.queryeer;

import static com.queryeer.api.action.Constants.FIND_ACTION;
import static java.util.Objects.requireNonNull;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;

import com.queryeer.api.component.DialogUtils;
import com.queryeer.api.component.IDialogFactory;
import com.queryeer.api.editor.IEditorFactory;
import com.queryeer.api.editor.ITextEditor;
import com.queryeer.api.editor.ITextEditorKit;

/** Factory for creating value dialogs with formatted content */
class DialogFactory implements IDialogFactory
{
    private final IEditorFactory editorFactory;

    DialogFactory(IEditorFactory editorFactory)
    {
        this.editorFactory = requireNonNull(editorFactory, "editorFactory");
    }

    /** Show a value dialog with provided title value and format */
    @Override
    public void showValueDialog(String title, Object val, Format format)
    {
        Object value = val;
        switch (format)
        {
            case JSON:
                value = Utils.formatJson(value);
                break;
            case XML:
                value = Utils.formatXML(String.valueOf(value));
                break;
            default:
                break;
        }

        if (value == null)
        {
            return;
        }

        if (value.getClass()
                .isArray())
        {
            int length = Array.getLength(value);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++)
            {
                list.add(Array.get(value, i));
            }
            value = list;
        }

        if (value instanceof Collection
                || value instanceof Map)
        {
            // Always use json for map/collection types
            format = Format.JSON;
            value = Utils.formatJson(value);
        }

        String mime = format.getMime();
        DialogUtils.AFrame frame = new DialogUtils.AFrame(title);
        ITextEditor textEditor = editorFactory.createTextEditor(new ITextEditorKit()
        {
            @Override
            public boolean readOnly()
            {
                return true;
            }

            @Override
            public String getSyntaxMimeType()
            {
                return mime;
            }

            @Override
            public int getRows()
            {
                return 40;
            }

            @Override
            public int getColumns()
            {
                return 80;
            }
        });
        textEditor.setValue(value);

        JComponent component = textEditor.getComponent();

        // Hook up find
        @SuppressWarnings("unchecked")
        List<Action> actions = (List<Action>) component.getClientProperty(com.queryeer.api.action.Constants.QUERYEER_ACTIONS);
        if (actions != null)
        {
            for (Action action : actions)
            {
                if (FIND_ACTION.equals(action.getValue(Action.ACTION_COMMAND_KEY)))
                {
                    KeyStroke keyStroke = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
                    InputMap inputMap = frame.getRootPane()
                            .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
                    inputMap.put(keyStroke, FIND_ACTION);
                    frame.getRootPane()
                            .getActionMap()
                            .put(FIND_ACTION, action);
                    break;
                }
            }
        }

        frame.getContentPane()
                .add(component);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setSize(Constants.DEFAULT_DIALOG_SIZE);
        frame.setVisible(true);
    }
}
