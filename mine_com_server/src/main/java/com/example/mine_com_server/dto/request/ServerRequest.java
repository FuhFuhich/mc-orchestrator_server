package com.example.mine_com_server.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ServerRequest {

    @NotBlank(message = "Название ноды не может быть пустым")
    @Size(min = 2, max = 100, message = "Название должно быть от 2 до 100 символов")
    private String name;

    @NotBlank(message = "IP-адрес не может быть пустым")
    @Pattern(
            regexp = "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$",
            message = "Некорректный IP-адрес"
    )
    private String ipAddress;

    @Min(value = 1,     message = "SSH порт не может быть меньше 1")
    @Max(value = 65535, message = "SSH порт не может быть больше 65535")
    private Integer sshPort;

    @NotBlank(message = "SSH пользователь не может быть пустым")
    @Size(max = 50, message = "Имя пользователя не более 50 символов")
    private String sshUser;

    @NotBlank(message = "Тип аутентификации не может быть пустым")
    @Pattern(
            regexp = "^(password|key)$",
            message = "Тип аутентификации: password или key"
    )
    private String authType;

    private String sshPrivateKey;
    private String sshPassword;

    @Size(max = 1000, message = "Описание не более 1000 символов")
    private String description;

    @Size(max = 50, message = "ОС не более 50 символов")
    private String os;
}