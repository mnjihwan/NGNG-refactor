package com.ngng.chat.privateChat.dto.response;

import com.ngng.chat.privateChat.entity.PrivateChatRoom;
import com.ngng.chat.user.entity.User;
import lombok.*;

import java.sql.Timestamp;
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ReadPrivateChatMessageResponseDTO {
    ReadPrivateChatProductResponseDTO product;
    Long privateChatId;
    ReadPrivateChatRoomResponseDTO privateChatRoom;
    User user;
    String message;
    Timestamp createdAt;
    String contentType;


}
