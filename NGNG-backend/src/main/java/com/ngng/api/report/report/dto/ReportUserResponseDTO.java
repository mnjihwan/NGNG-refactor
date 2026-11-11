package com.ngng.api.report.report.dto;

import com.ngng.api.user.user.entity.User;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReportUserResponseDTO {
    private final Long userId;
    private final String name;
    private final String nickname;

    public static ReportUserResponseDTO from(User user) {
        return user == null ? null : new ReportUserResponseDTO(user.getUserId(), user.getName(), user.getNickname());
    }
}
