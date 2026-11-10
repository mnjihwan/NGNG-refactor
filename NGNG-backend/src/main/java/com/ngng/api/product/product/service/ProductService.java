package com.ngng.api.product.product.service;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.product.product.dto.UserDTO;
import com.ngng.api.product.product.dto.request.CreateProductRequestDTO;
import com.ngng.api.product.product.dto.request.TagRequestDTO;
import com.ngng.api.product.product.dto.request.UpdateProductRequestDTO;
import com.ngng.api.product.product.dto.response.*;
import com.ngng.api.product.product.entity.Product;
import com.ngng.api.product.product.entity.Tag;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "product-log")
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final TagService tagService;
    private final AuthService authService;
    private final ProductCacheInvalidator productCacheInvalidator;

    public Long create(CreateProductRequestDTO request){
        System.out.println("request = " + request.getUserId());
        return productRepository.save(new Product(request)).getProductId();
    }

    @Cacheable(cacheNames = "getProduct", key = "#productId", cacheManager = "contentCacheManager")
    public ReadProductResponseDTO read(Long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        return convertToReadProductResponseDTO(product);
    }

    public List<ReadAllProductsDTO> readAll() {
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            return null;
        }
        return products.stream().map((product -> ReadAllProductsDTO
                .builder()
                .id(product.getProductId())
                .content(product.getContent())
                .createdAt(product.getCreatedAt())
                .discountable(product.getDiscountable())
                .forSale(product.getForSale())
                .freeShipping(product.getFreeShipping())
                .title(product.getTitle())
                .isEscrow(product.getIsEscrow())
                .price(product.getPrice())
                .visible(product.getVisible())
                .updatedAt(product.getUpdatedAt())
                .refreshedAt(product.getRefreshedAt())
                .isEscrow(product.getIsEscrow())
                .purchaseAt(product.getPurchaseAt())
                .user(new UserDTO(product.getUser()))
                .category(ReadProductCategoryResponseDTO.builder()
                        .id(product.getCategory().getCategoryId())
                        .name(product.getCategory().getCategoryName())
                        .build())
                .status(ReadProductStatusResponseDTO.builder()
                        .id(product.getStatus().getStatusId())
                        .name(product.getStatus().getStatusName())
                        .build()).build())).toList();
    }


    public Page<ReadAllProductsDTO> readAll(Integer page) {

        List<Sort.Order> sorts = new ArrayList<>();
        sorts.add(Sort.Order.asc("productId"));
        Pageable pageable = PageRequest.of(page, 5, Sort.by(sorts));

        Page<Product> products = productRepository.findAllByVisible(true, pageable);

        if (products.isEmpty()) {
            return null;
        }
        return products.map(product -> ReadAllProductsDTO.builder()
                .id(product.getProductId())
                .content(product.getContent())
                .createdAt(product.getCreatedAt())
                .discountable(product.getDiscountable())
                .forSale(product.getForSale())
                .freeShipping(product.getFreeShipping())
                .title(product.getTitle())
                .isEscrow(product.getIsEscrow())
                .price(product.getPrice())
                .visible(product.getVisible())
                .updatedAt(product.getUpdatedAt())
                .refreshedAt(product.getRefreshedAt())
                .isEscrow(product.getIsEscrow())
                .purchaseAt(product.getPurchaseAt())
                .user(new UserDTO(product.getUser()))
                .category(ReadProductCategoryResponseDTO.builder()
                        .id(product.getCategory().getCategoryId())
                        .name(product.getCategory().getCategoryName())
                        .build())
                .status(ReadProductStatusResponseDTO.builder()
                        .id(product.getStatus().getStatusId())
                        .name(product.getStatus().getStatusName())
                        .build())
                .build());
    }

    public List<Product> readSellProductsByUserId(Long sellerId) {

        List<Product> products = productRepository.readAllSellProductBySellerId(sellerId);

//        return products.stream()
//                .map(item -> new ReadProductMypageResponseDTO().from(item))
//                .collect(Collectors.toList());
        return products;
    }


    @Transactional
    public ReadProductResponseDTO update(Long productId, UpdateProductRequestDTO request) {
        Product found = productRepository.findById(productId).orElseThrow();
        requireProductManager(found);

        List<String> originalTags = found.getTags().stream().map(Tag::getTagName).toList();  // 기존의 태그dhk
        List<String> newTags = request.getTags().stream().map(TagRequestDTO::getTagName).toList();  // 새로운 태그

        tagService.deleteAllByProductIdAndTagName(productId, originalTags);

        found.getTags().clear();
        found.from(request);

        log.info("Success Update Product id: {} ",productId);
//        return found.getProductId();
        ReadProductResponseDTO response = convertToReadProductResponseDTO(found);
        productCacheInvalidator.invalidate(productId);
        return response;
    }

    public Long updateForSale(Long productId, Boolean forSale) {
        Product target = productRepository.findById(productId).orElse(null);
        if (target != null) {
            target.setForSale(forSale);
            log.info("Success Update Product ForSale id: {} ",productId);
            productCacheInvalidator.invalidate(productId);
        }
        return target.getProductId();

    }

    public Long updateRefresh(Long productId){
        Product target = productRepository.findById(productId).orElse(null);
        if(target != null){
            target.setRefreshedAt(new Timestamp(new Date().getTime()));
            log.info("Success Update Product Refresh id: {} ",productId);
            productCacheInvalidator.invalidate(productId);
        }
        return target.getProductId();
    }


    public Long delete(Long productId){

        Product product = productRepository.findById(productId).orElseThrow();
        requireProductManager(product);
        // 신고 안 받았고 거래 진행 안 했거나 거래취소된 경우에만 삭제 가능
        if (product.getReports() == null || product.getTransactionDetails() == null || product.getTransactionDetails().getStatus().getId().equals(6L)) {
            product.setVisible(false);
            product.setForSale(false);
            log.info("Success Delete Product id: {} ",productId);
            Long deletedId = productRepository.save(product).getProductId();
            productCacheInvalidator.invalidate(productId);
            return deletedId;
        } else {
            return -1L;
        }

    }

    private void requireProductManager(Product product) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof CustomUserDetails details)
                || details.getUser() == null || details.getUser().getEmail() == null
                || details.getUser().getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (authentication.getAuthorities().stream().anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            return;
        }
        if (authentication.getAuthorities().stream().anyMatch(authority -> "ROLE_USER".equals(authority.getAuthority()))) {
            User caller = authService.readAuthUser();
            User owner = product.getUser();
            if (caller != null && caller.getUserId() != null && owner != null && owner.getUserId() != null
                    && caller.getUserId().equals(owner.getUserId())) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    // 업데이트 할때 status, category 네임값이 null로 들어감 (UpdateProductRequestDTO에서 Long 타입이여서)
    public ReadProductResponseDTO convertToReadProductResponseDTO(Product product) {
        if (product == null) {
            return null;
        }
        return ReadProductResponseDTO
                .builder()
                .id(product.getProductId())
                .content(product.getContent())
                .createdAt(product.getCreatedAt())
                .discountable(product.getDiscountable())
                .forSale(product.getForSale())
                .freeShipping(product.getFreeShipping())
                .images(product.getProductImages()
                        .stream()
                        .filter(image -> image.getVisible())
                        .map(image -> ReadProductImageResponseDTO
                                .builder()
                                .id(image.getProductImageId())
                                .imageURL(image.getImageUrl())
                                .visible(image.getVisible())
                                .contentType(image.getContentType())
                                .build())
                        .collect(Collectors.toList()))
                .title(product.getTitle())
                .isEscrow(product.getIsEscrow())
                .price(product.getPrice())
                .visible(product.getVisible())
                .updatedAt(product.getUpdatedAt())
                .refreshedAt(product.getRefreshedAt())
                .isEscrow(product.getIsEscrow())
                .purchaseAt(product.getPurchaseAt())
                .tags(product.getTags()
                        .stream().map(tag -> TagResponseDTO
                                .builder()
                                .tagName(tag.getTagName())
                                .build())
                        .collect(Collectors.toList())
                )
                .status(ReadProductStatusResponseDTO.builder()
                        .id(product.getStatus().getStatusId())
                        .name(product.getStatus().getStatusName())
                        .build()
                )
                .user(new UserDTO(product.getUser()))
                .category(ReadProductCategoryResponseDTO.builder()
                        .id(product.getCategory().getCategoryId())
                        .name(product.getCategory().getCategoryName())
                        .build())
                .chats(product.getPublicChats().stream()
                        .map(chat ->
                                ReadChatResponseDTO.builder()
                                        .id(chat.getPublicChatId())
                                        .message(chat.getMessage())
                                        .userId(chat.getUser().getUserId())
                                        .userNickName(chat.getUser().getNickname())
                                        .createdAt(chat.getCreatedAt())
                                        .build()
                        ).toList())
                .reports(product.getReports().stream().map(report ->
                        ReadReportResponseDTO.builder()
                                .reportId(report.getReportId())
                                .reportContents(report.getReportContents())
                                .reportType(report.getReportType())
                                .reporter(new UserDTO(report.getReporter()))
                                .user(new UserDTO(report.getUser()))
                                .createdAt(report.getCreatedAt())
                                .updatedAt(report.getUpdatedAt())
                                .isReport(report.getIsReport())
                                .build()).toList())
                .build();
    }


}
