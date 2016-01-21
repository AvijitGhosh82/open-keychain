package org.sufficientlysecure.keychain.util;


public class Arrays {

    public static long[] concatenate(long[] a, long[] b)
    {
        if (a != null && b != null)
        {
            long[] rv = new long[a.length + b.length];

            System.arraycopy(a, 0, rv, 0, a.length);
            System.arraycopy(b, 0, rv, a.length, b.length);

            return rv;
        }
        else if (b != null)
        {
            return org.spongycastle.util.Arrays.clone(b);
        }
        else
        {
            return org.spongycastle.util.Arrays.clone(a);
        }
    }

}
