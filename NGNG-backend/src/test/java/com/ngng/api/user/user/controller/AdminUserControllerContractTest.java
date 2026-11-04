package com.ngng.api.user.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngng.api.user.point.service.PointHistoryService;
import com.ngng.api.user.user.dto.UserReadResponseDTO;
import com.ngng.api.user.user.entity.Role;
import com.ngng.api.user.user.service.JoinService;
import com.ngng.api.user.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerContractTest {
    private UserService users;
    private PointHistoryService points;
    private JoinService join;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        users = mock(UserService.class);
        points = mock(PointHistoryService.class);
        join = mock(JoinService.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        mvc = MockMvcBuilders.standaloneSetup(new AdminUserController(users, points, join))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void omittedPageDefaultsToZeroAndReturnsEmptyPage() throws Exception {
        when(users.findAll(0)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));

        mvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(users).findAll(0);
        verifyNoMoreInteractions(users);
        verifyNoInteractions(points, join);
    }

    @Test
    void requestedPageAndUserFieldsAreReturnedInJson() throws Exception {
        Role role = Role.builder().roleId(3L).roleType("ADMIN").build();
        UserReadResponseDTO dto = UserReadResponseDTO.builder().userId(42L).name("Bob")
                .nickName("bob-nick").phoneNumber("010-2000").email("bob@example.test")
                .accountBank("Bank B").accountNumber("222").address("Address B")
                .role(role).point(920L).build();
        when(users.findAll(2)).thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(2, 5), 11));

        mvc.perform(get("/admin/users").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(42))
                .andExpect(jsonPath("$.content[0].name").value("Bob"))
                .andExpect(jsonPath("$.content[0].nickName").value("bob-nick"))
                .andExpect(jsonPath("$.content[0].phoneNumber").value("010-2000"))
                .andExpect(jsonPath("$.content[0].email").value("bob@example.test"))
                .andExpect(jsonPath("$.content[0].accountBank").value("Bank B"))
                .andExpect(jsonPath("$.content[0].accountNumber").value("222"))
                .andExpect(jsonPath("$.content[0].address").value("Address B"))
                .andExpect(jsonPath("$.content[0].role.roleId").value(3))
                .andExpect(jsonPath("$.content[0].role.roleType").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].point").value(920))
                .andExpect(jsonPath("$.pageable.pageNumber").value(2))
                .andExpect(jsonPath("$.pageable.pageSize").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.totalElements").value(11));

        verify(users).findAll(2);
        verifyNoMoreInteractions(users);
        verifyNoInteractions(points, join);
    }

    @Test
    void nonNumericPageIsRejectedByStandaloneBinding() throws Exception {
        mvc.perform(get("/admin/users").param("page", "invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users, points, join);
    }
}
