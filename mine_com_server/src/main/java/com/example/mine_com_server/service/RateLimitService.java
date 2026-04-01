package com.example.mine_com_server.service;

import com.example.mine_com_server.exception.TooManyRequestsException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Slf4j
@Service
public class RateLimitService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void checkLogin(String ip) {
        consume(
                "login:" + ip,
                Bandwidth.builder()
                        .capacity(5)
                        .refillIntervally(5, Duration.ofMinutes(1))
                        .build(),
                "Слишком много попыток входа. Подождите минуту."
        );
    }

    public void checkRegister(String ip) {
        consume(
                "register:" + ip,
                Bandwidth.builder()
                        .capacity(3)
                        .refillIntervally(3, Duration.ofMinutes(1))
                        .build(),
                "Слишком много попыток регистрации. Подождите минуту."
        );
    }

    public void checkApi(String userId) {
        consume(
                "api:" + userId,
                Bandwidth.builder()
                        .capacity(100)
                        .refillIntervally(100, Duration.ofMinutes(1))
                        .build(),
                "Слишком много запросов. Подождите минуту."
        );
    }

    private void consume(String key, Bandwidth bandwidth, String errorMessage) {
        Bucket bucket = buckets.computeIfAbsent(key,
                k -> Bucket.builder().addLimit(bandwidth).build());

        if (!bucket.tryConsume(1)) {
            log.warn("[RATE LIMIT] Превышен лимит для ключа: {}", key);
            throw new TooManyRequestsException(errorMessage);
        }
    }
}