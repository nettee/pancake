package me.nettee.pancake.core.page;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Pages {

    public static final byte DEFAULT_BYTE = (byte) 0xee;

    public static byte[] makeDefaultBytes(byte value, int length) {
        byte[] data = new byte[length];
        Arrays.fill(data, value);
        return data;
    }

    public static byte[] makeDefaultBytes(int length) {
        return makeDefaultBytes(DEFAULT_BYTE, length);
    }

    public static String pageRangeRepr(Set<Integer> pageNumSet) {
        Integer[] pageNums = pageNumSet.toArray(new Integer[pageNumSet.size()]);
        List<Pair<Integer, Integer>> ranges = new ArrayList<>();
        int i = 0;
        while (i < pageNums.length) {
            int j = i;
            while (j+1 < pageNums.length && pageNums[j+1] == pageNums[j] + 1) {
                j++;
            }
            // pageNums[i..j] is continuous
            ranges.add(new ImmutablePair<>(pageNums[i], pageNums[j]));
            i = j + 1;
        }
        return ranges.stream().map(range -> {
            int min = range.getLeft();
            int max = range.getRight();
            if (min == max) {
                return String.valueOf(min);
            } else {
                return String.format("%d-%d", min, max);
            }
        }).collect(Collectors.joining(","));
    }
}
