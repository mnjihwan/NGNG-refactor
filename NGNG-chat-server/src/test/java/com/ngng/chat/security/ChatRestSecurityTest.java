package com.ngng.chat.security;

import com.ngng.chat.privateChat.entity.PrivateChatMessage;
import com.ngng.chat.privateChat.entity.PrivateChatMessageRead;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatRestSecurityTest {
    ChatSecurityFixture f; MockMvc mvc;
    @BeforeEach void setUp() { f=new ChatSecurityFixture(); mvc=MockMvcBuilders.webAppContextSetup(f.context).apply(springSecurity()).build(); }
    @AfterEach void tearDown() { f.close(); }
    @Test void anonymousPublicReadWorksAndPrivateRequiresExplicitValidBearer() throws Exception {
        mvc.perform(get("/public-chats/10")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/private-chats/2")).andExpect(status().isUnauthorized());
        mvc.perform(get("/public-chats/10").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/private-chats/2").header("Authorization",f.token(f.buyer),f.token(f.buyer))).andExpect(status().isUnauthorized());
        mvc.perform(get("/private-chats/2").cookie(new jakarta.servlet.http.Cookie("refreshToken",f.token(f.buyer)))).andExpect(status().isUnauthorized());
        verify(f.reads,never()).save(any()); assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void ownListFindAndPrivateReadKeepNormalContractsAndOtherUserDoesNotReadOrWrite() throws Exception {
        mvc.perform(get("/private-chats/2").header("Authorization",f.token(f.buyer))).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/private-chats/find/10/2").header("Authorization",f.token(f.buyer))).andExpect(status().isOk()).andExpect(content().string("20"));
        mvc.perform(get("/private-chats/find/10/2").header("Authorization",f.token(f.seller))).andExpect(status().isOk()).andExpect(content().string("20"));
        mvc.perform(get("/private-chats/20/2").header("Authorization",f.token(f.buyer))).andExpect(status().isOk()).andExpect(jsonPath("chatRoomId").value(20));
        clearInvocations(f.messages,f.reads);
        mvc.perform(get("/private-chats/2").header("Authorization",f.token(f.other))).andExpect(status().isForbidden());
        mvc.perform(get("/private-chats/20/2").header("Authorization",f.token(f.other))).andExpect(status().isForbidden());
        mvc.perform(get("/private-chats/20/3").header("Authorization",f.token(f.other,"ADMIN",f.clock.instant().plusSeconds(100)))).andExpect(status().isForbidden());
        mvc.perform(get("/private-chats/find/10/2").header("Authorization",f.token(f.other))).andExpect(status().isForbidden());
        verifyNoInteractions(f.messages); verify(f.reads,never()).save(any());
    }
    @Test void deniedPrivateReadLeavesExistingReadPointerAndMissingOwnRoomStays404() throws Exception {
        var marker=PrivateChatMessage.builder().privateChatId(77L).build();
        var read=PrivateChatMessageRead.builder().privateChatMessage(marker).build();
        when(f.reads.findByPrivateChatRoomIdAndUserId(20L,3L)).thenReturn(read);
        mvc.perform(get("/private-chats/20/3").header("Authorization",f.token(f.other))).andExpect(status().isForbidden());
        assertSame(marker,read.getPrivateChatMessage()); verify(f.reads,never()).save(any());
        mvc.perform(get("/private-chats/999/2").header("Authorization",f.token(f.buyer))).andExpect(status().isNotFound());
        when(f.rooms.findByProductIdAndBuyerId(10L,2L)).thenReturn(Optional.empty());
        mvc.perform(get("/private-chats/find/10/2").header("Authorization",f.token(f.buyer))).andExpect(status().isNotFound());
    }
    @Test void authorizedReadStillAdvancesPointerToLatestMessage() throws Exception {
        var message=PrivateChatMessage.builder().privateChatId(101L).user(f.seller).message("fixture").build();
        when(f.messages.findByPrivateChatRoomIdAndVisible(20L,true)).thenReturn(List.of(message));
        mvc.perform(get("/private-chats/20/2").header("Authorization",f.token(f.buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("messages[0].chatId").value(101));
        verify(f.reads,times(1)).save(argThat(pointer->pointer.getPrivateChatMessage().getPrivateChatId().equals(101L)));
    }
    @Test void roomCreateChecksActualProductSellerAndBuyerBeforeAnySave() throws Exception {
        for(String body: List.of("{\"productId\":10,\"buyerId\":3,\"sellerId\":1}","{\"productId\":10,\"buyerId\":2,\"sellerId\":3}"))
            mvc.perform(post("/private-chats").header("Authorization",f.token(f.buyer)).contentType("application/json").content(body)).andExpect(status().isForbidden());
        verify(f.rooms,never()).save(any()); verify(f.reads,never()).save(any());
        mvc.perform(post("/private-chats").header("Authorization",f.token(f.buyer,"UNCONFIRMED_USER",f.clock.instant().plusSeconds(100)))
                .contentType("application/json").content("{\"productId\":10,\"buyerId\":2,\"sellerId\":1}"))
                .andExpect(status().isOk()).andExpect(content().string("30"));
        verify(f.rooms,times(1)).save(any()); verify(f.reads,times(2)).save(any());
    }
    @Test void publicMessageMutationKeepsMessageIdMeaningAndRequiresOwner() throws Exception {
        mvc.perform(put("/public-chats/200").header("Authorization",f.token(f.buyer)).contentType("application/json").content("{\"message\":\"bad\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/public-chats/200").header("Authorization",f.token(f.other,"ADMIN",f.clock.instant().plusSeconds(100))))
                .andExpect(status().isForbidden()); verify(f.publicChats,never()).save(any());
        mvc.perform(put("/public-chats/200").header("Authorization",f.token(f.seller)).contentType("application/json").content("{\"message\":\"updated\"}"))
                .andExpect(status().isOk()).andExpect(content().string("200"));
        mvc.perform(delete("/public-chats/200").header("Authorization",f.token(f.seller))).andExpect(status().isOk()).andExpect(content().string("200"));
        verify(f.publicChats,times(2)).save(any());
    }
    @Test void malformedIdsBodiesAndExpiredRequestsFailWithoutWrites() throws Exception {
        mvc.perform(get("/private-chats/not-a-number").header("Authorization",f.token(f.buyer))).andExpect(status().isBadRequest());
        mvc.perform(post("/private-chats").header("Authorization",f.token(f.buyer)).contentType("application/json").content("broken"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/private-chats/2").header("Authorization",f.token(f.buyer,"USER",f.clock.instant()))).andExpect(status().isUnauthorized());
        verify(f.rooms,never()).save(any()); verify(f.reads,never()).save(any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void malformedSignedExpAndNbfAlwaysReturn401IncludingPublicReads() throws Exception {
        for(String token:ChatSecurityFixture.invalidTemporalTokens(f.clock.instant()))
            mvc.perform(get("/public-chats/10").header("Authorization",token)).andExpect(status().isUnauthorized());
        verify(f.reads,never()).save(any()); verify(f.publicChats,never()).save(any());
    }
}
