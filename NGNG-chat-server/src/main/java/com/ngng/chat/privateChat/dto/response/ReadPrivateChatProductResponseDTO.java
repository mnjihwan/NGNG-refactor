package com.ngng.chat.privateChat.dto.response;

import com.ngng.chat.product.dto.TransactionRequestDTO;
import com.ngng.chat.transaction.entity.TransactionDetails;
import com.ngng.chat.user.entity.User;
import lombok.*;

import java.util.List;
import java.util.Optional;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ReadPrivateChatProductResponseDTO {
    Long productId;
    String productTitle;
    Long price;
    Boolean isEscrow;
    Boolean discountable;
    String thumbnailUrl;
    ReadChatRoomTransactionDetails transactionDetails;
    UserDTO seller;
    UserDTO buyer;
   TransactionRequestDTO request;
}
