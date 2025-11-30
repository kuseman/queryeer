package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.junit.jupiter.api.Test;

import se.kuseman.payloadbuilder.jdbc.parser.presto.SqlBaseLexer;
import se.kuseman.payloadbuilder.jdbc.parser.presto.SqlBaseParser;

//CSOFF
class PrestoTest
{
    @Test
    void test()
    {
        CharStream charStream = CharStreams.fromString("select * FROM product");
        Lexer lexer = new SqlBaseLexer(charStream);
        // lexer.removeErrorListeners();

        // AntlrErrorListener errorListener = new AntlrErrorListener(parseResult);
        // lexer.addErrorListener(errorListener);

        TokenStream tokenStream = new CommonTokenStream(lexer);

        SqlBaseParser parser = new SqlBaseParser(tokenStream);

        // parser.removeErrorListeners();
        // parser.addErrorListener(errorListener);

        parser.setErrorHandler(new DefaultErrorStrategy()
        {
            @Override
            protected Token singleTokenDeletion(Parser recognizer)
            {
                return null;
            }

        });

        // TODO: index sql-clauses intervals

        // SingleStatementContext singleStatement = parser.singleStatement();

        System.err.println();
    }
}
// CSON
