package io.jenkins.plugins.hiddenlayer;

import com.hiddenlayer.api.client.HiddenLayerClient;
import com.hiddenlayer.api.client.okhttp.HiddenLayerOkHttpClient;
import hudson.ProxyConfiguration;
import hudson.util.Secret;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;

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

        HiddenLayerOkHttpClient.Builder builder =
                HiddenLayerOkHttpClient.builder().clientId(clientId).clientSecret(clientSecret.getPlainText());

        Proxy proxy = getJenkinsProxy();
        if (proxy != null) {
            builder.proxy(proxy);
            configureProxyAuthentication();
        }

        HiddenLayerClient client = builder.build();
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

    private static void configureProxyAuthentication() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null || jenkins.proxy == null) {
            return;
        }

        ProxyConfiguration proxyConfig = jenkins.proxy;
        String username = proxyConfig.getUserName();
        Secret password = proxyConfig.getSecretPassword();

        if (username == null || username.isEmpty()) {
            return;
        }

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == RequestorType.PROXY) {
                    return new PasswordAuthentication(
                            username, password != null ? password.getPlainText().toCharArray() : new char[0]);
                }
                return null;
            }
        });
    }
}
