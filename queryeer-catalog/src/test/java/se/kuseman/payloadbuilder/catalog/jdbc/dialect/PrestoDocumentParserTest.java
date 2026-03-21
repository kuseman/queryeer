package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Disabled;

import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.ITemplateService;

/** Test of {@link PrestoDocumentParser}. */
@Disabled
class PrestoDocumentParserTest extends AntlrDocumentParserTestBase
{
    @Override
    protected AntlrDocumentParser<?> createParser()
    {
        return new PrestoDocumentParser(mock(IEventBus.class), mock(QueryActionsConfigurable.class), crawlService, connectionContext, mock(ITemplateService.class));
    }
}
