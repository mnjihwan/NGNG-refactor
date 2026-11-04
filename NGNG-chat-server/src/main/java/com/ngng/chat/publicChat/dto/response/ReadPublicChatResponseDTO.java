package com.ngng.chat.publicChat.dto.response;

import lombok.*;

import java.sql.Timestamp;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ReadPublicChatResponseDTO {
    Long id;
    Long userId;
    String userName;
    String userNickname;
    String message;
    Boolean isImage;
    Timestamp createdAt;
}
