package com.vyng.vertex.verticle;

import com.vyng.vertex.error.NotFoundException;
import com.vyng.vertex.error.QueryLimitReachedException;
import com.vyng.vertex.service.GetUserInfoService;
import com.vyng.vertex.service.RemoveUserService;
import com.vyng.vertex.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Launcher;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

public class HttpServerVerticle extends AbstractVerticle {

    private final static java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("VertxHttpServer");

    private RemoveUserService removeUserService;
    private GetUserInfoService getUserInfoService;

    public static void main(final String[] args) {
        Launcher.executeCommand("run", HttpServerVerticle.class.getName(),
                "-conf src/main/conf/my-application-conf.json");
    }

    @Override
    public void start(Promise<Void> prom) {
        MongoClient mongoClient = initMongoClient();
        RedisClient redisClient = initRedisClient();
        initServices(mongoClient, redisClient);

        Router router = initRouter();
        createHttpServer(prom, router);
    }

    private void initServices(MongoClient mongoClient, RedisClient redisClient) {
        getUserInfoService = new GetUserInfoService(mongoClient, redisClient);
        removeUserService = new RemoveUserService(mongoClient);
    }

    @NotNull
    private Router initRouter() {
        Router router = Router.router(vertx);

        router.route("/users/:phone").handler(this::deleteUser);
        router.route("/info/:id").handler(this::getUser);
        // health check
        router.get("/health").handler(rc -> rc.response().end("OK"));
        // links to pages
        router.get("/userinfo").handler(StaticHandler.create("webroot/getuser"));
        router.get("/removeuser").handler(StaticHandler.create("webroot/removeuser"));
        router.get("/").handler(StaticHandler.create("webroot/main"));
        router.route("/static/*").handler(StaticHandler.create());
        return router;
    }

    private MongoClient initMongoClient() {
        String uri = Utils.getParam("MONGODB_URI");
        JsonObject mongoconfig = new JsonObject().put("connection_string", uri);
        return MongoClient.createShared(vertx, mongoconfig);
    }

    private RedisClient initRedisClient() {
        URI redis = URI.create(Utils.getParam("REDISCLOUD_URL"));
        RedisOptions redisOptions = new RedisOptions()
                .setAuth(redis.getUserInfo().split(":", 2)[1])
                .setHost(redis.getHost())
                .setPort(redis.getPort());
        return RedisClient.create(vertx, redisOptions);
    }

    private void createHttpServer(Promise<Void> prom, Router router) {
        String portString = Utils.getParam("PORT");
        int port;
        try {
            port = Integer.parseInt(portString);
        } catch (NumberFormatException ex) {
            prom.fail("Port is not set in env");
            return;
        }

        HttpServerOptions options = new HttpServerOptions().setPort(port).setLogActivity(true);
        vertx.createHttpServer(options).requestHandler(router).listen(result -> {
            if (result.succeeded()) {
                prom.complete();
            } else {
                prom.fail(result.cause());
            }
        });
        LOGGER.info("Server started: http://localhost:" + portString);
    }

    private void getUser(RoutingContext rc) {
        final String id = rc.request().getParam("id");
        final String remoteIp = rc.request().remoteAddress().host();
        Future<JsonObject> promise = getUserInfoService.getUserInfo(id, remoteIp);
        promise.setHandler(ar -> {
            if (ar.succeeded()) {
                LOGGER.info("Got info about the user: " + id);
                rc.response().setStatusCode(200)
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(Json.encodePrettily(ar.result()));
            } else {
                LOGGER.warning("Can't get info about the user: " + ar.toString());
                if (ar.cause() instanceof QueryLimitReachedException) {
                    rc.response().setStatusCode(429)
                            .putHeader("content-type", "text/plain; charset=utf-8")
                            .end(ar.cause().getMessage());
                } else if (ar.cause() instanceof NotFoundException) {
                    rc.response().setStatusCode(404)
                            .putHeader("content-type", "text/plain; charset=utf-8")
                            .end(ar.cause().getMessage());
                } else {
                    String message = ar.cause() != null && ar.cause().getMessage() != null ? ar.cause().getMessage() : ar.cause().toString();
                    rc.response().setStatusCode(400)
                            .putHeader("content-type", "text/plain; charset=utf-8")
                            .end(message);
                }
            }
        });
    }

    private void deleteUser(RoutingContext routingContext) {
        removeUserService.deleteUser(routingContext.request().getParam("phone"), routingContext);
    }
}

// TODO: require a token to remove a number
// TODO: log removals and users who did it
