package com.ngng.chat.privateChat.respository;

import com.ngng.chat.privateChat.entity.PrivateChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrivateChatMessageRepository extends JpaRepository<PrivateChatMessage, Long> {
    List<PrivateChatMessage> findAllByPrivateChatRoomIdAndPrivateChatIdGreaterThan(Long privateChatRoomId,Long privateChatId);
    List<PrivateChatMessage> findByPrivateChatRoomIdAndVisible(Long privateChatRoomId, Boolean visible);
}
