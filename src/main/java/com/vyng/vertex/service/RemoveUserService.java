package com.vyng.vertex.service;

import com.vyng.vertex.utils.Errors;
import com.vyng.vertex.utils.Utils;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import okhttp3.*;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RemoveUserService  {

    private final static java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("VertxHttpServer");
    public static final String REMOVE_USER_ENDPOINT = Utils.getParam("REMOVE_USER_ENDPOINT");
    public static final String DELETE_TOKEN_ENV = "DELETE_TOKEN";

    public static final Set<String> ALLOWED_TO_REMOVE_PHONES = new HashSet<>();

    static {
        Collections.addAll(ALLOWED_TO_REMOVE_PHONES,"" ); // TODO: move to Mongo
    }

	private final MongoClient mongoClient;

    public RemoveUserService(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
	}

	public void deleteUser(String phone, RoutingContext routingContext) {
        String msg = "Deleting a user: " + phone;
        LOGGER.info(msg);

        if (!allowedToDelete(phone)) {
            String errorMsg = "Tried to remove a non-whitelisted number: " + phone;
            LOGGER.warning(errorMsg);
            Errors.error(routingContext, 403, errorMsg);
            return;
        }

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

    private boolean allowedToDelete(String phone) {
        return ALLOWED_TO_REMOVE_PHONES.contains(phone);
    }

}