package com.ngng.chat.privateChat.dto.response;

import lombok.*;

import java.sql.Timestamp;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class RecentMessageDTO {
    String message;
    Timestamp createdAt;
    String contentType;
}
