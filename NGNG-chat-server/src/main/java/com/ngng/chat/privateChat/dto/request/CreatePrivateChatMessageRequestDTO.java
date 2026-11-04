package com.ngng.chat.privateChat.dto.request;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CreatePrivateChatMessageRequestDTO {
    Long userId;
    String message;
    Long privateChatRoomId;
    String contentType;
}
