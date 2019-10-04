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
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.shiro.ShiroAuth;
import io.vertx.ext.auth.shiro.ShiroAuthOptions;
import io.vertx.ext.auth.shiro.ShiroAuthRealmType;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;

import static io.vertx.ext.auth.shiro.PropertiesProviderConstants.PROPERTIES_PROPS_PATH_FIELD;

public class HttpServerVerticle extends AbstractVerticle {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("VertxHttpServer");
    private static final String HEROKU_DATA_SOURCE = "heroku_data_source";

    private RemoveUserService removeUserService;
    private GetUserInfoService getUserInfoService;

    public static void main(final String[] args) {
        Launcher.executeCommand("run", HttpServerVerticle.class.getName(),
                "-conf config/my-application-conf.json");
    }

    @Override
    public void start(Promise<Void> prom) {
        MongoClient prodMongoClient = initMongoClient();
        MongoClient herokuMongoClient = initHerokuMongoClient();
        RedisClient redisClient = initRedisClient();
        initServices(prodMongoClient, herokuMongoClient, redisClient);

        Router router = initRouter();
        createHttpServer(prom, router);
    }

    private void initServices(MongoClient mongoClient, MongoClient herokuMongoClient, RedisClient redisClient) {
        getUserInfoService = new GetUserInfoService(mongoClient, redisClient);
        removeUserService = new RemoveUserService(herokuMongoClient);
    }

    @NotNull
    private Router initRouter() {
        Router router = Router.router(vertx);

        AuthProvider authProvider = initAuthProvider(router);

        // Main services endpoints

        router.route("/users/:id/info")
                .handler(rc -> checkAuth(rc, "get_info"))
                .handler(this::getUser);

        // We need to manually handle 401 here, otherwise, an error on trying to redirect DELETE method happens
        router.delete("/users/phone/:phone")
                .handler(rc -> checkAuth(rc, "remove_users"))
                .handler(this::deleteUser)
                .failureHandler(this::handle401);

        // Health check for monitoring tools
        router.get("/health").handler(rc -> rc.response().end("OK"));

        // Handles auth
        router.route("/loginhandler")
                .handler(BodyHandler.create())
                .handler(FormLoginHandler.create(authProvider).setDirectLoggedInOKURL("/"));

        router.route("/logout").handler(this::logout);

        // Links to static pages
        router.get("/login").handler(StaticHandler.create("webroot/login").setCachingEnabled(false));
        // This one shows that authentication required to access the page
        router.get("/userinfo")
                .handler(RedirectAuthHandler.create(authProvider, "/login/"))
                .handler(StaticHandler.create("webroot/getuser").setCachingEnabled(false).setCachingEnabled(false));
        router.get("/removeuser")
                .handler(RedirectAuthHandler.create(authProvider, "/login/"))
                .handler(StaticHandler.create("webroot/removeuser").setCachingEnabled(false).setCachingEnabled(false));
        router.get("/static/*").handler(StaticHandler.create());
        router.get("/").handler(StaticHandler.create("webroot/main"));

        // Common errors handling
        router.errorHandler(401, this::redirectToLogin);
        router.errorHandler(500, this::logSevere);

        return router;
    }

    private void logout(RoutingContext rc) {
        rc.clearUser();
        // Redirect back to the index page
        rc.response().putHeader("location", "/").setStatusCode(302).end();
    }

    private void logSevere(RoutingContext ar) {
        LOGGER.severe("500 error: Request: " + sanitizeParam(ar.request().path()) +
                ", params: " + sanitizeParam(ar.request().params().toString()) +
                ", user: " + ar.user());
        // According to docs, we must not call ar.next() here
    }

    private void redirectToLogin(RoutingContext rc) {
        rc.response().putHeader("location", "/login").setStatusCode(302).end();
    }

    private void handle401(RoutingContext rc) {
        // We need to manually handle 401 here, otherwise, an error on trying to redirect DELETE method happens
        if (rc.statusCode() == 401) {
            rc.response().setStatusCode(401).end("Please, login");
        } else {
            rc.next();
        }
    }

