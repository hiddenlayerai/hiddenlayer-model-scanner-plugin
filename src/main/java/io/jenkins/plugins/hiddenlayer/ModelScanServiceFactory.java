package io.jenkins.plugins.hiddenlayer;

import com.hiddenlayer.api.client.HiddenLayerClient;
import com.hiddenlayer.api.client.HiddenLayerClientImpl;
import com.hiddenlayer.api.core.ClientOptions;
import hudson.ProxyConfiguration;
import hudson.util.Secret;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class ModelScanServiceFactory {

    private static final String HIDDENLAYER_API_HOST = "api.hiddenlayer.ai";

    private static ScannerService instance;

    public static void setTestInstance(ScannerService scanner) {
        instance = scanner;
    }

    public static ScannerService getInstance(String clientId, Secret clientSecret) {
        if (instance != null) {
            return instance;
        }

        JenkinsHttpClient.Builder httpClientBuilder = JenkinsHttpClient.builder();

        Proxy proxy = getJenkinsProxy();
        if (proxy != null) {
            httpClientBuilder.proxy(proxy);
            Authenticator proxyAuth = getProxyAuthenticator();
            if (proxyAuth != null) {
                httpClientBuilder.proxyAuthenticator(proxyAuth);
            }
        }

        ClientOptions clientOptions = ClientOptions.builder()
                .httpClient(httpClientBuilder.build())
                .clientId(clientId)
                .clientSecret(clientSecret.getPlainText())
                .build();

        HiddenLayerClient client = new HiddenLayerClientImpl(clientOptions);
        return new ModelScannerWrapper(client.modelScanner());
    }

    private static Proxy getJenkinsProxy() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return null;
        }

        ProxyConfiguration proxyConfig = jenkins.proxy;
        if (proxyConfig == null) {
            return null;
        }

        if (isNoProxyHost(proxyConfig, HIDDENLAYER_API_HOST)) {
            return null;
        }

        String proxyHost = proxyConfig.name;
        int proxyPort = proxyConfig.port;

        if (proxyHost == null || proxyHost.isEmpty()) {
            return null;
        }

        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }

    private static boolean isNoProxyHost(ProxyConfiguration proxyConfig, String host) {
        for (Pattern pattern : proxyConfig.getNoProxyHostPatterns()) {
            if (pattern.matcher(host).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Authenticator getProxyAuthenticator() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null || jenkins.proxy == null) {
            return null;
        }

        ProxyConfiguration proxyConfig = jenkins.proxy;
        String username = proxyConfig.getUserName();
        Secret password = proxyConfig.getSecretPassword();

        if (username == null || username.isEmpty()) {
            return null;
        }

        String passwordStr = password != null ? password.getPlainText() : "";

        return new Authenticator() {
            @Override
            public Request authenticate(Route route, Response response) {
                String credential = Credentials.basic(username, passwordStr);
                return response.request()
                        .newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            }
        };
    }
}
