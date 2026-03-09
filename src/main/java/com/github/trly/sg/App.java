package com.github.trly.sg;

import java.lang.reflect.Field;

import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.ProtocolSwitchStrategy;
import org.apache.hc.client5.http.impl.auth.HttpAuthenticator;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;

/**
 * Test application that deliberately uses symbols from httpcomponents-client 5.4.4
 * that were removed or changed in 5.5.2, to verify migration breakage detection.
 */
public class App {

    public static void main(final String[] args) {
        int failures = 0;
        failures += runCheck("ProtocolSwitch enum access", App::demonstrateProtocolSwitchEnumAccess);
        failures += runCheck("HttpAuthenticator internals", App::demonstrateHttpAuthenticatorInternals);
        failures += runCheck("CloseableHttpResponse execRuntime", App::demonstrateCloseableHttpResponseExecRuntime);
        failures += runCheck("MultipartEntityBuilder generateBoundary", App::demonstrateMultipartBoundaryGeneration);
        failures += runCheck("Cookie HTTP_ONLY_ATTR case", App::demonstrateCookieHttpOnlyAttrCaseSensitive);
        failures += runCheck("HttpAuthenticator updateAuthState", App::demonstrateHttpAuthenticatorUpdateAuthState);

        System.out.println("\n=== Results: " + failures + " check(s) failed ===");
        if (failures > 0) {
            System.exit(1);
        }
    }

    @FunctionalInterface
    private interface CheckAction {
        void run() throws Exception;
    }

    private static int runCheck(final String name, final CheckAction check) {
        try {
            check.run();
            System.out.println("[PASS] " + name);
            return 0;
        } catch (final Exception | Error e) {
            System.out.println("[FAIL] " + name + ": " + e.getMessage());
            return 1;
        }
    }

    /**
     * Uses ProtocolSwitchStrategy.ProtocolSwitch enum values (FAILURE, TLS).
     * In 5.5.2, this enum was completely removed from ProtocolSwitchStrategy.
     */
    static void demonstrateProtocolSwitchEnumAccess() throws Exception {
        // Access the package-private ProtocolSwitch enum via reflection
        // In 5.4.4: enum ProtocolSwitch { FAILURE, TLS } exists
        // In 5.5.2: this enum is completely removed
        final Class<?> switchClass = Class.forName(
                "org.apache.hc.client5.http.impl.ProtocolSwitchStrategy$ProtocolSwitch");
        final Object[] constants = switchClass.getEnumConstants();
        System.out.println("ProtocolSwitch enum constants found: " + constants.length);
        for (final Object c : constants) {
            System.out.println("  - " + c);
        }
    }

    /**
     * Accesses HttpAuthenticator's internal LOG and parser fields.
     * In 5.4.4: HttpAuthenticator has its own LOG (Logger) and parser (AuthChallengeParser) fields.
     * In 5.5.2: HttpAuthenticator extends AuthenticationHandler and is @Deprecated;
     *           its own LOG/parser fields were removed (they live in the parent class now).
     */
    static void demonstrateHttpAuthenticatorInternals() throws Exception {
        final HttpAuthenticator authenticator = new HttpAuthenticator();

        // Access the private LOG field - exists in 5.4.4, removed in 5.5.2
        final Field logField = HttpAuthenticator.class.getDeclaredField("LOG");
        logField.setAccessible(true);
        final Object logger = logField.get(null);
        System.out.println("HttpAuthenticator.LOG: " + logger);

        // Access the private parser field - exists in 5.4.4, removed in 5.5.2
        final Field parserField = HttpAuthenticator.class.getDeclaredField("parser");
        parserField.setAccessible(true);
        final Object parser = parserField.get(authenticator);
        System.out.println("HttpAuthenticator.parser: " + parser.getClass().getSimpleName());
    }

