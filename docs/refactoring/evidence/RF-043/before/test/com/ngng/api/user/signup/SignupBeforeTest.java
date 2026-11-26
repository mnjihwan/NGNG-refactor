package com.ngng.api.user.signup;

import com.ngng.api.user.user.service.*;
import com.ngng.api.user.user.dto.request.*;
import com.ngng.api.user.user.entity.*;
import com.ngng.api.user.user.repository.*;
import com.ngng.api.user.point.service.PointHistoryService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Compiled exclusively against preserved HEAD source copies, never the edited signup classes. */
class SignupBeforeTest {
    @Test void originalEmailReturnsTheSameCodeAsMemoryMailboxWithoutServerVerificationState() throws Exception {
        var users=mock(UserRepository.class);var sender=mock(JavaMailSender.class);List<MimeMessage> mailbox=new ArrayList<>();
        when(sender.createMimeMessage()).thenAnswer(c->new MimeMessage((Session)null));doAnswer(c->{mailbox.add(c.getArgument(0));return null;}).when(sender).send(any(MimeMessage.class));
        var service=new EmailAuthService(sender,users);ReflectionTestUtils.setField(service,"from","rf043@example.test");
        var response=service.sendMail(new EmailAuthRequest("Synthetic","rf043-new@example.test"));assertTrue(response.isSuccess());assertTrue(response.authNumber().matches("[0-9]{6}"));
        assertTrue(mailbox.get(0).getContent().toString().endsWith(response.authNumber()));verify(users).existsByEmail("rf043-new@example.test");verifyNoMoreInteractions(users);
    }
    @Test void originalJoinAcceptsOnlyFiveFieldsAndWritesWithoutAnyProof() {
        var users=mock(UserRepository.class);var roles=mock(UserRoleRepository.class);var passwords=mock(PasswordEncoder.class);var points=mock(PointHistoryService.class);
        when(roles.findByRoleType("UNCONFIRMED_USER")).thenReturn(Role.builder().roleType("UNCONFIRMED_USER").build());when(passwords.encode("synthetic-password")).thenReturn("encoded");
        when(users.save(any())).thenAnswer(c->{User u=c.getArgument(0);u.setUserId(43L);return u;});
        var service=new JoinService(users,roles,passwords,points);assertTrue(service.join(new JoinRequest("Synthetic","rf043-new@example.test","synthetic-password","fixture","01112345678")).isJoinSuccess());
        verify(users).save(any());verify(points).createInitByUser(any());
    }
}
