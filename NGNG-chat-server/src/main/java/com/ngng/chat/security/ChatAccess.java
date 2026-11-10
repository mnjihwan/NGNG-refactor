package com.ngng.chat.security;

import com.ngng.chat.privateChat.dto.request.*;
import com.ngng.chat.privateChat.entity.PrivateChatRoom;
import com.ngng.chat.privateChat.respository.PrivateChatRoomRepository;
import com.ngng.chat.product.repository.ProductRepository;
import com.ngng.chat.publicChat.repository.PublicChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.time.Clock;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ChatAccess {
    private final PrivateChatRoomRepository rooms;
    private final ProductRepository products;
    private final PublicChatRepository publicChats;
    private final Clock clock;

    public ChatIdentity identity(Principal principal) {
        Object candidate = principal instanceof Authentication auth ? auth.getPrincipal() : principal;
        if (!(candidate instanceof ChatIdentity identity) || !clock.instant().isBefore(identity.expiresAt()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chat authentication required");
        return identity;
    }
    public Long self(Principal principal, Long supplied) {
        Long id = identity(principal).userId();
        require(Objects.equals(id, supplied));
        return id;
    }
    public void publicSend(Principal principal, Long productId, Long userId) {
        self(principal, userId);
        products.findById(productId).orElseThrow(() -> missing());
    }
    public void publicOwner(Principal principal, Long messageId) {
        Long userId = identity(principal).userId();
        var message = publicChats.findById(messageId).orElseThrow(() -> missing());
        require(Objects.equals(message.getUser().getId(), userId));
    }
    public void createRoom(Principal principal, CreatePrivateChatRoomRequestDTO request) {
        self(principal, request.getBuyerId());
        if (request.getProductId() == null || request.getSellerId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat resource required");
        var product = products.findById(request.getProductId()).orElseThrow(() -> missing());
        require(Objects.equals(product.getUser().getId(), request.getSellerId()));
    }
    public PrivateChatRoom room(Principal principal, Long roomId) {
        Long userId = identity(principal).userId();
        var room = rooms.findById(roomId).orElseThrow(() -> missing());
        participant(room, userId);
        return room;
    }
    public void destination(Principal principal, Long productId, Long buyerId) {
        Long id = identity(principal).userId();
        var room = rooms.findByProductIdAndBuyerId(productId, buyerId).orElseThrow(() -> denied());
        participant(room, id);
    }
    public void findRoom(Principal principal, Long productId, Long buyerId) {
        Long id = identity(principal).userId();
        if (Objects.equals(id, buyerId)) { products.findById(productId).orElseThrow(() -> missing()); return; }
        destination(principal, productId, buyerId);
    }
    public void privateSend(Principal principal, Long productId, Long buyerId, Long sendToId,
                            CreatePrivateChatMessageRequestDTO request) {
        Long id = self(principal, request.getUserId());
        var room = room(principal, request.getPrivateChatRoomId());
        Long counterpart = Objects.equals(id, room.getBuyer().getId()) ? room.getSeller().getId() : room.getBuyer().getId();
        require(Objects.equals(productId, room.getProduct().getId()) && Objects.equals(buyerId, room.getBuyer().getId()) &&
                Objects.equals(sendToId, counterpart));
    }
    private void participant(PrivateChatRoom room, Long userId) {
        require(Objects.equals(userId, room.getSeller().getId()) || Objects.equals(userId, room.getBuyer().getId()));
    }
    private void require(boolean allowed) { if (!allowed) throw denied(); }
    private ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat resource not found"); }
}
