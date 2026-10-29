package com.itheima.mapper;

import com.itheima.mapper.LeaderboardSnapshotMapper.ItemRow;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardSnapshotMapperTest {
    @Test
    void batchInsertExpandsAllModelRowsWithoutARealDatabase() {
        Configuration configuration = new Configuration();
        configuration.addMapper(LeaderboardSnapshotMapper.class);
        ItemRow first = item("A");
        ItemRow second = item("B");

        var sql = configuration.getMappedStatement(
                LeaderboardSnapshotMapper.class.getName() + ".insertItems")
                .getBoundSql(Map.of("snapshotId", 7L, "items", List.of(first, second)));

        assertTrue(sql.getSql().contains("`license`"));
        assertTrue(sql.getSql().contains("newly_listed"));
        assertEquals(22, sql.getParameterMappings().size());
    }

    private static ItemRow item(String name) {
        ItemRow row = new ItemRow();
        row.setModelName(name);
        row.setOrganization("Org");
        row.setLicense("MIT");
        row.setRankNumber(1);
        row.setScore(1400.0);
        row.setNewlyListed(false);
        return row;
    }
}
