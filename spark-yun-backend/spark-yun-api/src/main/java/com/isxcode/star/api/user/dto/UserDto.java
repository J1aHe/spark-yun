package com.isxcode.star.api.user.dto;

import lombok.Data;

@Data
public class UserDto {

    private String id;

    private String username;

    private String account;

    private String status;

    private String createDateTime;

    private String phone;

    private String email;

    private String remark;
}