    private void checkAuth(RoutingContext rc, String authority) {
        if (rc.user() == null) {
            LOGGER.info("Unauthenticated request to: " + rc.request().path());
            rc.response().setStatusCode(401).end("Please, login");
            return;
        }
        rc.user().isAuthorized(authority, authResult -> {
            if (authResult.succeeded()) {
                if (authResult.result()) {
                    rc.next();
                } else {
                    LOGGER.warning("Unauthorized request to: " + rc.request().path() + " from user: " + rc.user());
                    rc.response().setStatusCode(403).end("Unauthorized");
                }
            } else {
                LOGGER.warning("Auth check failure: " + rc.request().path() + " from user: " + rc.user());
                rc.fail(authResult.cause());
            }
        });
    }

    @NotNull
    private AuthProvider initAuthProvider(Router router) {
        initUserAuthConfig();

        // Simple auth service which uses a properties file for user/role info
        ShiroAuthOptions options = new ShiroAuthOptions()
                .setType(ShiroAuthRealmType.PROPERTIES)
                .setConfig(new JsonObject().put(PROPERTIES_PROPS_PATH_FIELD, Utils.getParam("USER_CONFIG_PATH")));
        AuthProvider authProvider = ShiroAuth.create(vertx, options);
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)).setAuthProvider(authProvider));
        return authProvider;
    }

    /**
     * We don't want to expose user config, so getting it via env variables
     */
    private void initUserAuthConfig() {
        File configFile = new File(Utils.getParam("USER_CONFIG_PATH"));
        if (!configFile.exists()) {
            try (FileOutputStream fos = new FileOutputStream(configFile)) {

                String downloadUrl = Utils.getParam("USER_CONFIG_URL");
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(downloadUrl).build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("Failed to download file: " + response);
                }

                LOGGER.info("User config downloaded");
                fos.write(response.body().bytes());
                LOGGER.info("Config written to file");
            } catch (IOException e) {
                LOGGER.severe("Could not download user config: " + e);
            }
        } else {
            LOGGER.info("User config file already exists");
        }
    }

    private MongoClient initMongoClient() {
        String uri = Utils.getParam("MONGODB_URI");
        LOGGER.fine("Initializing Mongo client. Uri found: " + !uri.isEmpty());
        JsonObject mongoconfig = new JsonObject().put("connection_string", uri);
        return MongoClient.createShared(vertx, mongoconfig);
    }

    private MongoClient initHerokuMongoClient() {
        String uri = Utils.getParam("MONGO_DB_HEROKU");
        LOGGER.fine("Initializing Mongo Heroku client. Uri found: " + !uri.isEmpty());
        JsonObject mongoconfig = new JsonObject().put("connection_string", uri);
        return MongoClient.createShared(vertx, mongoconfig, HEROKU_DATA_SOURCE);
    }

    private RedisClient initRedisClient() {
        URI redis = URI.create(Utils.getParam("REDISCLOUD_URL"));
        LOGGER.fine("Initializing Redis client. Uri found: " + !redis.getHost().isEmpty());
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
                LOGGER.info("Server started: http://localhost:" + portString);
            } else {
                prom.fail(result.cause());
                LOGGER.severe("Error on starting the server: " + result.cause());
            }
        });
    }

    private void getUser(RoutingContext rc) {
        final String id = rc.request().getParam("id");
        final String sanitizedId = sanitizeParam(id);
        final String remoteIp = rc.request().remoteAddress().host();
        Future<JsonObject> promise = getUserInfoService.getUserInfo(sanitizedId, remoteIp);

        promise.setHandler(ar -> {
            if (ar.succeeded()) {
                LOGGER.info("Got info about the user: " + sanitizedId);
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
        String phone = routingContext.request().getParam("phone");
        String sanitizedPhone = sanitizeParam(phone);
        removeUserService.deleteUser(sanitizedPhone, routingContext);
    }

    private String sanitizeParam(String param) {
        return param.replaceAll("[\n\r\t]", "_");
    }
}

// TODO: log removals and users who did it
