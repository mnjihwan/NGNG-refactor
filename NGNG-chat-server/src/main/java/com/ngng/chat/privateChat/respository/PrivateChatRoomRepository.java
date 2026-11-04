package com.ngng.chat.privateChat.respository;

import com.ngng.chat.privateChat.entity.PrivateChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrivateChatRoomRepository extends JpaRepository<PrivateChatRoom, Long> {

    List<PrivateChatRoom> findAllBySellerIdOrBuyerId(Long sellerId, Long buyerId);
    Optional<PrivateChatRoom> findByProductIdAndBuyerId(Long productId, Long buyerId);
}
