package com.ngng.chat.thumbnail.service;

import com.ngng.chat.thumbnail.dto.response.ReadThumbnailResponseDTO;
import com.ngng.chat.thumbnail.repository.ThumbnailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ThumbnailService {
    private final ThumbnailRepository thumbnailRepository;

    public ReadThumbnailResponseDTO read(Long productId){
        return ReadThumbnailResponseDTO
                .builder()
                .thumbnailUrl(thumbnailRepository.findByProductId(productId).orElseThrow().getThumbnailUrl())
                .build();
    }

}
