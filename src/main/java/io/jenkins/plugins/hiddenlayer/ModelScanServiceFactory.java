package io.jenkins.plugins.hiddenlayer;

import com.hiddenlayer.api.client.HiddenLayerClient;
import com.hiddenlayer.api.client.okhttp.HiddenLayerOkHttpClient;
import hudson.ProxyConfiguration;
import hudson.util.Secret;
import java.net.InetSocketAddress;
import java.net.Proxy;
import jenkins.model.Jenkins;

public class ModelScanServiceFactory {

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

        String proxyHost = proxyConfig.name;
        int proxyPort = proxyConfig.port;

        if (proxyHost == null || proxyHost.isEmpty()) {
            return null;
        }

        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }
}
