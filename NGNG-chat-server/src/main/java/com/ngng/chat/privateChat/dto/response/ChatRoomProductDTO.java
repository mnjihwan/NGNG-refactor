package com.ngng.chat.privateChat.dto.response;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ChatRoomProductDTO {
    Long productId;
    String productTitle;
    String productThumbnailUrl;
}
