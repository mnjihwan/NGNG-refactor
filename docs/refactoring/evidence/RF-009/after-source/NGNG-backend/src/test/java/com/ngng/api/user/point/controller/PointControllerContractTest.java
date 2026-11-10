package com.ngng.api.user.point.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngng.api.user.point.entity.PointHistory;
import com.ngng.api.user.point.repository.PointHistoryRepository;
import com.ngng.api.user.point.service.PointHistoryService;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Standalone MVC: real controller/service/validation; no security filter, Boot error dispatch, or DB. */
class PointControllerContractTest {
    private final PointHistoryRepository histories = mock(PointHistoryRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final User user = User.builder().userId(7L).name("Alice").build();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        PointHistoryService service = new PointHistoryService(histories, auth, users);
        mvc = MockMvcBuilders.standaloneSetup(new PointController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new ObjectMapper().findAndRegisterModules()))
                .build();
    }

    private void balance(long cost) {
        when(auth.readAuthUser()).thenReturn(user);
        when(users.lockUserIdForPointUpdate(7L)).thenReturn(Optional.of(7L));
        when(histories.findLatestCostForUpdate(7L)).thenReturn(Optional.of(cost));
        when(histories.save(any())).thenAnswer(invocation -> {
            PointHistory history = invocation.getArgument(0);
            history.setId(101L);
            return history;
        });
    }

    @Test
    void chargeReturnsCreatedWithPointFieldsWithoutUser() throws Exception {
        balance(1000L);
        mvc.perform(post("/points").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addCost\":200,\"type\":\"충전\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.beforeCost").value(1000))
                .andExpect(jsonPath("$.addCost").value(200))
                .andExpect(jsonPath("$.cost").value(1200))
                .andExpect(jsonPath("$.type").value("충전"))
                .andExpect(jsonPath("$.typeDetail").value("Alice 유저가 200 금액 만큼 충전 하셨습니다."))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.createdAt").value((Object) null))
                .andExpect(jsonPath("$.updatedAt").value((Object) null));
    }

    @Test
    void entireBalancePaymentReturnsOkAndKeepsPositiveAddCost() throws Exception {
        balance(200L);
        mvc.perform(post("/points/payment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentCost\":200,\"payProductId\":42}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.beforeCost").value(200))
                .andExpect(jsonPath("$.addCost").value(200))
                .andExpect(jsonPath("$.cost").value(0))
                .andExpect(jsonPath("$.type").value("결제"))
                .andExpect(jsonPath("$.typeDetail").value("Alice 유저가 200 금액 만큼 42상품을 구매 하셨습니다."))
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> invalidWrites() {
        return Stream.of("/points", "/points/payment").flatMap(path -> {
            String field = path.equals("/points") ? "addCost" : "paymentCost";
            return Stream.of("null", "0", "-1", "-9223372036854775808", "\"bad\"", "9223372036854775808")
                    .map(value -> org.junit.jupiter.params.provider.Arguments.of(path, "{\"" + field + "\":" + value + "}"));
        });
    }

    @ParameterizedTest
    @MethodSource("invalidWrites")
    void invalidWriteAmountsReturnBadRequestWithoutRepositoryAccess(String path, String json) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(auth, users, histories);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/points", "/points/payment", "/points/check"})
    void missingAmountsReturnBadRequest(String path) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(auth, users, histories);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"bad\"", "9223372036854775808"})
    void checkRejectsNullOrUnbindableAmount(String value) throws Exception {
        mvc.perform(post("/points/check").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentCost\":" + value + "}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(auth, users, histories);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void checkReturnsFalseForNonpositiveAmount(long amount) throws Exception {
        mvc.perform(post("/points/check").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentCost\":" + amount + "}"))
                .andExpect(status().isOk()).andExpect(content().string("false"));
        verifyNoInteractions(auth, users, histories);
    }

    @ParameterizedTest
    @ValueSource(longs = {99, 100, 101})
    void checkReturnsBooleanAndNeverAppends(long amount) throws Exception {
        when(auth.readAuthUser()).thenReturn(user);
        when(histories.findLastByUserId(7L)).thenReturn(Optional.of(PointHistory.builder().cost(100L).build()));
        mvc.perform(post("/points/check").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentCost\":" + amount + "}"))
                .andExpect(status().isOk()).andExpect(content().string(Boolean.toString(amount <= 100)));
        verifyNoInteractions(users);
        verify(histories, never()).save(any());
    }

    @Test
    void insufficientPaymentReturnsBadRequestWithNoStandaloneResponseBody() throws Exception {
        balance(100L);
        var result = mvc.perform(post("/points/payment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentCost\":101,\"payProductId\":42}"))
                .andExpect(status().isBadRequest()).andExpect(content().string("")).andReturn();
        assertThat(result.getResponse().getContentType()).isNull();
        assertThat(result.getResponse().getErrorMessage()).isEqualTo("Insufficient point balance");
        verify(histories, never()).save(any());
    }

    @Test
    void overflowingChargeReturnsBadRequestWithoutAppending() throws Exception {
        balance(Long.MAX_VALUE);
        mvc.perform(post("/points").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addCost\":1,\"type\":\"충전\"}"))
                .andExpect(status().isBadRequest());
        verify(histories, never()).save(any());
    }

    @Test
    void readEndpointsKeepExistingLookupAndPointFields() throws Exception {
        when(auth.readAuthUser()).thenReturn(user);
        PointHistory latest = PointHistory.builder().id(101L).cost(200L).user(user).build();
        when(histories.findLastByUserId(7L)).thenReturn(Optional.of(latest));
        when(histories.findAllByUserId(7L)).thenReturn(List.of(latest));
        mvc.perform(get("/points")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101)).andExpect(jsonPath("$.cost").value(200));
        mvc.perform(get("/points/all")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101)).andExpect(jsonPath("$[0].cost").value(200));
        verifyNoInteractions(users);
        verify(histories, never()).save(any());
        verify(histories, never()).findLatestCostForUpdate(any());
    }
}
