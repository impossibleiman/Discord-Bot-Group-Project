package com.example.model;

import java.util.ArrayList;
import java.util.List;

public class MemberData {
    private long joinTimestamp;
    private long leaveTimestamp;
    private List<String> roleIds = new ArrayList<>();

    public long getJoinTimestamp() {
        return joinTimestamp;
    }

    public void setJoinTimestamp(long joinTimestamp) {
        this.joinTimestamp = joinTimestamp;
    }

    public long getLeaveTimestamp() {
        return leaveTimestamp;
    }

    public void setLeaveTimestamp(long leaveTimestamp) {
        this.leaveTimestamp = leaveTimestamp;
    }

    public List<String> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<String> roleIds) {
        this.roleIds = roleIds;
    }
}