package me.edgan.redditslide.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import net.dean.jraw.RedditClient;
import net.dean.jraw.http.OkHttpAdapter;
import net.dean.jraw.http.UserAgent;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * A real {@link RedditClient} whose requests never leave the test: an interceptor answers each one
 * from {@code route} and records it. Each request is kept as {@code path?query}, and the
 * Authorization header each carried is kept beside it.
 */
public final class FakeReddit {
    /** One canned answer: an HTTP status and a body, JSON unless it is the HTML block page. */
    public static final class Answer {
        final int code;
        final String body;
        final String contentType;

        Answer(int code, String body, String contentType) {
            this.code = code;
            this.body = body;
            this.contentType = contentType;
        }

        public static Answer json(int code, String body) {
            return new Answer(code, body, "application/json; charset=UTF-8");
        }

        /** Reddit's "blocked by network security" page. */
        public static Answer blocked() {
            return new Answer(403, "<body>You've been blocked by network security.</body>", "text/html");
        }
    }

    public static final String TOKEN = "bearer test-token";

    public final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    public final List<String> authorizations = Collections.synchronizedList(new ArrayList<>());
    public final RedditClient client;

    public FakeReddit(Function<Request, Answer> route) {
        OkHttpClient http =
                new OkHttpClient.Builder()
                        .addInterceptor(
                                chain -> {
                                    Request request = chain.request();
                                    String query = request.url().encodedQuery();
                                    requests.add(
                                            request.url().encodedPath()
                                                    + (query == null ? "" : "?" + query));
                                    String auth = request.header("Authorization");
                                    authorizations.add(auth == null ? "" : auth);
                                    Answer answer = route.apply(request);
                                    return new Response.Builder()
                                            .request(request)
                                            .protocol(Protocol.HTTP_1_1)
                                            .code(answer.code)
                                            .message("test")
                                            .header("Content-Type", answer.contentType)
                                            // Generous, so JRAW's own rate limiter never waits.
                                            .header("X-Ratelimit-Remaining", "100000")
                                            .header("X-Ratelimit-Reset", "60")
                                            .body(
                                                    ResponseBody.create(
                                                            answer.body,
                                                            MediaType.get(answer.contentType)))
                                            .build();
                                })
                        .build();
        OkHttpAdapter adapter = new OkHttpAdapter(http, Protocol.HTTP_1_1);
        adapter.getDefaultHeaders().put("Authorization", TOKEN);
        client = new RedditClient(UserAgent.of("test"), adapter);
    }

    /** A post's own {@code /comments/<id>} answer, its first listing holding just the post. */
    public static String postListing(String id) {
        return "[{\"kind\":\"Listing\",\"data\":{\"children\":["
                + post(id)
                + "]}},{\"kind\":\"Listing\",\"data\":{\"children\":[]}}]";
    }

    public static String post(String id) {
        return "{\"kind\":\"t3\",\"data\":{\"id\":\""
                + id
                + "\",\"name\":\"t3_"
                + id
                + "\",\"title\":\"Title "
                + id
                + "\",\"subreddit\":\"test\",\"author\":\"someone\",\"created_utc\":1,"
                + "\"saved\":false}}";
    }

    /** An {@code /api/info} answer listing these posts. */
    public static String infoListing(String... ids) {
        StringBuilder children = new StringBuilder();
        for (String id : ids) {
            if (children.length() > 0) children.append(',');
            children.append(post(id));
        }
        return "{\"kind\":\"Listing\",\"data\":{\"children\":[" + children + "],\"after\":null}}";
    }

    public static final String NOT_FOUND = "{\"message\":\"Not Found\",\"error\":404}";
}