    /**
     * Accesses CloseableHttpResponse's execRuntime field.
     * In 5.4.4: CloseableHttpResponse has a field `private final ExecRuntime execRuntime`.
     * In 5.5.2: this field was replaced with `private final CloseableDelegate closeableDelegate`.
     */
    static void demonstrateCloseableHttpResponseExecRuntime() throws Exception {
        final BasicClassicHttpResponse basicResponse = new BasicClassicHttpResponse(200);
        final CloseableHttpResponse response = CloseableHttpResponse.adapt(basicResponse);

        // Access the private execRuntime field - exists in 5.4.4, removed in 5.5.2
        final Field execRuntimeField = CloseableHttpResponse.class.getDeclaredField("execRuntime");
        execRuntimeField.setAccessible(true);
        final ExecRuntime runtime = (ExecRuntime) execRuntimeField.get(response);
        System.out.println("CloseableHttpResponse.execRuntime: " + runtime);

        response.close();
    }

    /**
     * Invokes MultipartEntityBuilder's private generateBoundary() method.
     * In 5.4.4: private String generateBoundary() exists and returns "httpclient_boundary_" + UUID.
     * In 5.5.2: this method was removed entirely.
     */
    static void demonstrateMultipartBoundaryGeneration() throws Exception {
        final MultipartEntityBuilder builder = MultipartEntityBuilder.create();

        // Access the private generateBoundary() method - exists in 5.4.4, removed in 5.5.2
        final java.lang.reflect.Method genMethod =
                MultipartEntityBuilder.class.getDeclaredMethod("generateBoundary");
        genMethod.setAccessible(true);
        final String boundary = (String) genMethod.invoke(builder);
        System.out.println("Generated boundary: " + boundary);

        // Verify it follows the 5.4.4 format
        if (!boundary.startsWith("httpclient_boundary_")) {
            throw new AssertionError("Expected boundary to start with 'httpclient_boundary_'");
        }
    }

    /**
     * Tests Cookie.HTTP_ONLY_ATTR constant value.
     * In 5.4.4: Cookie.HTTP_ONLY_ATTR = "httpOnly" (camelCase)
     * In 5.5.2: Cookie.HTTP_ONLY_ATTR = "httponly" (lowercase)
     * Code that depends on exact case matching will break.
     */
    static void demonstrateCookieHttpOnlyAttrCaseSensitive() {
        final String httpOnlyAttr = Cookie.HTTP_ONLY_ATTR;
        System.out.println("Cookie.HTTP_ONLY_ATTR = \"" + httpOnlyAttr + "\"");

        // In 5.4.4 this is "httpOnly" - code relying on this exact casing will break in 5.5.2
        if (!"httpOnly".equals(httpOnlyAttr)) {
            throw new AssertionError(
                    "Expected Cookie.HTTP_ONLY_ATTR to be 'httpOnly' but got '" + httpOnlyAttr + "'");
        }
        System.out.println("Cookie.HTTP_ONLY_ATTR matches expected camelCase 'httpOnly'");
    }

    /**
     * Uses HttpAuthenticator.updateAuthState() which in 5.4.4 returns boolean without
     * throwing checked exceptions. In 5.5.2 the method signature is preserved but the
     * class is @Deprecated and now extends AuthenticationHandler.
     */
    static void demonstrateHttpAuthenticatorUpdateAuthState() {
        final HttpAuthenticator authenticator = new HttpAuthenticator();
        final HttpHost host = new HttpHost("localhost");
        final BasicClassicHttpResponse response = new BasicClassicHttpResponse(401);
        final AuthExchange authExchange = new AuthExchange();
        final BasicHttpContext context = new BasicHttpContext();

        // In 5.4.4, updateAuthState is defined directly on HttpAuthenticator
        // In 5.5.2, it delegates to handleResponse() from the parent AuthenticationHandler
        final boolean updated = authenticator.updateAuthState(
                host,
                ChallengeType.TARGET,
                response,
                DefaultAuthenticationStrategy.INSTANCE,
                authExchange,
                context);
        System.out.println("Auth state updated: " + updated);
    }
}
