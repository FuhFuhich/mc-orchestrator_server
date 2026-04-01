package com.example.mine_com_server.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RconResponse {
    private boolean success;
    private String response;
    private String error;

    public static RconResponse ok(String response) {
        return new RconResponse(true, response, null);
    }

    public static RconResponse fail(String error) {
        return new RconResponse(false, null, error);
    }
}
