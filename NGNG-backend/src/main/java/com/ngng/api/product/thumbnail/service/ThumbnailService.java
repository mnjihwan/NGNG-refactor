package com.ngng.api.product.thumbnail.service;

import com.ngng.api.product.product.entity.Product;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.product.product.service.ProductWriterAuthorization;
import com.ngng.api.user.user.service.AuthService;
import com.ngng.api.product.thumbnail.entity.Thumbnail;
import com.ngng.api.product.thumbnail.repository.ThumbnailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThumbnailService {
    private final ThumbnailRepository thumbnailRepository;
    private final ProductRepository productRepository;
    private final AuthService authService;

    @Transactional
    public void create( Long productId , String thumbnailUrl){

        Product product = ProductWriterAuthorization.requireProduct(productId, productRepository, authService);
        Optional<Thumbnail> thumbnail = thumbnailRepository.findByProduct(product);

        // 기존 썸네일이 존재하는 경우
        if (thumbnail.isPresent()) {
            if (thumbnail.get().getProduct() == null || !productId.equals(thumbnail.get().getProduct().getProductId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            ProductWriterAuthorization.requireManager(thumbnail.get().getProduct(), authService);
            thumbnailRepository.deleteByProduct(thumbnail.get().getProduct());
        }

        thumbnailRepository.save(Thumbnail.builder()
                        .thumbnailUrl(thumbnailUrl)
                        .product(product)
                .build());
    }

    public Thumbnail readByProductId(Long productId){
        return thumbnailRepository.findByProductProductId(productId).orElse(null);
    }

    @Transactional
    public void update(Long thumbnailId, String newUrl){
        Thumbnail found = thumbnailRepository.findById(thumbnailId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ProductWriterAuthorization.requireManager(found.getProduct(), authService);
        found.setThumbnailUrl(newUrl);
        thumbnailRepository.save(found);

    }
}
