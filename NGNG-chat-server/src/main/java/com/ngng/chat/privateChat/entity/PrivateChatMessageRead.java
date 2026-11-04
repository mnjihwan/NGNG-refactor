package com.ngng.chat.privateChat.entity;

import com.ngng.chat.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name = "private_chat_message_read")
@DynamicInsert
public class PrivateChatMessageRead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long privateChatMessageReadId;
    @ManyToOne
    @JoinColumn(name="private_chat_message_id")
    PrivateChatMessage privateChatMessage;
    @ManyToOne
    @JoinColumn(name="user_id")
    User user;
    @ManyToOne
    @JoinColumn(name="private_chat_room_id")
    PrivateChatRoom privateChatRoom;
}
