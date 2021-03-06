package me.nettee.pancake.core.record;

import me.nettee.pancake.core.model.RID;
import me.nettee.pancake.core.model.Record;
import me.nettee.pancake.core.page.Pages;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

class RecordFileTestUtils {

    private static Logger logger = LoggerFactory.getLogger(RecordFileTestUtils.class);

    // Return as JUnit parameterized test params.
    static List<Object[]> randomRecordNumbers(int recordSize) {
        int capacity = RecordPage.getPageRecordCapacity(recordSize);
        Object[][] data = {
                {1},
                {RandomUtils.nextInt(2, capacity)},
                {capacity},
                {capacity + 1},
                {2 * capacity + 5},
                {RandomUtils.nextInt(2, 10) * capacity},
                {RandomUtils.nextInt(capacity + 2, capacity * 10)},
        };
        return Arrays.asList(data);
    }

    static List<Pair<Record, RID>> insertRecords(RecordFile recordFile,
                                                 int N,
                                                 IntFunction<Record> recordGenerator) {
        List<Pair<Record, RID>> insertedRecords = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            Record record = recordGenerator.apply(i);
            RID rid = recordFile.insertRecord(record);
            insertedRecords.add(new ImmutablePair<>(record, rid));
        }

        Set<RID> rids = insertedRecords.stream()
                .map(Pair::getRight)
                .collect(Collectors.toSet());
        Map<Integer, Set<Integer>> counter = new TreeMap<>();
        rids.forEach(rid -> {
            if (!counter.containsKey(rid.pageNum)) {
                counter.put(rid.pageNum, new HashSet<>());
            }
            counter.get(rid.pageNum).add(rid.slotNum);
        });
        counter.forEach((pageNum, slots) -> logger.debug(
                "Page[{}] contains records [{}]",
                pageNum, Pages.pageRangeRepr(slots)));

        return insertedRecords;
    }

    static List<Pair<Record, RID>> insertRandomRecords(RecordFile recordFile,
                                                       int N,
                                                       int recordSize) {
        return insertRecords(recordFile, N, i -> getRandomRecord(recordSize));
    }

    static Record getRandomRecord(int recordSize) {
        String str = RandomStringUtils.randomAlphabetic(recordSize);
        return Record.fromString(str);
    }

    static <E> E pickOne(List<E> list) {
        int i = RandomUtils.nextInt(0, list.size());
        return list.get(i);
    }

    static <E> List<E> pickSome(List<E> list, int m) {
        List<E> res = new ArrayList<>(m);
        int nNeeded = m;
        int nLeft = list.size();
        for (E e : list) {
            // Pick the element with probability nNeeded / nLeft
            int r = RandomUtils.nextInt(0, nLeft);
            if (r < nNeeded) {
                res.add(e);
                nNeeded--;
                if (nNeeded == 0) {
                    break;
                }
            }
            nLeft--;
        }
        return res;
    }
}
