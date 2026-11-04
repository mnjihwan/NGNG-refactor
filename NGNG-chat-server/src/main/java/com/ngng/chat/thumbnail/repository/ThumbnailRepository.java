package com.ngng.chat.thumbnail.repository;

import com.ngng.chat.thumbnail.entity.Thumbnail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ThumbnailRepository extends JpaRepository<Thumbnail, Long> {
    public Optional<Thumbnail> findByProductId(Long productId);
}
