package com.ngng.api.transaction.status;

import com.ngng.api.product.product.entity.Category;
import com.ngng.api.product.product.entity.Product;
import com.ngng.api.product.product.entity.Status;
import com.ngng.api.product.thumbnail.entity.Thumbnail;
import com.ngng.api.transaction.entity.TransactionDetails;
import com.ngng.api.transaction.entity.TransactionStatus;
import com.ngng.api.user.user.entity.User;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic graph only. Separate Long allocation deliberately bypasses every boxing cache. */
final class StatusFixture {
    private StatusFixture() { }
    @SuppressWarnings("removal")
    static Long distinctId(long value) { return new Long(value); }
    static User user(Long id) {
        return User.builder().userId(id).name("user-" + id).phoneNumber("000").build();
    }
    static TransactionDetails graph(Long owner, Long buyer) {
        Product product = Product.builder().productId(7L).title("fixture").price(900L)
                .user(user(owner)).category(Category.builder().categoryId(1L).categoryName("category").build())
                .status(Status.builder().statusId(1L).statusName("used").build())
                .thumbnail(Thumbnail.builder().thumbnailId(1L).thumbnailUrl("fixture-thumb").build())
                .tags(List.of()).productImages(List.of()).build();
        return TransactionDetails.builder().id(88L).address("fixture-address").product(product)
                .consumer(user(buyer)).seller(user(distinctId(3000))).price(300L)
                .status(new TransactionStatus(1L, "requested")).build();
    }
    static void separateEqual(Long first, Long second) {
        assertEquals(first, second);
        assertNotSame(first, second, "test must exercise distinct Long objects");
    }
}
