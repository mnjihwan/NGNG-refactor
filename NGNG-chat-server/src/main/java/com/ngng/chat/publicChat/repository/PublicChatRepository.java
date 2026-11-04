package com.ngng.chat.publicChat.repository;

import com.ngng.chat.publicChat.entity.PublicChat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublicChatRepository extends JpaRepository<PublicChat, Long> {
    public List<PublicChat> findAllByProductIdAndVisible(Long productId, Boolean visible);
}
