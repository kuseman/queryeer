package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import com.queryeer.api.editor.ITextEditorDocumentParser.ParseItem;

/** Listener for errors and transform these into {@link ParseItem}'s */
class AntlrErrorListener extends BaseErrorListener
{
    private final List<ParseItem> result;

    AntlrErrorListener(List<ParseItem> result)
    {
        this.result = result;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e)
    {
        int startIndex = 1;
        int length = -1;

        if (offendingSymbol instanceof Token)
        {
            startIndex = ((Token) offendingSymbol).getStartIndex();
            length = ((Token) offendingSymbol).getStopIndex() - startIndex + 1;
        }
        else if (e != null)
        {
            if (e.getOffendingToken() != null)
            {
                startIndex = e.getOffendingToken()
                        .getStartIndex();
                length = e.getOffendingToken()
                        .getStopIndex() - startIndex + 1;

            }
            else if (e instanceof LexerNoViableAltException)
            {
                startIndex = ((LexerNoViableAltException) e).getStartIndex();
                length = 1;
            }
        }

        if (length == 0)
        {
            startIndex = -1;
            length = -1;
        }

        result.add(new ParseItem(msg, line - 1, startIndex, length));
    }
}