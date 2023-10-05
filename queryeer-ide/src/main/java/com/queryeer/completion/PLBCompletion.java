package com.queryeer.completion;

import static java.util.Objects.requireNonNull;

import javax.swing.Icon;

import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.CompletionProvider;

/** PLB completion */
class PLBCompletion extends BasicCompletion
{
    private final String toolTip;

    PLBCompletion(CompletionProvider provider, String replacementText, Icon icon)
    {
        this(provider, replacementText, null, icon);
    }

    PLBCompletion(CompletionProvider provider, String replacementText, String shortDesc, Icon icon)
    {
        this(provider, replacementText, shortDesc, null, null, icon, 0);
    }

    PLBCompletion(CompletionProvider provider, String replacementText, String shortDesc, Icon icon, int relevance)
    {
        this(provider, replacementText, shortDesc, null, null, icon, relevance);
    }

    PLBCompletion(CompletionProvider provider, String replacementText, String shortDesc, String summary, String toolTip, Icon icon, int relevance)
    {
        super(provider, replacementText, shortDesc, summary);
        this.toolTip = toolTip;
        setIcon(requireNonNull(icon, "icon"));
        setRelevance(relevance);
    }

    @Override
    public String getToolTipText()
    {
        return toolTip;
    }
}