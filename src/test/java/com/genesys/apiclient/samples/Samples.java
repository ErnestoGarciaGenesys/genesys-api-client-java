package com.genesys.apiclient.samples;

import com.genesys.apiclient.GApiClient;
import com.genesys.apiclient.GRequest;
import com.genesys.apiclient.OAuthPasswordGrantAuthentication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cometd.client.BayeuxClient;
import org.cometd.client.transport.LongPollingTransport;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.util.HttpCookieStore;
import org.eclipse.jetty.util.ajax.JSON;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.Test;

import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static com.genesys.apiclient.samples.Environment.*;
import static java.util.concurrent.TimeUnit.SECONDS;

class Samples {
  private static final Logger log = LogManager.getLogger();

  static final String PROVISIONING_API_PATH = "/provisioning/v3";

  static {
    // Uncomment to get network traces
//    System.setProperty("javax.net.debug","all");
  }

  static class TracingCookieStore extends HttpCookieStore {
    private final String name;

    public TracingCookieStore(String name) {
      this.name = name;
    }

    @Override
    public void add(URI uri, HttpCookie cookie) {
      super.add(uri, cookie);
      log.fatal("Added cookie to " + name + ": " + cookieToString(cookie));
      log.fatal(cookieStoreToString(name, this));
    }

    @Override
    public boolean removeAll() {
      log.fatal("All cookies removed from " + name);
      return super.removeAll();
    }
  }


  @Test
  void subscribe_to_Statistics_API() throws Throwable {

    SslContextFactory.Client sslContextFactory = new SslContextFactory.Client(); // Needed to accept https
    HttpClient httpClient = new HttpClient(sslContextFactory);

    final HttpCookieStore httpClientCookieStore = new TracingCookieStore("httpClient");
    httpClient.setCookieStore(httpClientCookieStore);

    httpClient.start();

    GApiClient apiClient = new GApiClient.Builder(httpClient)
        .setBaseUrl(API_BASE_URL)
        .setApiKey(API_KEY)
        .build();

    OAuthPasswordGrantAuthentication auth = apiClient.buildOAuthPasswordGrantAuthentication()
        .setClientCredentials(CLIENT_ID, CLIENT_SECRET)
        .setUserCredentials(TENANT, USER_NAME, USER_PASSWORD)
        .setRenewTokenTimeout(10, SECONDS)
        .build();

    auth.renewToken(10, SECONDS);

//    ContentResponse dummyResponse = httpClient.POST(API_BASE_URL + "/statistics/v3/notifications")
//        .header("x-api-key", API_KEY)
//        .send();

//    HttpCookie statisticsSessionIdCookie = httpClientCookieStore.getCookies().stream()
//        .filter(cookie -> "STATISTICS_SESSIONID".equalsIgnoreCase(cookie.getName()))
//        .findFirst()
//        .get();

    // Transports are set the cookieProvider *after* creating the bayeuxClient, because
    // BayeuxClient invariably sets its own cookieProvider inside the constructor.
    BayeuxClient bayeuxClient;

    //    if (builder.webSocketEnabled) {
//      WebSocketTransport webSocketTransport = createWebSocketTransport();
//      bayeuxClient = new BayeuxClient(builder.client.serverUri + "/api/v2/notifications", webSocketTransport, longPollingTransport);
//      webSocketTransport.setCookieProvider(builder.cookieSession.getCookieProvider());
//    } else {
    SslContextFactory.Client sslContextFactoryComet = new SslContextFactory.Client(); // Needed to accept https
    HttpClient httpClientComet = new HttpClient(sslContextFactoryComet);
    httpClientComet.start();

    LongPollingTransport longPollingTransport = new LongPollingTransport(null, httpClientComet) {
      @Override
      protected void customize(Request request) {
        super.customize(request);
        apiClient.applyToRequest(request);
        log.fatal("For request " + request.getURI() + ": " + cookieStoreToString("long-polling", getCookieStore()));
      }
    };

    bayeuxClient = new BayeuxClient(
        API_BASE_URL + "/statistics/v3/notifications",
        longPollingTransport);
//    }

    TracingCookieStore longPollingCookieStore = new TracingCookieStore("long-polling");
    longPollingTransport.setCookieStore(longPollingCookieStore);

    for (HttpCookie c : httpClientCookieStore.getCookies())
      longPollingCookieStore.add(new URI("https://gapi-use1.genesyscloud.com/statistics/v3/notifications"), c);

//    bayeuxClient.putCookie(statisticsSessionIdCookie);
//    longPollingCookieStore.add(new URI("https://gapi-use1.genesyscloud.com/statistics/v3/notifications"), statisticsSessionIdCookie);

    bayeuxClient.handshake(5000);

//    subscribe(bayeuxClient, "/**");
    subscribe(bayeuxClient, "/statistics/v3/service");
    subscribe(bayeuxClient, "/statistics/v3/updates");

//    HttpCookie statisticsSessionIdCookieFromBayeux = longPollingCookieStore.getCookies().stream()
//        .filter(cookie -> "STATISTICS_SESSIONID".equalsIgnoreCase(cookie.getName()))
//        .findFirst()
//        .get();
//
//    httpClientCookieStore.add(
//        new URI("https://gapi-use1.genesyscloud.com/statistics/v3/notifications"),
//        statisticsSessionIdCookieFromBayeux);

//    for (HttpCookie c : longPollingCookieStore.getCookies())
//      httpClientCookieStore.add(new URI("https://gapi-use1.genesyscloud.com/statistics/v3/notifications"), c);

    Random random = new Random();
    String id = String.format("%d03", random.nextInt(1000));

    Map<String, Object> subscription = createSubscriptionImmediateAgentState(id);

    GRequest subscribeRequest = apiClient.buildRequest("POST", "/statistics/v3/subscriptions")
        .setJsonContent(subscription)
        .build();

    log.fatal("Subscription request content: " + JSON.toString(subscription));

    subscribeRequest.send(10, SECONDS);

    Thread.sleep(30000);
  }

