package io.jenkins.plugins.hiddenlayer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import okhttp3.Authenticator;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

class ModelScanServiceFactoryTest {

    @Test
    void matchesNoProxyPattern_returnsTrue_whenHostMatchesExactPattern() {
        List<Pattern> patterns = Arrays.asList(Pattern.compile("api\\.hiddenlayer\\.ai"));

        assertTrue(ModelScanServiceFactory.matchesNoProxyPattern(patterns, "api.hiddenlayer.ai"));
    }

    @Test
    void matchesNoProxyPattern_returnsTrue_whenHostMatchesWildcardPattern() {
        List<Pattern> patterns = Arrays.asList(Pattern.compile(".*\\.internal\\.corp"));

        assertTrue(ModelScanServiceFactory.matchesNoProxyPattern(patterns, "api.internal.corp"));
        assertTrue(ModelScanServiceFactory.matchesNoProxyPattern(patterns, "web.internal.corp"));
    }

    @Test
    void matchesNoProxyPattern_returnsFalse_whenHostDoesNotMatch() {
        List<Pattern> patterns = Arrays.asList(Pattern.compile("localhost"), Pattern.compile(".*\\.internal\\.corp"));

        assertFalse(ModelScanServiceFactory.matchesNoProxyPattern(patterns, "api.hiddenlayer.ai"));
        assertFalse(ModelScanServiceFactory.matchesNoProxyPattern(patterns, "external.com"));
    }

    @Test
    void matchesNoProxyPattern_returnsFalse_whenPatternsEmpty() {
        List<Pattern> patterns = Collections.emptyList();

        assertFalse(ModelScanServiceFactory.matchesNoProxyPattern(patterns, "api.hiddenlayer.ai"));
    }

    @Test
    void matchesNoProxyPattern_returnsTrue_whenAnyPatternMatches() {
        List<Pattern> patterns =
                Arrays.asList(Pattern.compile("localhost"), Pattern.compile("127\\.0\\.0\\.1"), Pattern.compile(".*"));

        assertTrue(ModelScanServiceFactory.matchesNoProxyPattern(patterns, "anything.example.com"));
    }

    @Test
    void createProxyAuthenticator_addsProxyAuthorizationHeader() throws Exception {
        Authenticator authenticator = ModelScanServiceFactory.createProxyAuthenticator("testuser", "testpass");

        Request originalRequest =
                new Request.Builder().url("https://api.hiddenlayer.ai/test").build();

        Response challengeResponse = new Response.Builder()
                .request(originalRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(407)
                .message("Proxy Authentication Required")
                .build();

        Request authenticatedRequest = authenticator.authenticate(null, challengeResponse);

        assertNotNull(authenticatedRequest);
        String authHeader = authenticatedRequest.header("Proxy-Authorization");
        assertNotNull(authHeader);
        assertTrue(authHeader.startsWith("Basic "));

        String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)));
        assertEquals("testuser:testpass", decoded);
    }

    @Test
    void createProxyAuthenticator_handlesEmptyPassword() throws Exception {
        Authenticator authenticator = ModelScanServiceFactory.createProxyAuthenticator("testuser", "");

        Request originalRequest =
                new Request.Builder().url("https://api.hiddenlayer.ai/test").build();

        Response challengeResponse = new Response.Builder()
                .request(originalRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(407)
                .message("Proxy Authentication Required")
                .build();

        Request authenticatedRequest = authenticator.authenticate(null, challengeResponse);

        assertNotNull(authenticatedRequest);
        String authHeader = authenticatedRequest.header("Proxy-Authorization");
        String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)));
        assertEquals("testuser:", decoded);
    }
}
