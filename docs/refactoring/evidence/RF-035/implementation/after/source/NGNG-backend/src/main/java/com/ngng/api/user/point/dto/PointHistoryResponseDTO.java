package com.ngng.api.user.point.dto;

import com.ngng.api.user.point.entity.PointHistory;
import lombok.Getter;

import java.sql.Timestamp;

/** Explicit public point-history fields; user associations stay inside the service boundary. */
@Getter
public class PointHistoryResponseDTO {
    private final Long id;
    private final Long beforeCost;
    private final Long addCost;
    private final Long cost;
    private final String type;
    private final String typeDetail;
    private final Timestamp createdAt;
    private final Timestamp updatedAt;

    private PointHistoryResponseDTO(PointHistory history) {
        this.id = history.getId();
        this.beforeCost = history.getBeforeCost();
        this.addCost = history.getAddCost();
        this.cost = history.getCost();
        this.type = history.getType();
        this.typeDetail = history.getTypeDetail();
        this.createdAt = history.getCreatedAt();
        this.updatedAt = history.getUpdatedAt();
    }

    public static PointHistoryResponseDTO from(PointHistory history) {
        return history == null ? null : new PointHistoryResponseDTO(history);
    }
}
