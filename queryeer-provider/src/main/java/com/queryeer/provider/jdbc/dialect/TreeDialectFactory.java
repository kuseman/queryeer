package com.queryeer.provider.jdbc.dialect;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.queryeer.api.service.Inject;
import com.queryeer.jdbc.JdbcType;

@Inject
class TreeDialectFactory implements ITreeDialectFactory
{
    private Map<JdbcType, ITreeDialect> dialects;

    TreeDialectFactory(List<ITreeDialect> dialects)
    {
        this.dialects = requireNonNull(dialects).stream()
                .collect(toMap(ITreeDialect::getType, Function.identity()));
    }

    @Override
    public ITreeDialect getTreeDialect(JdbcType type)
    {
        return dialects.getOrDefault(type, dialects.get(JdbcType.JDBC_URL));
    }
}
