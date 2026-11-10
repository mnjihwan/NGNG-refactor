package com.ngng.api.user.user.address;
import com.ngng.api.global.security.jwt.util.JwtTokenProvider;
import com.ngng.api.user.user.dto.request.AddressConfirmRequest;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.*;
import com.ngng.api.user.user.service.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConfirmAddressServiceTest {
    @Test void beforeDirectCallWithoutAuthenticationCanChangeTarget() {
        var users = mock(UserRepository.class); var roles = mock(UserRoleRepository.class); var tokens = mock(JwtTokenProvider.class);
        var service = new ConfirmService(users,roles,tokens,new AuthService(users));
        var target = User.builder().userId(8001L).address("old-address").build();
        when(users.findById(8001L)).thenReturn(Optional.of(target));
        assertTrue(service.addressConfirm(new AddressConfirmRequest(8001L,"new-address")).isConfirmAddress());
        assertEquals("new-address",target.getAddress()); verify(users).findById(8001L); verifyNoMoreInteractions(users); verifyNoInteractions(roles,tokens);
    }
}
