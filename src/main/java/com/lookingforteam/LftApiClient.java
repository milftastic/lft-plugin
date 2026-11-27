package com.lookingforteam;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Talks to the LFT server. All calls are async (OkHttp thread pool); callbacks
 * run on that pool, never the client thread.
 * <p>
 * API contract (v1):
 * <pre>
 * PUT  /v1/status         {"id": "&lt;owner hash&gt;", "keys": ["&lt;visibility key&gt;", ...], "activities": ["COX", "TOB"]}
 *      -> 204. Replaces this owner's previous keys. Empty activities = not looking
 *      (send no keys then). Servers expire a status not refreshed for a few intervals.
 * POST /v1/status/lookup  {"keys": ["&lt;visibility key&gt;", ...]}
 *      -> 200 {"statuses": [{"key": "&lt;visibility key&gt;", "activities": ["COX"]}]}
 *      Only keys published by a live, non-empty status are returned.
 * </pre>
 * See {@link NameHasher#visibilityKey}: a status is only visible to the people
 * its owner published a key for.
 */
@Slf4j
@Singleton
class LftApiClient
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	// Keeps one request reasonably sized; a full friends list (400) plus a clan (500) fits.
	static final int MAX_KEYS = 1000;

	/**
	 * Everyone uses the same server, since you can only see friends on the same one.
	 * Developers can point a client elsewhere (e.g. a local server) by starting it
	 * with {@code -Dlft.serverUrl=http://localhost:8080}.
	 */
	private static final String SERVER_URL = System.getProperty("lft.serverUrl", "https://osrs-lft.fly.dev");

	private final OkHttpClient http;
	private final Gson gson;

	@Inject
	LftApiClient(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	/** @param onComplete called once when done: with null on success, or an error message */
	void publish(String id, Collection<String> keys, Set<Activity> activities, Consumer<String> onComplete)
	{
		HttpUrl url = endpoint("v1/status");
		if (url == null)
		{
			onComplete.accept("Invalid server URL");
			return;
		}

		List<String> names = new ArrayList<>();
		activities.forEach(a -> names.add(a.name()));
		// Not looking: send no keys, so nothing about friends leaves the client.
		List<String> keyList = names.isEmpty() ? Collections.emptyList() : limit(keys);
		Request request = new Request.Builder()
			.url(url)
			.put(RequestBody.create(JSON, gson.toJson(new PublishRequest(id, keyList, names))))
			.build();

		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("LFT publish failed", e);
				onComplete.accept("Can't reach server");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					onComplete.accept(response.isSuccessful() ? null : errorMessage(response.code()));
				}
			}
		});
	}

	void lookup(Collection<String> keys, Consumer<List<StatusDto>> onSuccess, Consumer<String> onError)
	{
		if (keys.isEmpty())
		{
			onSuccess.accept(Collections.emptyList());
			return;
		}

		HttpUrl url = endpoint("v1/status/lookup");
		if (url == null)
		{
			onError.accept("Invalid server URL");
			return;
		}

		Request request = new Request.Builder()
			.url(url)
			.post(RequestBody.create(JSON, gson.toJson(new LookupRequest(limit(keys)))))
			.build();

		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("LFT lookup failed", e);
				onError.accept("Can't reach server");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					ResponseBody responseBody = response.body();
					if (!response.isSuccessful() || responseBody == null)
					{
						onError.accept(errorMessage(response.code()));
						return;
					}

					LookupResponse parsed = gson.fromJson(responseBody.charStream(), LookupResponse.class);
					onSuccess.accept(parsed == null || parsed.statuses == null
						? Collections.emptyList()
						: parsed.statuses);
				}
				catch (JsonParseException e)
				{
					log.debug("Bad LFT lookup response", e);
					onError.accept("Bad response from server");
				}
			}
		});
	}

	private HttpUrl endpoint(String path)
	{
		HttpUrl base = HttpUrl.parse(SERVER_URL.trim());
		return base == null ? null : base.newBuilder().addPathSegments(path).build();
	}

	private static String errorMessage(int code)
	{
		switch (code)
		{
			case 429:
				return "Too many requests, slowing down. Will retry.";
			case 503:
				return "Server is busy. Will retry.";
			default:
				return "Server error " + code;
		}
	}

	private static List<String> limit(Collection<String> keys)
	{
		List<String> list = new ArrayList<>(keys);
		return list.size() > MAX_KEYS ? list.subList(0, MAX_KEYS) : list;
	}

	private static final class PublishRequest
	{
		final String id;
		final List<String> keys;
		final List<String> activities;

		PublishRequest(String id, List<String> keys, List<String> activities)
		{
			this.id = id;
			this.keys = keys;
			this.activities = activities;
		}
	}

	private static final class LookupRequest
	{
		final List<String> keys;

		LookupRequest(List<String> keys)
		{
			this.keys = keys;
		}
	}

	private static final class LookupResponse
	{
		List<StatusDto> statuses;
	}

	static final class StatusDto
	{
		String key;
		List<String> activities;
	}
}
