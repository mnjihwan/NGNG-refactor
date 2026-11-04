package com.ngng.chat.privateChat.respository;

import com.ngng.chat.privateChat.entity.PrivateChatMessageRead;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivateChatMessageReadRepository extends JpaRepository<PrivateChatMessageRead, Long> {
    PrivateChatMessageRead findByPrivateChatRoomIdAndUserId(Long chatRoomId, Long userId);
}

