package com.ngng.chat.privateChat.dto.response;

import com.ngng.chat.transaction.entity.TransactionStatus;
import lombok.*;

import java.util.Optional;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ReadChatRoomTransactionDetails {
    Long id;
    String address;
    TransactionStatus status;
}
