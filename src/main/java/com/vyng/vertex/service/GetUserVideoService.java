package com.vyng.vertex.service;

import com.mongodb.lang.NonNull;
import com.vyng.vertex.error.NotFoundException;
import com.vyng.vertex.error.QueryLimitReachedException;
import com.vyng.vertex.utils.Utils;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.redis.RedisClient;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class GetUserVideoService {
    private static final Logger LOGGER = java.util.logging.Logger.getLogger("GetUserVideoService");


    private static final int MAX_USER_REQUESTS = Integer.parseInt(Utils.getParam("MAX_USER_REQUESTS"));
    private static final int MAX_TOTAL_REQUESTS = Integer.parseInt(Utils.getParam("MAX_TOTAL_REQUESTS"));
    private static final String KEY_GET_TOTAL_COUNT = Utils.getParam("ENV", "unknown") + ":get:user:total";
    private static final String KEY_GET_USER_COUNT = Utils.getParam("ENV", "unknown") + ":get:user:";


    private final MongoClient mongoClient;

    private final RedisClient redisClient;

    private final RateLimiterService rateLimiterService;

    public GetUserVideoService(@NonNull MongoClient mongoClient, @NonNull RedisClient redisClient) {
        this.mongoClient = mongoClient;
        this.redisClient = redisClient;
        this.rateLimiterService=new RateLimiterService(this.redisClient);
    }

    public Future<JsonObject> getUserVideos(String  phoneNumber, String remoteIp)
    {
        LOGGER.info("Getting user info for: " + phoneNumber);

        // Add rate limit on total queries to the get user resource
        io.vertx.core.Future<Boolean> totalSuccess =
        rateLimiterService.getTotalRequestLimitFuture(KEY_GET_TOTAL_COUNT, MAX_TOTAL_REQUESTS, "Max daily total info request count reached");

        // Add rate limit on queries to the get user resource from one ip
        io.vertx.core.Future<Boolean> userSuccess =
                rateLimiterService.getTotalRequestLimitFuture(KEY_GET_USER_COUNT + remoteIp, MAX_USER_REQUESTS, "Max daily user info request count reached");

        return CompositeFuture.all(totalSuccess, userSuccess)
                .compose(__ -> getUserVideosByPhoneNumber(phoneNumber)).map(entry->flattenJson(phoneNumber,entry));
    }



    private io.vertx.core.Future<JsonObject> getUserVideosByPhoneNumber(String id) {
        Promise<JsonObject> mongoPromise = Promise.promise();
        JsonArray criteriaArray=new JsonArray();
        JsonObject phoneCriteria=new JsonObject().put("phoneNumber",id);
        JsonObject phoneNumbersMatchCriteria=new JsonObject().put("phoneNumbers",new JsonObject().put("$elemMatch",new JsonObject().put("$eq",id)));
        criteriaArray.add(phoneCriteria);
        criteriaArray.add(phoneNumbersMatchCriteria);
        mongoClient.findOne("users",
                new JsonObject().put("$or",criteriaArray),
                new JsonObject().put("_id", 1).put("phoneNumber", 1).put("createdAt", 1).put("profileVideo.videoUrls.sd",1).put("profileVideo.videoUrls.hd",1)
                .put("profileVideo.videoUrls.sd",1)
                .put("profileVideo.videoUrls.sd_share",1)
                .put("profileVideo.videoUrls.sd_logo",1).put("profileVideo.thumbnails",1)
                , mongoPromise);
        return mongoPromise.future();
    }

    @NotNull
    private JsonObject flattenJson(String id, JsonObject entry) {
        if (entry == null) {
            throw new NotFoundException("Object with the id was not found: " + id);
        }
        String id1 = entry.getString("phoneNumber");
        entry.put("_id", id1);
        String createdAt = entry.getJsonObject("createdAt").getString("$date");
        entry.put("createdAt", createdAt);
        return entry;
    }
}
