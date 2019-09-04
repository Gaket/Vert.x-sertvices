package com.vyng.vertex.service;

import com.mongodb.lang.NonNull;
import com.vyng.vertex.error.NotFoundException;
import com.vyng.vertex.error.QueryLimitReachedException;
import com.vyng.vertex.utils.Utils;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.redis.RedisClient;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class GetUserInfoService {

    private static final Logger LOGGER = java.util.logging.Logger.getLogger("GetUserInfoService");

    private static final int MAX_USER_REQUESTS = Integer.parseInt(Utils.getParam("MAX_USER_REQUESTS"));
    private static final int MAX_TOTAL_REQUESTS = Integer.parseInt(Utils.getParam("MAX_TOTAL_REQUESTS"));
    private static final int MONGO_ID_LENGTH = 24;
    private static final String KEY_GET_TOTAL_COUNT = Utils.getParam("ENV", "unknown") + ":get:user:total";
    private static final String KEY_GET_USER_COUNT = Utils.getParam("ENV", "unknown") + ":get:user:";
    private static final int LIMITATION_TIME = (int) TimeUnit.HOURS.toSeconds(12);

    @NonNull
    private final MongoClient mongoClient;
    @NonNull
    private final RedisClient redisClient;

    public GetUserInfoService(MongoClient mongoClient, RedisClient redisClient) {
        this.mongoClient = mongoClient;
        this.redisClient = redisClient;
    }

    public Future<JsonObject> getUserInfo(String id, String remoteIp) {
        LOGGER.info("Getting user info for: " + id);
        if (id.length() != MONGO_ID_LENGTH) {
            return Future.failedFuture("Unexpected id length");
        }

        // Add rate limit on total queries to the get user resource
        Future<Boolean> totalSuccess =
                getTotalRequestLimitFuture(KEY_GET_TOTAL_COUNT, MAX_TOTAL_REQUESTS, "Max daily total info request count reached");

        // Add rate limit on queries to the get user resource from one ip
        Future<Boolean> userSuccess =
                getTotalRequestLimitFuture(KEY_GET_USER_COUNT + remoteIp, MAX_USER_REQUESTS, "Max daily user info request count reached");

        return CompositeFuture.all(totalSuccess, userSuccess)
                .compose(__ -> getUserById(id))
                .map(entry -> flattenJson(id, entry));
    }

    @NotNull
    private JsonObject flattenJson(String id, JsonObject entry) {
        if (entry == null) {
            throw new NotFoundException("Object with the id was not found: " + id);
        }

        String id1 = entry.getJsonObject("_id").getString("$oid");
        entry.put("_id", id1);
        String createdAt = entry.getJsonObject("createdAt").getString("$date");
        entry.put("createdAt", createdAt);
        return entry;
    }

    private Future<JsonObject> getUserById(String id) {
        Promise<JsonObject> mongoPromise = Promise.promise();
        mongoClient.findOne("users",
                new JsonObject().put("_id", new JsonObject().put("$oid", id)),
                new JsonObject().put("_id", 1).put("phoneNumber", 1).put("createdAt", 1), mongoPromise);
        return mongoPromise.future();
    }

    private Future<Boolean> getTotalRequestLimitFuture(String key, int maxRequests, String error) {
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
