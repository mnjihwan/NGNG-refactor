package com.ngng.chat.privateChat.entity;

import com.ngng.chat.product.entity.Product;
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
@Table(name = "private_chat_room")
@DynamicInsert
public class PrivateChatRoom {
    @Id
    @Column(name = "private_chat_room_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @ManyToOne
    @JoinColumn(name="product_id")
    Product product;
    @ManyToOne
    @JoinColumn(name="buyer_id")
    User buyer;
    @ManyToOne
    @JoinColumn(name="seller_id")
    User seller;
    @CreationTimestamp
    Timestamp createdAt;
    @UpdateTimestamp
    Timestamp updatedAt;
    @ColumnDefault("true")
    Boolean visible;
}
