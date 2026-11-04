package com.ngng.chat.product.service;

import com.ngng.chat.product.entity.Product;
import com.ngng.chat.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    public Product read(Long productId){
        return productRepository.findById(productId).orElseThrow();
    }
}
