package com.ngng.chat.publicChat.dto.request;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CreatePublicChatRequestDTO {
    Long userId;
    String message;
    Boolean isImage;
}
