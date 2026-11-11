package com.ngng.api.transaction.status;

import com.ngng.api.product.product.entity.*;
import com.ngng.api.product.thumbnail.entity.Thumbnail;
import com.ngng.api.transaction.entity.*;
import com.ngng.api.user.user.entity.User;
import java.util.List;

final class Rf038Fixture {
    private Rf038Fixture() { }
    static User user(Long id) {
        return User.builder().userId(id).email("rf038@example.test").name("synthetic-" + id)
                .phoneNumber("000-0000-0000").build();
    }
    static TransactionDetails graph(Long status) {
        Product product = Product.builder().productId(7L).user(user(3000L)).title("synthetic-product").price(900L)
                .category(Category.builder().categoryId(1L).categoryName("category").build())
                .status(Status.builder().statusId(1L).statusName("used").build())
                .thumbnail(Thumbnail.builder().thumbnailId(1L).thumbnailUrl("synthetic-thumb").build())
                .tags(List.of()).productImages(List.of()).build();
        return TransactionDetails.builder().id(88L).product(product).seller(user(1000L)).consumer(user(2000L))
                .status(status == null ? null : new TransactionStatus(status, "synthetic-status-" + status))
                .price(300L).address("synthetic-address").build();
    }
}
