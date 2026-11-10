package com.ngng.api.transaction.read;

import com.ngng.api.product.product.entity.Category;
import com.ngng.api.product.product.entity.Product;
import com.ngng.api.product.product.entity.Status;
import com.ngng.api.product.thumbnail.entity.Thumbnail;
import com.ngng.api.transaction.entity.TransactionDetails;
import com.ngng.api.transaction.entity.TransactionStatus;
import com.ngng.api.user.user.entity.User;
import java.util.List;

/** Synthetic IDs and personal fields, never operating against a database. */
final class ReadFixture {
    private ReadFixture() { }
    @SuppressWarnings("removal")
    static Long id(long value) { return new Long(value); }
    static User user(Long id) {
        return User.builder().userId(id).name("synthetic-user-" + id).phoneNumber("000-0000-0000").build();
    }
    static TransactionDetails graph() {
        Product product = Product.builder().productId(7L).title("synthetic-product").price(900L)
                .user(user(id(3000))).category(Category.builder().categoryId(1L).categoryName("category").build())
                .status(Status.builder().statusId(1L).statusName("used").build())
                .thumbnail(Thumbnail.builder().thumbnailId(1L).thumbnailUrl("synthetic-thumb").build())
                .isEscrow(false).purchaseAt("synthetic-place").visible(true).freeShipping(false)
                .tags(List.of()).productImages(List.of()).build();
        return TransactionDetails.builder().id(88L).address("synthetic-private-address").product(product)
                .consumer(user(id(2000))).seller(user(id(1000))).price(300L)
                .status(new TransactionStatus(1L, "requested")).build();
    }
}
