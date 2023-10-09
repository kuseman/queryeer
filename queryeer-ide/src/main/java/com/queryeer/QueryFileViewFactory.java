package com.queryeer;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.extensions.output.IOutputToolbarActionFactory;

/** Factory for creating query files. */
class QueryFileViewFactory
{
    private final List<IOutputExtension> outputExtensions;
    private final List<IOutputToolbarActionFactory> outputToolbarActionFactories;

    QueryFileViewFactory(List<IOutputExtension> outputExtensions, List<IOutputToolbarActionFactory> outputToolbarActionFactories)
    {
        this.outputExtensions = requireNonNull(outputExtensions, "outputExtensions");
        this.outputToolbarActionFactories = requireNonNull(outputToolbarActionFactories, "outputToolbarActionFactories");
    }

    QueryFileView create(QueryFileModel model)
    {
        QueryFileView fileView = new QueryFileView(model, outputToolbarActionFactories);

        List<IOutputComponent> outputComponents = outputExtensions.stream()
                .filter(IOutputExtension::isAutoAdded)
                .sorted(Comparator.comparingInt(IOutputExtension::order))
                .map(e -> e.createResultComponent(fileView))
                .filter(Objects::nonNull)
                .collect(toList());

        fileView.setOutputComponents(outputComponents);

        return fileView;
    }
}