  private void subscribe(BayeuxClient bayeuxClient, String channelName) throws InterruptedException {
    CountDownLatch subscribed = new CountDownLatch(1);

    bayeuxClient.getChannel(channelName).subscribe(
        (channel, message) -> {
          log.fatal("Received event: Channel {} received message: {}", channel, message);
        },
        (channel, message) -> {
          log.fatal("Received event: Subscribed to channel {} by message: {}", channel, message);
          subscribed.countDown();
        });

    subscribed.await(5, SECONDS);
  }

  private Map<String, Object> createSubscription1(String id) {
    Map<String, Object> stat1Def = new LinkedHashMap<>();
    stat1Def.put("notificationMode", "Periodical");
    stat1Def.put("notificationFrequency", 10);
    stat1Def.put("category", "CurrentTime");
    stat1Def.put("subject", "DNStatus");
    stat1Def.put("mainMask", "*");

    Map<String, Object> stat1 = new LinkedHashMap<>();
    stat1.put("statisticId", "stat_" + id + "_0");
    stat1.put("objectId", "ernesto.garcia@genesys.com");
    stat1.put("objectType", "Agent");
    stat1.put("definition", stat1Def);

    List<Map<String, Object>> statistics = new ArrayList<>();
    statistics.add(stat1);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("statistics", statistics);

    Map<String, Object> subscription = new LinkedHashMap<>();
    subscription.put("operationId", "subscription_" + id);
    subscription.put("data", data);
    return subscription;
  }

  private Map<String, Object> createSubscription2(String id) {
    Map<String, Object> stat1Def = new LinkedHashMap<>();
    stat1Def.put("notificationMode", "Periodical");
    stat1Def.put("notificationFrequency", 5);
    stat1Def.put("insensitivity", 1);
    stat1Def.put("category", "CurrentTime");
    stat1Def.put("subject", "DNStatus");
    stat1Def.put("mainMask", "*");

    Map<String, Object> stat1 = new LinkedHashMap<>();
    stat1.put("statisticId", "stat_" + id + "_0");
    stat1.put("objectId", "jim.crespino@genesys.com");
    stat1.put("objectType", "Agent");
    stat1.put("definition", stat1Def);

    List<Map<String, Object>> statistics = new ArrayList<>();
    statistics.add(stat1);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("statistics", statistics);

    Map<String, Object> subscription = new LinkedHashMap<>();
    subscription.put("operationId", "subscription_" + id);
    subscription.put("data", data);
    return subscription;
  }

  private Map<String, Object> createSubscriptionImmediateAgentState(String id) {
    Map<String, Object> statDef = new LinkedHashMap<>();
    statDef.put("notificationMode", "Immediate");
    statDef.put("category", "CurrentState");
    statDef.put("subject", "AgentStatus");
    statDef.put("mainMask", "*");
    statDef.put("objects", "Agent"); //???

    Map<String, Object> stat = new LinkedHashMap<>();
    stat.put("statisticId", "stat_" + id + "_0");
    stat.put("objectId", "ernesto.garcia@genesys.com");
    stat.put("objectType", "Agent");
    stat.put("definition", statDef);

    List<Map<String, Object>> statistics = new ArrayList<>();
    statistics.add(stat);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("statistics", statistics);

    Map<String, Object> subscription = new LinkedHashMap<>();
    subscription.put("operationId", "subscription_" + id);
    subscription.put("data", data);
    return subscription;
  }

  static String cookieToString(HttpCookie c) {
    return c + ", domain=" + c.getDomain() + ", path=" + c.getPath();
  }

  static String cookieStoreToString(String name, CookieStore store) {
    StringBuilder res = new StringBuilder();

    res.append("Cookie store " + name + ": " + store + "\n");

    for (HttpCookie c : store.getCookies()) {
      res.append("\t");
      res.append(cookieToString(c));
      res.append("\n");
    }

    return res.toString();
  }
}
