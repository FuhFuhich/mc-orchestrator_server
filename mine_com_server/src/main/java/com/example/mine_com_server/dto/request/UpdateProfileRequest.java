package com.example.mine_com_server.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(min = 3, max = 32)
    private String username;
    private String email;
    private String phoneNumber;
}