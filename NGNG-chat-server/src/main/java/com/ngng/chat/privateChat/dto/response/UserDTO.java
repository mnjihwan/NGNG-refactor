package com.ngng.chat.privateChat.dto.response;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class UserDTO {
    Long id;
    String name;
    String nickname;
    String address;
    String accountBank;
    String accountNumber;
}
