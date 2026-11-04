package com.ngng.chat.privateChat.dto.request;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CreatePrivateChatRoomRequestDTO {
    Long productId;
    Long buyerId;
    Long sellerId;
}
