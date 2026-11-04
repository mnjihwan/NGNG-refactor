package com.ngng.chat.publicChat.controller;

import com.ngng.chat.privateChat.dto.request.CreatePrivateChatMessageRequestDTO;
import com.ngng.chat.privateChat.dto.request.CreatePrivateChatRoomRequestDTO;
import com.ngng.chat.privateChat.dto.response.PrivateChatMessageDTO;
import com.ngng.chat.privateChat.dto.response.ReadAllPrivateMessageDTO;
import com.ngng.chat.privateChat.dto.response.ReadPrivateChatRoomResponseDTO;
import com.ngng.chat.privateChat.service.PrivateChatMessageService;
import com.ngng.chat.privateChat.service.PrivateChatRoomService;
import com.ngng.chat.publicChat.dto.request.CreatePublicChatRequestDTO;
import com.ngng.chat.publicChat.dto.request.UpdatePublicChatRequestDTO;
import com.ngng.chat.publicChat.dto.response.ReadPublicChatResponseDTO;
import com.ngng.chat.publicChat.service.PublicChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequiredArgsConstructor
@Slf4j
public class PublicChatController {

    private final PublicChatService publicChatService;
    private final PrivateChatMessageService privateChatMessageService;
    private final PrivateChatRoomService privateChatRoomService;

    @MessageMapping("/{productId}")
    @SendTo("/public-chats/{productId}")
    public ResponseEntity<ReadPublicChatResponseDTO> create(@DestinationVariable Long productId, CreatePublicChatRequestDTO request){
        return ResponseEntity.ok(publicChatService.create(productId, request));
    }

    @GetMapping(path = "/public-chats/{productId}")
    public ResponseEntity<List<ReadPublicChatResponseDTO>> read(@PathVariable Long productId){
        return ResponseEntity.ok(publicChatService.readAllByProductId(productId));
    }

    @PutMapping(path = "/public-chats/{productId}")
    public ResponseEntity<Long> update(@PathVariable Long productId, @RequestBody UpdatePublicChatRequestDTO request){
        return ResponseEntity.ok(publicChatService.update(productId, request));
    }

    @DeleteMapping(path = "/public-chats/{productId}")
    public ResponseEntity delete(@PathVariable Long productId){
        return ResponseEntity.ok(publicChatService.delete(productId));
    }

    @PostMapping("/private-chats")
    ResponseEntity<Long> createChatRoom(@RequestBody CreatePrivateChatRoomRequestDTO request){
        return ResponseEntity.ok(privateChatRoomService.create(request));
    }

    @GetMapping(path = "/private-chats/{userId}")
    ResponseEntity<List<ReadPrivateChatRoomResponseDTO>> readAllChatRoom(@PathVariable Long userId){
        return ResponseEntity.ok(privateChatRoomService.readAllChatRoom(userId));
    }

    @GetMapping(path= "/private-chats/find/{productId}/{buyerId}")
    ResponseEntity<Long> findPrivateChatRoomId(@PathVariable Long productId, @PathVariable Long buyerId){
        Long result = privateChatRoomService.findByProductIdAndBuyerId(productId, buyerId);
        if(result > 0) {
            return ResponseEntity.ok(result);
        }else{
            return ResponseEntity.notFound().build();
        }

    }

    @GetMapping("/private-chats/{chatRoomId}/{userId}")
    ResponseEntity<ReadAllPrivateMessageDTO> readAllChatMessages(@PathVariable Long chatRoomId, @PathVariable Long userId){
        return ResponseEntity.ok(privateChatMessageService.readAllByPrivateChatRoomId(chatRoomId, userId));
    }

    @MessageMapping("/{productId}/{buyerId}/{sendToId}")
    @SendTo({"/private-chats/{productId}/{buyerId}", "/alarms/{sendToId}"})
    ResponseEntity<PrivateChatMessageDTO> createMessage(@DestinationVariable Long productId,
                                                        @DestinationVariable Long buyerId,
                                                        @DestinationVariable Long sendToId,
                                                        @RequestBody CreatePrivateChatMessageRequestDTO request){
    log.info("프라이빗 메시지 알림 보낼 곳: "+String.valueOf(sendToId));
        return ResponseEntity.ok(privateChatMessageService.create(request));
    }
}
