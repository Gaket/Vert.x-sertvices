package com.vyng.vertex.service;

import com.mongodb.lang.NonNull;
import com.vyng.vertex.error.QueryLimitReachedException;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.redis.RedisClient;

import java.util.concurrent.TimeUnit;

public class RateLimiterService {
    private static final int LIMITATION_TIME = (int) TimeUnit.HOURS.toSeconds(12);

    @NonNull
    private final RedisClient redisClient;

    public RateLimiterService(@NonNull RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    public Future<Boolean> getTotalRequestLimitFuture(String key, int maxRequests, String error) {
        Promise<Long> incrementedCount = Promise.promise();
        redisClient.incr(key, incrementedCount);
        return incrementedCount.future().map(count -> {
            if (count == 1) {
                redisClient.expire(key, LIMITATION_TIME, __ -> {
                });
            }
            if (count > maxRequests) {
                throw new QueryLimitReachedException(error);

            }
            return true;
        });
    }
}
