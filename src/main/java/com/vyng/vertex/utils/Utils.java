package com.vyng.vertex.utils;

import com.mongodb.lang.NonNull;
import io.vertx.core.Vertx;

public class Utils {

    @NonNull
    public static String getParam(String key) {
        return getParam(key, "");
    }

    @NonNull
    public static String getParam(String key, String def) {
        String param = Vertx.currentContext().config().getString(key);
        if (param == null) {
            param = System.getenv(key);
        }
        if (param == null) {
            param = "";
        }
        return param;
    }
}
