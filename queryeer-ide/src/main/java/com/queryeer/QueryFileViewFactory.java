package com.queryeer;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputToolbarActionFactory;
import com.queryeer.api.service.IEventBus;

/** Factory for creating query files. */
class QueryFileViewFactory
{
    private final List<IOutputExtension> outputExtensions;
    private final List<IOutputToolbarActionFactory> outputToolbarActionFactories;
    private final IEventBus eventBus;

    QueryFileViewFactory(List<IOutputExtension> outputExtensions, List<IOutputToolbarActionFactory> outputToolbarActionFactories, IEventBus eventBus)
    {
        this.outputExtensions = requireNonNull(outputExtensions, "outputExtensions");
        this.outputToolbarActionFactories = requireNonNull(outputToolbarActionFactories, "outputToolbarActionFactories");
        this.eventBus = requireNonNull(eventBus, "eventBus");
    }

    QueryFileView create(QueryFileModel model)
    {
        List<IOutputComponent> outputComponents = outputExtensions.stream()
                .sorted(Comparator.comparingInt(IOutputExtension::order))
                .map(e -> e.createResultComponent())
                .filter(Objects::nonNull)
                .collect(toList());

        QueryFileView view = new QueryFileView(model, eventBus, outputComponents, outputToolbarActionFactories);
        // Init the state
        model.getQueryFileState()
                .init(view);
        return view;
    }
}
