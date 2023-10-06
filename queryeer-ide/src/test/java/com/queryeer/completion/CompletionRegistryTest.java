package com.queryeer.completion;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.kuseman.payloadbuilder.api.QualifiedName;

/** Test of {@link CompletionRegistry} */
public class CompletionRegistryTest
{
    @Test
    public void test_qualified_name_contains()
    {
        assertTrue(CompletionRegistry.containsIgnoreCase(QualifiedName.of("sys", "objects"), QualifiedName.of("sys", "objects")));
        assertTrue(CompletionRegistry.containsIgnoreCase(QualifiedName.of("sys", "objects"), QualifiedName.of("objects")));
        assertFalse(CompletionRegistry.containsIgnoreCase(QualifiedName.of("sys", "objects"), QualifiedName.of("sys")));
    }
}
