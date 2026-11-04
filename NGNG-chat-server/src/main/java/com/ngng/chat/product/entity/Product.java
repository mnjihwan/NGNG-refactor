package com.ngng.chat.product.entity;

import com.ngng.chat.thumbnail.entity.Thumbnail;
import com.ngng.chat.transaction.entity.TransactionDetails;
import com.ngng.chat.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicInsert;
import java.sql.Timestamp;
import java.util.List;

import jakarta.persistence.Table;


@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name = "product")
@DynamicInsert
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;
    private String title;
    private String content;
    private Long price;

    @ColumnDefault("true")
    private Boolean isEscrow;

    @ColumnDefault("false")
    private Boolean discountable;

    private String purchaseAt;

    @ColumnDefault("true")
    private Boolean forSale;

    @ColumnDefault("true")
    private Boolean visible;

    @ColumnDefault("false")
    private Boolean freeShipping;

    private Timestamp refreshedAt;

    @CreationTimestamp
    private Timestamp createdAt;

    private Timestamp updatedAt;

    @ManyToOne
    @JoinColumn(name="user_id")
    private User user;

    @OneToOne(mappedBy = "product")
    private Thumbnail thumbnail;

    @OneToOne(mappedBy = "product")
    private TransactionDetails transactionDetails;

    @OneToMany
    @JoinColumn(name = "product_id")
    private List<TransactionRequest> requestList;

}
