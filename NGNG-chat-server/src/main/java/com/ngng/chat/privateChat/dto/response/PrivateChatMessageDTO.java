package com.ngng.chat.privateChat.dto.response;

import lombok.*;

import java.sql.Timestamp;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PrivateChatMessageDTO {
    Long chatId;
    String message;
    Timestamp createdAt;
    UserDTO user;
    String contentType;
    String productThumbnail;
}
