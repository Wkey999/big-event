package com.itheima.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FollowState {
    private boolean followed;
    private long followerCount;
    private long followeeCount;
}
