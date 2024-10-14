package com.queryeer;

import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;

import se.kuseman.payloadbuilder.api.OutputWriter;

/** Test of {@link ProxyOutputWriter} */
public class ProxyOutputWriterTest
{
    @Test
    public void test_all_get_methods_are_declared()
    {
        // Make sure that the proxy output writer has overridden all interface methods
        Method[] methods = OutputWriter.class.getDeclaredMethods();

        for (Method method : methods)
        {
            try
            {
                ProxyOutputWriter.class.getDeclaredMethod(method.getName(), method.getParameterTypes());
            }
            catch (NoSuchMethodException e)
            {
                fail(ProxyOutputWriter.class.getSimpleName() + " should have method: " + method + " implemented");
            }
        }
    }
}
