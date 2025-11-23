package com.queryeer.payloadbuilder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import se.kuseman.payloadbuilder.api.QualifiedName;

/** Test of {@link CompletionRegistry} */
class CompletionRegistryTest
{
    @Test
    void test_qualified_name_contains()
    {
        assertTrue(CompletionRegistry.containsIgnoreCase(QualifiedName.of("sys", "objects"), QualifiedName.of("sys", "objects")));
        assertTrue(CompletionRegistry.containsIgnoreCase(QualifiedName.of("sys", "objects"), QualifiedName.of("objects")));
        assertFalse(CompletionRegistry.containsIgnoreCase(QualifiedName.of("sys", "objects"), QualifiedName.of("sys")));
    }
}
