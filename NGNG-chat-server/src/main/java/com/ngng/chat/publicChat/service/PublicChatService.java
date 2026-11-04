package com.ngng.chat.publicChat.service;
import com.ngng.chat.publicChat.dto.request.CreatePublicChatRequestDTO;
import com.ngng.chat.publicChat.dto.request.UpdatePublicChatRequestDTO;
import com.ngng.chat.publicChat.dto.response.ReadPublicChatResponseDTO;
import com.ngng.chat.publicChat.entity.PublicChat;
import com.ngng.chat.publicChat.repository.PublicChatRepository;
import com.ngng.chat.user.entity.User;
import com.ngng.chat.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublicChatService {
    private final PublicChatRepository publicChatRepository;
    private final UserService userService;

    public ReadPublicChatResponseDTO create(Long productId, CreatePublicChatRequestDTO request){
        PublicChat data = publicChatRepository.save(PublicChat
                .builder()
                .productId(productId)
                        .isImage(request.getIsImage())
                .user(User.builder()
                        .id(request.getUserId())
                        .build())
                .message(request.getMessage())
                .build());
        User user = userService.read(request.getUserId());
        return  ReadPublicChatResponseDTO.builder()
                .id(data.getId())
                .userId(user.getId())
                .userName(user.getName())
                .userNickname(user.getNickname())
                .message(data.getMessage())
                .createdAt(data.getCreatedAt())
                .build();
    }

    public List<ReadPublicChatResponseDTO> readAllByProductId(Long productId){
        return publicChatRepository.findAllByProductIdAndVisible(productId, true)
                .stream().map(chat -> ReadPublicChatResponseDTO
                        .builder()
                        .id(chat.getId())
                        .isImage(chat.getIsImage())
                        .userId(chat.getUser().getId())
                        .userName(chat.getUser().getName())
                        .userNickname(chat.getUser().getNickname())
                        .message(chat.getMessage())
                        .createdAt(chat.getCreatedAt())
                        .build())
                .toList();
    }

    public Long update(Long publicChatId, UpdatePublicChatRequestDTO message){
        PublicChat found = publicChatRepository.findById(publicChatId).orElseThrow();
        found.setMessage(message.getMessage());
        found.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return publicChatRepository.save(found).getId();
    }

    public Long delete(Long publicChatId){
        PublicChat found = publicChatRepository.findById(publicChatId).orElseThrow();
        found.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        found.setVisible(false);
        return publicChatRepository.save(found).getId();
    }
}
