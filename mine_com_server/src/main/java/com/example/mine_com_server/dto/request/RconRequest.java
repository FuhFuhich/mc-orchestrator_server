package com.example.mine_com_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RconRequest {

    @NotBlank(message = "Команда не может быть пустой")
    @Size(max = 1024, message = "Команда не может превышать 1024 символа")
    private String command;
}
