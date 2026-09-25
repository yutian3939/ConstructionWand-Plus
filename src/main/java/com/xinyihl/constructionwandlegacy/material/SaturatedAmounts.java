package com.xinyihl.constructionwandlegacy.material;

public final class SaturatedAmounts {
    private SaturatedAmounts() {
    }

    public static int fromLong(long value) {
        if (value <= 0L) {
            return 0;
        }
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    public static int add(int first, int second) {
        if (first <= 0) {
            return Math.max(0, second);
        }
        if (second <= 0) {
            return first;
        }
        long sum = (long) first + second;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    public static long add(long first, long second) {
        if (second <= 0L) {
            return first;
        }
        if (first >= Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }
}
