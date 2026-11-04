package com.ngng.chat.privateChat.service;

import com.ngng.chat.privateChat.entity.PrivateChatMessage;
import com.ngng.chat.privateChat.entity.PrivateChatMessageRead;
import com.ngng.chat.privateChat.respository.PrivateChatMessageReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PrivateChatMessageReadService {
    private final PrivateChatMessageReadRepository privateChatMessageReadRepository;

    public void update(Long userId, Long chatRoomId, Long messageId){
        PrivateChatMessageRead found = privateChatMessageReadRepository.findByPrivateChatRoomIdAndUserId(chatRoomId, userId);
        found.setPrivateChatMessage(PrivateChatMessage.builder().privateChatId(messageId).build());
        privateChatMessageReadRepository.save(found);
    }
}
