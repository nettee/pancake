package me.nettee.pancake.core.record;

import me.nettee.pancake.core.page.Pages;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

public class RecordFileTestUtils {

    private static Logger logger = LoggerFactory.getLogger(RecordFileTestUtils.class);

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
        return insertRecords(recordFile, N, i -> {
            String str = RandomStringUtils.randomAlphabetic(recordSize);
            return Record.fromString(str);
        });
    }

}
