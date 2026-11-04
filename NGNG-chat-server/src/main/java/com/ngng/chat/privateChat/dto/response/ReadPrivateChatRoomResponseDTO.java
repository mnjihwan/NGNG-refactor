package com.ngng.chat.privateChat.dto.response;

import com.ngng.chat.product.dto.TransactionRequestDTO;
import com.ngng.chat.transaction.entity.TransactionDetails;
import com.ngng.chat.user.entity.User;
import lombok.*;

import java.sql.Timestamp;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ReadPrivateChatRoomResponseDTO {
    Long privateChatRoomId;
    ReadChatRoomUserResponseDTO buyer;
    ReadChatRoomUserResponseDTO seller;
    Timestamp createdAt;
    ReadChatRoomTransactionDetails transactionDetails;
    Long unreadMessageCount;
    RecentMessageDTO recentMessage;
    ChatRoomProductDTO product;
    TransactionRequestDTO request;
}
