package com.github.trly.sg;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteInfo;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.entity.mime.FormBodyPartBuilder;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.IdleConnectionEvictor;
import org.apache.hc.client5.http.impl.InMemoryDnsResolver;
import org.apache.hc.client5.http.impl.auth.HttpAuthenticator;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.util.TimeValue;

/**
 * Test application that references public API symbols from httpcomponents-client 5.4.4
 * whose definitions are in files that changed between 5.4.4 and 5.5.2.
 *
 * These are all typed, public API references that SCIP can track for cross-repo
 * usage detection via the usagesForSymbol API.
 */
public class App {

    public static void main(final String[] args) {
        int failures = 0;
        failures += runCheck("Cookie.HTTP_ONLY_ATTR constant", App::checkCookieHttpOnlyAttr);
        failures += runCheck("DnsResolver.resolve(String)", App::checkDnsResolverResolve);
        failures += runCheck("InMemoryDnsResolver usage", App::checkInMemoryDnsResolver);
        failures += runCheck("HttpAuthenticator.updateAuthState()", App::checkHttpAuthenticatorUpdateAuthState);
        failures += runCheck("DefaultHttpRequestRetryStrategy", App::checkDefaultHttpRequestRetryStrategy);
        failures += runCheck("MultipartEntityBuilder public API", App::checkMultipartEntityBuilder);
        failures += runCheck("FormBodyPartBuilder.create()", App::checkFormBodyPartBuilder);
        failures += runCheck("DefaultRedirectStrategy.isRedirected()", App::checkDefaultRedirectStrategy);
        failures += runCheck("AuthExchange state management", App::checkAuthExchange);
        failures += runCheck("AuthScope matching", App::checkAuthScope);
        failures += runCheck("CloseableHttpResponse.adapt()", App::checkCloseableHttpResponse);
        failures += runCheck("HttpRoute construction", App::checkHttpRoute);
        failures += runCheck("RequestConfig builder", App::checkRequestConfig);
        failures += runCheck("ConnectionConfig builder", App::checkConnectionConfig);
        failures += runCheck("DefaultConnectionKeepAliveStrategy", App::checkKeepAliveStrategy);
        failures += runCheck("UrlEncodedFormEntity construction", App::checkUrlEncodedFormEntity);
        failures += runCheck("DefaultAuthenticationStrategy", App::checkDefaultAuthenticationStrategy);
        failures += runCheck("IdleConnectionEvictor construction", App::checkIdleConnectionEvictor);

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
     * References Cookie.HTTP_ONLY_ATTR — a public constant whose value changed
     * from "httpOnly" (5.4.4) to "httponly" (5.5.2). The symbol signature is
     * identical but the compiled value differs (inlined at compile time).
     */
    static void checkCookieHttpOnlyAttr() {
        final String httpOnlyAttr = Cookie.HTTP_ONLY_ATTR;
        final String secureAttr = Cookie.SECURE_ATTR;
        final String domainAttr = Cookie.DOMAIN_ATTR;
        final String pathAttr = Cookie.PATH_ATTR;
        final String expiresAttr = Cookie.EXPIRES_ATTR;
        final String maxAgeAttr = Cookie.MAX_AGE_ATTR;
        System.out.println("  Cookie.HTTP_ONLY_ATTR = \"" + httpOnlyAttr + "\"");
        System.out.println("  Cookie.SECURE_ATTR = \"" + secureAttr + "\"");
        System.out.println("  Other attrs: " + domainAttr + ", " + pathAttr
                + ", " + expiresAttr + ", " + maxAgeAttr);
    }

    /**
     * References DnsResolver.resolve(String) — the interface method that exists in
     * both versions, but in 5.5.2 a new overload resolve(String, int) was added.
     * Also exercises resolveCanonicalHostname(String).
     */
    static void checkDnsResolverResolve() throws UnknownHostException {
        final InMemoryDnsResolver memResolver = new InMemoryDnsResolver();
        memResolver.add("test.local", InetAddress.getByName("127.0.0.1"));
        final DnsResolver resolver = memResolver;
        final InetAddress[] addresses = resolver.resolve("test.local");
        final String canonical = resolver.resolveCanonicalHostname("test.local");
        System.out.println("  Resolved test.local: " + (addresses != null ? addresses.length : 0) + " addresses");
        System.out.println("  Canonical hostname: " + canonical);
    }

    /**
     * References InMemoryDnsResolver — a public class in a file that changed
     * between versions (whitespace, but still in diff scope).
     */
    static void checkInMemoryDnsResolver() throws UnknownHostException {
        final InMemoryDnsResolver resolver = new InMemoryDnsResolver();
        resolver.add("test.example.com", InetAddress.getByName("127.0.0.1"));
        final InetAddress[] result = resolver.resolve("test.example.com");
        System.out.println("  InMemoryDnsResolver resolved: " + result[0].getHostAddress());
    }

    /**
     * References HttpAuthenticator constructor and updateAuthState() method.
     * In 5.4.4 HttpAuthenticator is a standalone class. In 5.5.2 it extends
     * AuthenticationHandler and is @Deprecated. The public API method
     * updateAuthState() still exists but delegates differently.
     */
    static void checkHttpAuthenticatorUpdateAuthState() {
        final HttpAuthenticator authenticator = new HttpAuthenticator();
        final HttpHost host = new HttpHost("localhost");
        final BasicClassicHttpResponse response = new BasicClassicHttpResponse(401);
        final AuthExchange authExchange = new AuthExchange();
        final BasicHttpContext context = new BasicHttpContext();

        final boolean updated = authenticator.updateAuthState(
                host,
                ChallengeType.TARGET,
                response,
                DefaultAuthenticationStrategy.INSTANCE,
                authExchange,
                context);
        System.out.println("  Auth state updated: " + updated);
    }

    /**
     * References DefaultHttpRequestRetryStrategy constructors.
     * In 5.5.2 the constructor validation changed and retryRequest() gained
     * a new early-exit check comparing retryInterval to responseTimeout.
     */
    static void checkDefaultHttpRequestRetryStrategy() {
        final DefaultHttpRequestRetryStrategy strategy1 = new DefaultHttpRequestRetryStrategy();
        final DefaultHttpRequestRetryStrategy strategy2 =
                new DefaultHttpRequestRetryStrategy(3, TimeValue.ofSeconds(1));
        final boolean retryable1 = strategy1.retryRequest(
                new BasicClassicHttpResponse(503), 1, new BasicHttpContext());
        final boolean retryable2 = strategy2.retryRequest(
                new BasicClassicHttpResponse(503), 1, new BasicHttpContext());
        System.out.println("  Default strategy retryable: " + retryable1);
        System.out.println("  Custom strategy retryable: " + retryable2);
    }

    /**
     * References MultipartEntityBuilder.create(), setBoundary(), setMode(), build().
     * In 5.5.2, boundary generation algorithm changed from ThreadLocalRandom to UUID.
     * Also exercises setCharset() and setMimeSubtype().
     */
    static void checkMultipartEntityBuilder() {
        final MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMimeSubtype("form-data");
        builder.setBoundary("test-boundary");
        builder.addTextBody("field1", "value1");
        final HttpEntity entity = builder.build();
        System.out.println("  MultipartEntity content type: " + entity.getContentType());
    }

    /**
     * References FormBodyPartBuilder.create(String, ContentBody) — the public
     * factory method. In 5.5.2, a new overload create(String, ContentBody, HttpMultipartMode)
     * was added, and the internal constructor signature changed.
     */
    static void checkFormBodyPartBuilder() {
        final FormBodyPartBuilder partBuilder = FormBodyPartBuilder.create(
                "testField",
                new org.apache.hc.client5.http.entity.mime.StringBody(
                        "testValue",
                        org.apache.hc.core5.http.ContentType.TEXT_PLAIN));
        partBuilder.addField("X-Custom", "test");
        System.out.println("  FormBodyPart built: " + partBuilder.build().getName());
    }

    /**
     * References DefaultRedirectStrategy and its isRedirected() method.
     * In 5.5.2, explicit constructors were added and isRedirectAllowed()
     * override was added. INSTANCE constant is used here.
     */
    static void checkDefaultRedirectStrategy() throws Exception {
        final DefaultRedirectStrategy strategy = DefaultRedirectStrategy.INSTANCE;
        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/original");
        final BasicClassicHttpResponse response = new BasicClassicHttpResponse(302);
        response.setHeader("Location", "http://example.com/redirected");
        final BasicHttpContext context = new BasicHttpContext();

        final boolean redirected = strategy.isRedirected(request, response, context);
        System.out.println("  Is redirected (302): " + redirected);
    }

    /**
     * References AuthExchange and its state management methods.
     * The file was modified between versions (comment additions).
     */
    static void checkAuthExchange() {
        final AuthExchange exchange = new AuthExchange();
        final AuthExchange.State state = exchange.getState();
        exchange.reset();
        System.out.println("  AuthExchange initial state: " + state);
        System.out.println("  AuthExchange state after reset: " + exchange.getState());
    }

    /**
     * References AuthScope constructors and matching.
     * The file had whitespace fixes between versions.
     */
    static void checkAuthScope() {
        final AuthScope scope1 = new AuthScope("localhost", 8080);
        final AuthScope scope2 = new AuthScope("localhost", -1);
        final AuthScope scope3 = new AuthScope(null, -1);
        final int match = scope1.match(scope2);
        System.out.println("  AuthScope host: " + scope1.getHost()
                + ", port: " + scope1.getPort()
                + ", scheme: " + scope1.getSchemeName());
        System.out.println("  Scope match result: " + match);
        System.out.println("  Wildcard scope: " + scope3);
    }

    /**
     * References CloseableHttpResponse.adapt() — the public static factory method.
     * In 5.5.2, a new create() factory and CloseableDelegate pattern were added,
     * and the internal field changed from ExecRuntime to CloseableDelegate.
     */
    static void checkCloseableHttpResponse() throws Exception {
        final BasicClassicHttpResponse basicResponse = new BasicClassicHttpResponse(200);
        basicResponse.setHeader("Content-Type", "text/plain");
        final CloseableHttpResponse response = CloseableHttpResponse.adapt(basicResponse);
        System.out.println("  CloseableHttpResponse status: " + response.getCode());
        System.out.println("  Content-Type: " + response.getFirstHeader("Content-Type"));
        response.close();
    }

    /**
     * References HttpRoute constructors and RouteInfo methods.
     * The files had formatting changes between versions.
     */
    static void checkHttpRoute() {
        final HttpHost target = new HttpHost("https", "example.com", 443);
        final HttpHost proxy = new HttpHost("http", "proxy.local", 8080);
        final HttpRoute directRoute = new HttpRoute(target);
        final HttpRoute proxiedRoute = new HttpRoute(target, proxy);
        System.out.println("  Direct route: " + directRoute);
        System.out.println("  Proxied route hops: " + proxiedRoute.getHopCount());
        System.out.println("  Is tunnelled: " + (proxiedRoute.getTunnelType() == RouteInfo.TunnelType.PLAIN));
        System.out.println("  Target host: " + proxiedRoute.getTargetHost());
        System.out.println("  Proxy host: " + proxiedRoute.getProxyHost());
    }

    /**
     * References RequestConfig.Builder — the file had javadoc changes between versions.
     */
    static void checkRequestConfig() {
        final RequestConfig config = RequestConfig.custom()
                .setRedirectsEnabled(true)
                .setCircularRedirectsAllowed(false)
                .setMaxRedirects(10)
                .build();
        System.out.println("  Redirects enabled: " + config.isRedirectsEnabled());
        System.out.println("  Max redirects: " + config.getMaxRedirects());
    }

    /**
     * References ConnectionConfig.Builder — the file had javadoc changes between versions.
     */
    static void checkConnectionConfig() {
        final ConnectionConfig config = ConnectionConfig.custom()
                .setConnectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        System.out.println("  Connect timeout: " + config.getConnectTimeout());
    }

    /**
     * References DefaultConnectionKeepAliveStrategy — the file had formatting
     * changes between versions.
     */
    static void checkKeepAliveStrategy() {
        final DefaultConnectionKeepAliveStrategy strategy =
                DefaultConnectionKeepAliveStrategy.INSTANCE;
        final BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        response.setHeader("Keep-Alive", "timeout=30");
        final TimeValue keepAlive = strategy.getKeepAliveDuration(response, new BasicHttpContext());
        System.out.println("  Keep-alive duration: " + keepAlive);
    }

    /**
     * References UrlEncodedFormEntity constructors — the file had formatting
     * changes between versions.
     */
    static void checkUrlEncodedFormEntity() {
        final UrlEncodedFormEntity entity = new UrlEncodedFormEntity(
                Collections.singletonList(new BasicNameValuePair("key", "value")));
        System.out.println("  Form entity content type: " + entity.getContentType());
        System.out.println("  Form entity content length: " + entity.getContentLength());
    }

    /**
     * References DefaultAuthenticationStrategy.INSTANCE — the file had
     * internal changes between versions.
     */
    static void checkDefaultAuthenticationStrategy() {
        final DefaultAuthenticationStrategy strategy = DefaultAuthenticationStrategy.INSTANCE;
        System.out.println("  DefaultAuthenticationStrategy: " + strategy.getClass().getSimpleName());
    }

    /**
     * References IdleConnectionEvictor constructors — the file had internal
     * changes between versions.
     */
    static void checkIdleConnectionEvictor() {
        System.out.println("  IdleConnectionEvictor class available: "
                + IdleConnectionEvictor.class.getSimpleName());
    }
}
