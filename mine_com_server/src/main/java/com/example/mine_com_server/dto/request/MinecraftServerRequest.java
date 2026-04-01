package com.example.mine_com_server.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.UUID;

@Data
public class MinecraftServerRequest {

    @NotNull(message = "ID ноды обязателен")
    private UUID nodeId;

    @NotBlank(message = "Название сервера не может быть пустым")
    @Size(min = 2, max = 100, message = "Название должно быть от 2 до 100 символов")
    private String name;

    @NotBlank(message = "Версия Minecraft обязательна")
    @Pattern(regexp = "^\\d+\\.\\d+(\\.\\d+)?$", message = "Версия Minecraft должна быть в формате 1.20 или 1.20.1")
    private String minecraftVersion;

    @Pattern(regexp = "^(vanilla|forge|neoforge|fabric|paper)?$",
             message = "Мод-лоадер: vanilla, forge, neoforge, fabric, paper")
    private String modLoader;

    @Size(max = 64, message = "Версия mod loader не более 64 символов")
    private String modLoaderVersion;

    /**
     * Deploy target: "screen" (existing VM logic) or "docker" (new Docker logic).
     */
    @Pattern(regexp = "^(screen|docker)?$", message = "Цель деплоя: screen или docker")
    private String deployTarget;

    @Min(value = 1024, message = "Игровой порт не может быть меньше 1024")
    @Max(value = 65535, message = "Игровой порт не может быть больше 65535")
    private Integer gamePort;

    private Boolean allocateAllResources;

    @Min(value = 512, message = "RAM не может быть меньше 512 МБ")
    @Max(value = 65536, message = "RAM не может быть больше 65536 МБ")
    private Integer ramMb;

    @Min(value = 1, message = "CPU не может быть меньше 1 ядра")
    @Max(value = 128, message = "CPU не может быть больше 128 ядер")
    private Integer cpuCores;

    @Min(value = 1024, message = "Диск не может быть меньше 1024 МБ")
    private Integer diskMb;

    private Boolean autoRestart;
    private Boolean backupEnabled;

    @Min(value = 1, message = "Интервал бэкапа не менее 1 часа")
    @Max(value = 168, message = "Интервал бэкапа не более 168 часов (7 дней)")
    private Integer backupIntervalHours;

    private Boolean backupAutoDelete;

    @Min(value = 1, message = "Хранить бэкапы не менее 1 часа")
    @Max(value = 8760, message = "Хранить бэкапы не более 8760 часов (1 год)")
    private Integer backupDeleteAfterHours;

    private Boolean whitelistEnabled;
    private Boolean rconEnabled;

    @Min(value = 1024, message = "RCON порт не может быть меньше 1024")
    @Max(value = 65535, message = "RCON порт не может быть больше 65535")
    private Integer rconPort;

    @Size(min = 6, max = 100, message = "RCON пароль от 6 до 100 символов")
    private String rconPassword;

    @Pattern(regexp = "^(ssd|hdd|ram)?$", message = "Тип хранилища: ssd, hdd или ram")
    private String storageType;

    @Min(value = 1, message = "Минимум 1 лог-файл")
    @Max(value = 100, message = "Максимум 100 лог-файлов")
    private Integer logMaxFiles;

    private String backupPath;

    @Min(value = 1, message = "Минимум 1 бэкап")
    @Max(value = 100, message = "Максимум 100 бэкапов")
    private Integer backupMaxCount;
}
