package com.payment.emv.host.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public abstract class JSONFactory {

	public static Gson create() {
		GsonBuilder gsonBuilder = new GsonBuilder().setPrettyPrinting();

		return gsonBuilder.disableHtmlEscaping().create();
	}

	public static String toJson(final Object dto) {
		return create().toJson(dto);
	}

	public static Object fromJSON(final String secret, final Class clazz) {
		return create().fromJson(secret, clazz);
	}
}
