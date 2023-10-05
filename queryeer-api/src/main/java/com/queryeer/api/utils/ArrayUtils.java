package com.queryeer.api.utils;

import java.lang.reflect.Array;

/** Utils for arrays */
public class ArrayUtils
{
    /** Returns true if provided array is empty (null or 0 elements) */
    public static boolean isEmpty(Object array)
    {
        return array == null
                || Array.getLength(array) == 0;
    }
}
