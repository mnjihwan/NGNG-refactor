package com.ngng.api.product.writer;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.product.product.controller.ProductController;
import com.ngng.api.product.product.dto.request.CreateProductRequestDTO;
import com.ngng.api.product.product.entity.*;
import com.ngng.api.product.product.repository.*;
import com.ngng.api.product.product.service.*;
import com.ngng.api.product.productImage.controller.ProductImageController;
import com.ngng.api.product.thumbnail.entity.Thumbnail;
import com.ngng.api.product.thumbnail.repository.ThumbnailRepository;
import com.ngng.api.product.thumbnail.service.ThumbnailService;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.service.AuthService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductAdditionalWriterServiceTest {
    ProductRepository products; ProductImageRepository images; ThumbnailRepository thumbnails;
    AuthService auth; ProductCacheInvalidator invalidator; Product target; ProductService service;
    ProductImageService imageService; ThumbnailService thumbnailService; UploadService uploads;
    ProductController controller; ProductImageController alias;
    @BeforeEach void setup() throws Exception {
        products=mock(ProductRepository.class);images=mock(ProductImageRepository.class);thumbnails=mock(ThumbnailRepository.class);
        auth=mock(AuthService.class);invalidator=mock(ProductCacheInvalidator.class);uploads=mock(UploadService.class);
        service=new ProductService(products,mock(TagService.class),auth,invalidator);
        imageService=new ProductImageService(images,thumbnails,products,auth);thumbnailService=new ThumbnailService(thumbnails,products,auth);
        controller=new ProductController(service,mock(CompressService.class),mock(AwsS3Service.class),uploads,imageService,thumbnailService,auth);
        alias=new ProductImageController(mock(CompressService.class),mock(AwsS3Service.class),uploads,imageService,thumbnailService);
        target=spy(Product.builder().productId(77L).user(user(1000L)).build());
        when(products.findById(77L)).thenReturn(Optional.of(target)); when(auth.readAuthUser()).thenReturn(user(1000L));
        when(thumbnails.findByProduct(any())).thenReturn(Optional.empty());when(images.findByProduct(any())).thenReturn(List.of());
        when(uploads.uploadImageFile(any())).thenReturn("fixture.png");when(uploads.uploadImageThumbnails(any())).thenReturn("thumb.png");when(uploads.uploadFile(any())).thenReturn("fixture.mp4");
        login("USER");
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    static User user(Long id){return User.builder().userId(id).email("rf039@example.test").build();}
    void login(String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CustomUserDetails(user(1000L)),"unused",List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
    CreateProductRequestDTO request(){return CreateProductRequestDTO.builder().userId(1000L).tags(List.of()).build();}
    void writer(String writer){switch(writer){
        case "create" -> service.create(request());case "refresh" -> service.updateRefresh(77L);
        case "image-create" -> imageService.create(77L,"new.png");case "image-delete" -> imageService.delete(77L,"old.png");
        case "images-delete" -> imageService.deleteImages(77L);case "thumbnail-create" -> thumbnailService.create(77L,"new.png");
        case "thumbnail-update" -> thumbnailService.update(90L,"new.png");default -> throw new AssertionError(writer);
    }}
    void noWrites(){verify(products,never()).save(any());verify(images,never()).save(any());verify(thumbnails,never()).save(any());
        verify(thumbnails,never()).deleteByProduct(any());verify(images,never()).deleteAllByProductIdAndImages(any(),any());
        verify(target,never()).setRefreshedAt(any());verifyNoInteractions(invalidator,uploads);}
    void forbidden(Runnable run){assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,run::run).getStatusCode());noWrites();}
    @ParameterizedTest @ValueSource(strings={"create","refresh","image-create","image-delete","images-delete","thumbnail-create","thumbnail-update"})
    void thirdPartyDeniedBeforeAnyMutation(String writer){
        when(auth.readAuthUser()).thenReturn(user(2000L));when(thumbnails.findById(90L)).thenReturn(Optional.of(Thumbnail.builder().product(target).thumbnailUrl("old").build()));
        forbidden(()->writer(writer));
    }
    @ParameterizedTest @ValueSource(strings={"null","anonymous","unauthenticated","string","null-user","null-email","blank-email","unknown-role"})
    void invalidAuthenticationDeniesAllWriters(String shape){
        switch(shape){case "null" -> SecurityContextHolder.clearContext();
            case "anonymous" -> SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key","anonymous",List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            case "unauthenticated" -> SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CustomUserDetails(user(1000L)),"x"));
            case "string" -> SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("unsupported","x",List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            case "null-user","null-email","blank-email" -> SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CustomUserDetails(shape.equals("null-user")?null:User.builder().email(shape.equals("null-email")?null:" ").build()),"x",List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            case "unknown-role" -> login("UNKNOWN");}
        when(thumbnails.findById(90L)).thenReturn(Optional.of(Thumbnail.builder().product(target).build()));
        for(String writer:List.of("create","refresh","image-create","image-delete","images-delete","thumbnail-create","thumbnail-update"))forbidden(()->writer(writer));
        verifyNoInteractions(auth);
    }
    @ParameterizedTest @ValueSource(strings={"caller","caller-id","owner","owner-id","product-id"})
    void nullIdsNeverMatch(String shape){
        switch(shape){case "caller" -> when(auth.readAuthUser()).thenReturn(null);case "caller-id" -> when(auth.readAuthUser()).thenReturn(user(null));
            case "owner" -> target.setUser(null);case "owner-id" -> target.setUser(user(null));case "product-id" -> target.setProductId(null);}
        forbidden(()->service.updateRefresh(77L));
    }
    @ParameterizedTest @CsvSource({"false,empty","false,unsupported","true,empty","true,unsupported"})
    void emptyAndUnsupportedUploadsStillAuthorize(boolean useAlias,String kind){
        when(auth.readAuthUser()).thenReturn(user(2000L));MultipartFile file=mock(MultipartFile.class);
        MultipartFile[] files=kind.equals("empty")?new MultipartFile[0]:new MultipartFile[]{file};
        forbidden(()->{if(useAlias)alias.fileUpload(files,"77");else controller.fileUpload(files,"77");});verifyNoInteractions(file);
    }
    @ParameterizedTest @CsvSource({"false,bad","true,bad","false,0","true,0","false,-1","true,-1","false,9223372036854775808","true,9223372036854775808","false,999","true,999"})
    void invalidUploadIdNeverTouchesFiles(boolean useAlias,String id){
        MultipartFile file=mock(MultipartFile.class);var exception=assertThrows(ResponseStatusException.class,()->{if(useAlias)alias.fileUpload(new MultipartFile[]{file},id);else controller.fileUpload(new MultipartFile[]{file},id);});
        assertEquals(id.equals("999")?HttpStatus.NOT_FOUND:HttpStatus.BAD_REQUEST,exception.getStatusCode());verifyNoInteractions(file);noWrites();
    }
    @Test void imageActualRelationshipMustMatchPath(){
        var other=Product.builder().productId(78L).user(user(1000L)).build();
        var image=spy(ProductImage.builder().product(other).visible(true).build());when(images.findByProductProductIdAndImageUrl(77L,"old.png")).thenReturn(image);
        forbidden(()->imageService.delete(77L,"old.png"));verify(image,never()).setVisible(any());
    }
    @Test void thumbnailUpdateAuthorizesStoredProduct(){
        var other=Product.builder().productId(78L).user(user(2000L)).build();var thumbnail=spy(Thumbnail.builder().product(other).thumbnailUrl("old").build());
        when(thumbnails.findById(90L)).thenReturn(Optional.of(thumbnail));forbidden(()->thumbnailService.update(90L,"new"));verify(thumbnail,never()).setThumbnailUrl(any());
    }
    @Test void mismatchedBulkImagesDoNotDeleteThumbnailFirst(){
        when(thumbnails.findByProduct(target)).thenReturn(Optional.of(Thumbnail.builder().product(target).build()));
        when(images.findByProduct(target)).thenReturn(List.of(ProductImage.builder().product(Product.builder().productId(78L).user(user(1000L)).build()).build()));
        forbidden(()->imageService.deleteImages(77L));
    }
    @Test void mismatchedExistingThumbnailCannotBeReplaced(){
        when(thumbnails.findByProduct(target)).thenReturn(Optional.of(Thumbnail.builder().product(Product.builder().productId(78L).user(user(1000L)).build()).build()));
        forbidden(()->thumbnailService.create(77L,"new"));
    }
    @Test void forgedCreateDoesNotReadTagsOrCascadeSave(){
        var request=spy(request());when(auth.readAuthUser()).thenReturn(user(2000L));forbidden(()->service.create(request));verify(request,never()).getTags();
    }
    @Test void ownerEmptyUploadKeepsBothResponseContracts(){
        assertEquals(77L,controller.fileUpload(new MultipartFile[0],"77"));assertEquals("hihi",alias.fileUpload(new MultipartFile[0],"77"));verifyNoInteractions(uploads,images,thumbnails);
    }
    @Test void missingImageAndThumbnailReturn404WithoutWrites(){
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,()->imageService.delete(77L,"missing.png")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,()->thumbnailService.update(90L,"new")).getStatusCode());noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"USER","ADMIN","UNCONFIRMED_USER"})
    @SuppressWarnings("removal") void sameValueDistinctLongOwnerIsAllowed(String role){
        login(role);target.setUser(user(new Long(1000)));when(auth.readAuthUser()).thenReturn(user(new Long(1000)));
        assertEquals(77L,service.updateRefresh(77L));verify(invalidator).invalidate(77L);
        imageService.create(77L,"new.mp4");var captor=org.mockito.ArgumentCaptor.forClass(ProductImage.class);verify(images).save(captor.capture());assertEquals("VIDEO",captor.getValue().getContentType());
        thumbnailService.create(77L,"thumb.png");verify(thumbnails).save(any());
    }
    @Test void adminManagesAnotherOwnersImagesAndThumbnail(){
        login("ADMIN");when(auth.readAuthUser()).thenReturn(user(2000L));
        var image=ProductImage.builder().productImageId(88L).product(target).visible(true).build();when(images.findByProductProductIdAndImageUrl(77L,"old.png")).thenReturn(image);when(images.save(image)).thenReturn(image);
        var thumb=Thumbnail.builder().product(target).thumbnailId(90L).build();when(thumbnails.findById(90L)).thenReturn(Optional.of(thumb));
        assertEquals(88L,imageService.delete(77L,"old.png"));assertFalse(image.getVisible());thumbnailService.update(90L,"new");assertEquals("new",thumb.getThumbnailUrl());
        assertEquals(77L,imageService.deleteImages(77L));
    }
    @Test void firstVideoDoesNotGenerateThumbnailAndAliasContractRemains() throws Exception {
        MultipartFile video=mock(MultipartFile.class);when(video.getOriginalFilename()).thenReturn("fixture.mp4");
        assertEquals("hihi",alias.fileUpload(new MultipartFile[]{video},"77"));verify(uploads).uploadFile(video);verify(uploads,never()).uploadImageThumbnails(any());
    }
    @Test void authorizedUploadFailureCanLeaveEarlierSideEffect() throws Exception {
        MultipartFile file=mock(MultipartFile.class);when(file.getOriginalFilename()).thenReturn("fixture.png");
        when(uploads.uploadImageThumbnails(file)).thenThrow(new java.io.IOException("synthetic failure"));
        assertThrows(RuntimeException.class,()->controller.fileUpload(new MultipartFile[]{file},"77"));verify(images).save(any());verify(thumbnails,never()).save(any());
    }
    @Test void unsupportedOwnerInputKeepsSuccessWithoutUpload(){
        MultipartFile file=mock(MultipartFile.class);when(file.getOriginalFilename()).thenReturn("fixture.txt");
        assertEquals(77L,controller.fileUpload(new MultipartFile[]{file},"77"));verifyNoInteractions(uploads);
    }
}
