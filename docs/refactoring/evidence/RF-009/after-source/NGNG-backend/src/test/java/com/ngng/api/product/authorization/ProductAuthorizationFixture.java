package com.ngng.api.product.authorization;

import com.ngng.api.product.product.dto.request.TagRequestDTO;
import com.ngng.api.product.product.dto.request.UpdateProductRequestDTO;
import com.ngng.api.product.product.entity.*;
import com.ngng.api.user.user.entity.User;
import java.util.ArrayList;
import java.util.List;

final class ProductAuthorizationFixture {
    static final String EMAIL = "rf007@example.test";
    static final String BODY = """
            {"title":"changed","content":"new content","price":400,"isEscrow":true,
             "discountable":true,"purchaseAt":"2026-11","forSale":true,"visible":true,
             "freeShipping":true,"statusId":2,"categoryId":3,"tags":[{"tagName":"new"}]}
            """;
    static User user(Long id) { return User.builder().userId(id).email(EMAIL).name("owner").nickname("owner").build(); }
    static Product product(Long owner) {
        return Product.builder().productId(77L).user(user(owner)).title("original").content("old content")
                .price(300L).isEscrow(false).discountable(false).freeShipping(false).purchaseAt("2025")
                .visible(true).forSale(true).status(Status.builder().statusId(1L).build())
                .category(Category.builder().categoryId(1L).build())
                .tags(new ArrayList<>(List.of(Tag.builder().tagName("old").build())))
                .productImages(new ArrayList<>()).publicChats(new ArrayList<>()).reports(new ArrayList<>()).build();
    }
    static UpdateProductRequestDTO request() {
        return UpdateProductRequestDTO.builder().title("changed").content("new content").price(400L)
                .isEscrow(true).discountable(true).purchaseAt("2026-11").forSale(true).visible(true)
                .freeShipping(true).statusId(2L).categoryId(3L)
                .tags(List.of(TagRequestDTO.builder().tagName("new").build())).build();
    }
    // Deliberately outside Long's small-value cache; separate objects with equal values.
    @SuppressWarnings("removal") static Long distinctId(long id) { return new Long(id); }
    private ProductAuthorizationFixture() {}
}
