package com.ngng.chat.privateChat.dto.response;

import com.ngng.chat.product.dto.TransactionRequestDTO;
import lombok.*;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ReadAllPrivateMessageDTO {
    Long chatRoomId;
    ReadPrivateChatProductResponseDTO product;
    List<PrivateChatMessageDTO> messages;
    TransactionRequestDTO request;
}
