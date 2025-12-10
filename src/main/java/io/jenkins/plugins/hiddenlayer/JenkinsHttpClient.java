package io.jenkins.plugins.hiddenlayer;

import com.hiddenlayer.api.core.RequestOptions;
import com.hiddenlayer.api.core.Timeout;
import com.hiddenlayer.api.core.http.Headers;
import com.hiddenlayer.api.core.http.HttpClient;
import com.hiddenlayer.api.core.http.HttpMethod;
import com.hiddenlayer.api.core.http.HttpRequest;
import com.hiddenlayer.api.core.http.HttpRequestBody;
import com.hiddenlayer.api.core.http.HttpResponse;
import com.hiddenlayer.api.core.http.QueryParams;
import com.hiddenlayer.api.errors.HiddenLayerIoException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import okhttp3.Authenticator;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.BufferedSink;

/**
 * Custom HttpClient implementation based on the SDK's OkHttpClient.
 * Adds support for proxy authentication via OkHttp's proxyAuthenticator.
 */
public class JenkinsHttpClient implements HttpClient {

    private final okhttp3.OkHttpClient okHttpClient;

    private JenkinsHttpClient(okhttp3.OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    @Override
    public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
        Call call = newCall(request, requestOptions);
        try {
            return toResponse(call.execute());
        } catch (IOException e) {
            throw new HiddenLayerIoException("Request failed", e);
        } finally {
            if (request.body() != null) {
                request.body().close();
            }
        }
    }

    @Override
    public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions requestOptions) {
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();
        Call call = newCall(request, requestOptions);

        call.enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                future.complete(toResponse(response));
            }

            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(new HiddenLayerIoException("Request failed", e));
            }
        });

        future.whenComplete((result, e) -> {
            if (e instanceof CancellationException) {
                call.cancel();
            }
            if (request.body() != null) {
                request.body().close();
            }
        });

        return future;
    }

    @Override
    public void close() {
        okHttpClient.dispatcher().executorService().shutdown();
        okHttpClient.connectionPool().evictAll();
        if (okHttpClient.cache() != null) {
            try {
                okHttpClient.cache().close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    private Call newCall(HttpRequest request, RequestOptions requestOptions) {
        okhttp3.OkHttpClient.Builder clientBuilder = okHttpClient.newBuilder();

        String logLevel = System.getenv("HIDDENLAYER_LOG");
        if (logLevel != null) {
            HttpLoggingInterceptor.Level level = null;
            if ("info".equalsIgnoreCase(logLevel)) {
                level = HttpLoggingInterceptor.Level.BASIC;
            } else if ("debug".equalsIgnoreCase(logLevel)) {
                level = HttpLoggingInterceptor.Level.BODY;
            }
            if (level != null) {
                HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
                interceptor.setLevel(level);
                interceptor.redactHeader("Authorization");
                clientBuilder.addNetworkInterceptor(interceptor);
            }
        }

        Timeout timeout = requestOptions.getTimeout();
        if (timeout != null) {
            clientBuilder
                    .connectTimeout(timeout.connect())
                    .readTimeout(timeout.read())
                    .writeTimeout(timeout.write())
                    .callTimeout(timeout.request());
        }

        okhttp3.OkHttpClient client = clientBuilder.build();
        return client.newCall(toOkHttpRequest(request, client));
    }

    private Request toOkHttpRequest(HttpRequest request, okhttp3.OkHttpClient client) {
        RequestBody body = null;
        if (request.body() != null) {
            body = toOkHttpRequestBody(request.body());
        }
        if (body == null && requiresBody(request.method())) {
            body = RequestBody.create("", null);
        }

        HttpUrl.Builder urlBuilder = HttpUrl.parse(request.baseUrl()).newBuilder();
        for (String segment : request.pathSegments()) {
            urlBuilder.addPathSegment(segment);
        }
        QueryParams queryParams = request.queryParams();
        for (String key : queryParams.keys()) {
            for (String value : queryParams.values(key)) {
                urlBuilder.addQueryParameter(key, value);
            }
        }

        Request.Builder builder = new Request.Builder()
                .url(urlBuilder.build())
                .method(request.method().name(), body);

        Headers headers = request.headers();
        for (String name : headers.names()) {
            for (String value : headers.values(name)) {
                builder.addHeader(name, value);
            }
        }

        if (!headers.names().contains("X-Stainless-Read-Timeout") && client.readTimeoutMillis() != 0) {
            builder.addHeader(
                    "X-Stainless-Read-Timeout",
                    String.valueOf(Duration.ofMillis(client.readTimeoutMillis()).getSeconds()));
        }
        if (!headers.names().contains("X-Stainless-Timeout") && client.callTimeoutMillis() != 0) {
            builder.addHeader(
                    "X-Stainless-Timeout",
                    String.valueOf(Duration.ofMillis(client.callTimeoutMillis()).getSeconds()));
        }

        return builder.build();
    }

    private boolean requiresBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    private RequestBody toOkHttpRequestBody(HttpRequestBody body) {
        MediaType mediaType = body.contentType() != null ? MediaType.parse(body.contentType()) : null;
        long length = body.contentLength();

        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return mediaType;
            }

            @Override
            public long contentLength() {
                return length;
            }

            @Override
            public boolean isOneShot() {
                return !body.repeatable();
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                body.writeTo(sink.outputStream());
            }
        };
    }

    private HttpResponse toResponse(Response response) {
        Headers headers = toHeaders(response.headers());

        return new HttpResponse() {
            @Override
            public int statusCode() {
                return response.code();
            }

            @Override
            public Headers headers() {
                return headers;
            }

            @Override
            public InputStream body() {
                return response.body().byteStream();
            }

            @Override
            public void close() {
                response.body().close();
            }
        };
    }

    private Headers toHeaders(okhttp3.Headers okHeaders) {
        Headers.Builder builder = Headers.builder();
        for (int i = 0; i < okHeaders.size(); i++) {
            builder.put(okHeaders.name(i), okHeaders.value(i));
        }
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Timeout timeout = Timeout.builder().build();
        private Proxy proxy = null;
        private Authenticator proxyAuthenticator = null;

        public Builder timeout(Timeout timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = Timeout.builder().request(timeout).build();
            return this;
        }

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder proxyAuthenticator(Authenticator proxyAuthenticator) {
            this.proxyAuthenticator = proxyAuthenticator;
            return this;
        }

        public JenkinsHttpClient build() {
            okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(timeout.connect())
                    .readTimeout(timeout.read())
                    .writeTimeout(timeout.write())
                    .callTimeout(timeout.request());

            if (proxy != null) {
                builder.proxy(proxy);
            }

            if (proxyAuthenticator != null) {
                builder.proxyAuthenticator(proxyAuthenticator);
            }

            okhttp3.OkHttpClient client = builder.build();
            client.dispatcher().setMaxRequestsPerHost(client.dispatcher().getMaxRequests());

            return new JenkinsHttpClient(client);
        }
    }
}
