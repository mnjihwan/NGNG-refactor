package com.ngng.chat.privateChat.dto.response;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ReadChatRoomUserResponseDTO {
    Long id;
    String name;
    String nickname;
}
