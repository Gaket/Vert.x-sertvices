package com.vyng.vertex.service;

import com.vyng.vertex.utils.Errors;
import com.vyng.vertex.utils.Utils;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import okhttp3.*;

import java.io.IOException;

public class RemoveUserService {

    private final static java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("VertxHttpServer");
    private static final String REMOVE_USER_ENDPOINT = Utils.getParam("REMOVE_USER_ENDPOINT");
    private static final String DELETE_TOKEN_ENV = "DELETE_TOKEN";

    private final MongoClient mongoClient;

    public RemoveUserService(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public void deleteUser(String phone, RoutingContext routingContext) {
        String msg = "Deleting a user: " + phone;
        LOGGER.info(msg);

        allowedToDelete(phone)
                .setHandler(asyncresult -> {
                    if (asyncresult.failed()) {
                        Errors.error(routingContext, 400, asyncresult.cause());
                        return;
                    }

                    if (asyncresult.result() == null || asyncresult.result().isEmpty()) {
                        String errorMsg = "Tried to remove a non-whitelisted number: " + phone;
                        Errors.error(routingContext, 403, errorMsg);
                        return;
                    }
                    removeUserThroughApi(phone, routingContext);
                });
    }

    private void removeUserThroughApi(String phone, RoutingContext routingContext) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(REMOVE_USER_ENDPOINT + phone)
                .addHeader("x-auth-token", Utils.getParam(DELETE_TOKEN_ENV)).delete().build();
        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onResponse(Call arg0, Response response) throws IOException {
                if (response.isSuccessful()) {
                    LOGGER.info("Deleted the user: " + phone);
                    routingContext.response().setStatusCode(204).end();
                } else {
                    String msg = "Failed to delete the user: " + phone;
                    LOGGER.warning(msg + ". " + response.code());
                    Errors.error(routingContext, response.code(), msg);
                }
            }

            @Override
            public void onFailure(Call arg0, IOException arg1) {
                routingContext.fail(arg1);
            }
        });
    }

    private Future<JsonObject> allowedToDelete(String phone) {
        Promise<JsonObject>  result = Promise.promise();
        mongoClient.findOne("phones_to_delete", new JsonObject().put("phone", phone), null, result);
        return result.future();
    }
}