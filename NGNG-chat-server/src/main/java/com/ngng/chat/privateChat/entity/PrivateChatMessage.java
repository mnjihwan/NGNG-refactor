package com.ngng.chat.privateChat.entity;

import com.ngng.chat.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name = "private_chat_message")
@DynamicInsert
public class PrivateChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long privateChatId;
    @ManyToOne
    @JoinColumn(name="user_id")
    User user;
    String message;
    @ColumnDefault("true")
    Boolean visible;
    @ManyToOne
    @JoinColumn(name="private_chat_room_id")
    PrivateChatRoom privateChatRoom;
    @CreationTimestamp
    Timestamp createdAt;
    @UpdateTimestamp
    Timestamp updatedAt;
    String contentType;
}
