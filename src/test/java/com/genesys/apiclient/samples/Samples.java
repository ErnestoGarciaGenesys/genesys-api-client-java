package com.genesys.apiclient.samples;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.client.util.FutureResponseListener;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.ajax.JSON;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.genesys.apiclient.samples.Environment.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

class Samples {
  private static final Logger log = LogManager.getLogger();

  static final String PROVISIONING_API_PATH = "/provisioning/v3";

  @Test
  void subscribe_to_Statistics_API() throws Throwable {

    // Uncomment to get network traces
//    System.setProperty("javax.net.debug","all");

    //GenesysApiContext apiContext = GenesysApiContext.builder()
    //    .setApiKey(API_KEY)
    //    .setApiBaseUrl(API_BASE_URL)
    //    .build(); // Can this also be just a request modifier?


    SslContextFactory.Client sslContextFactory = new SslContextFactory.Client(); // Needed to accept https
    HttpClient httpClient = new HttpClient(sslContextFactory);
    httpClient.start();

    String tokenUrl = API_BASE_URL + "/auth/v3" + "/oauth/token";
    Request tokenRequest = httpClient.POST(tokenUrl);

    tokenRequest.header("x-api-key", API_KEY);
    tokenRequest.header(HttpHeader.AUTHORIZATION, "Basic " + B64Code.encode(CLIENT_ID + ":" + CLIENT_SECRET, StandardCharsets.ISO_8859_1));

    // alternative:
//    BasicAuthentication.BasicResult basicAuth = new BasicAuthentication.BasicResult(new URI(tokenUrl), CLIENT_ID, CLIENT_SECRET);
//    basicAuth.apply(tokenRequest);

    tokenRequest.accept(MimeTypes.Type.APPLICATION_JSON.toString());

    Fields fields = new Fields();
    fields.put("grant_type", "password");
    fields.put("client_id", CLIENT_ID);
    fields.put("username", TENANT + "\\" + USER_NAME);
    fields.put("password", PASSWORD);
    log.debug("Fields are: " + fields);
    tokenRequest.content(new FormContentProvider(fields));

    CompletableFuture<Result> tokenResultFuture = new CompletableFuture<>();

    // More alternatives for efficiently handling the response in:
    // https://www.eclipse.org/jetty/documentation/current/http-client-api.html
    BufferingResponseListener tokenListener = new BufferingResponseListener() {
      @Override
      public void onComplete(Result result) {
        tokenResultFuture.complete(result);
      }
    };

    tokenRequest.send(tokenListener);

    Result tokenResult = tokenResultFuture.get(10, SECONDS);
    System.out.println(tokenResult);

    if (tokenResult.isFailed())
      throw tokenResult.getFailure();

    // This is using Jetty Client JSON parser
    Map tokenResponse = (Map)JSON.parse(new InputStreamReader(tokenListener.getContentAsInputStream(), UTF_8));
    log.debug("Response: " + tokenResponse);

    // TODO: use refresh_token when access_token expires. This can be done by:
    // - scheduling refresh before expiration
    // - refreshing on failed token
    String accessToken = (String) tokenResponse.get("access_token");

    Random random = new Random();
    String id = String.format("%d03", random.nextInt(1000));

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

    Request subscribeRequest = httpClient.POST(API_BASE_URL + "/statistics/v3/subscriptions");
    subscribeRequest.header("x-api-key", API_KEY);
    subscribeRequest.header(HttpHeader.AUTHORIZATION, "Bearer " + accessToken);
    subscribeRequest.header(HttpHeader.CONTENT_TYPE, MimeTypes.Type.APPLICATION_JSON.asString());
    subscribeRequest.accept(MimeTypes.Type.APPLICATION_JSON.asString());

    String subscriptionStr = JSON.toString(subscription);
    log.debug("Subscription message: " + subscriptionStr);
    subscribeRequest.content(new StringContentProvider(subscriptionStr));

    FutureResponseListener subscribeListener = new FutureResponseListener(subscribeRequest);
    subscribeRequest.send(subscribeListener);
    ContentResponse subscribeContentResponse = subscribeListener.get(10, SECONDS);
    Map subscribeResponse = (Map)JSON.parse(subscribeContentResponse.getContentAsString());
    log.debug("Subscribe response: " + subscribeResponse);

    // GenesysAuthContext could be more general than just the current OAuth context.
    // The context, therefore, is just a transformer of requests! It attaches proper
    // authn content (like an authn header) to requests.
//    GenesysAuthContext authContext = GenesysOAuthAuthCodeGrantContext().builder()
//        .setApiContext(apiContext) // or apiContext.createOAuthAuthCodeGrantContext?
//        .setClientCredentials(CLIENT_ID, CLIENT_SECRET)
//        .setUserCredentials(USER_NAME, PASSWORD)
//        .build(); // This will take time. Needs to be async.
//
//    GenesysRequest getQueuesRequest = GenesysRequest.builder()
//        .setApiContext(apiContext) // isn't this implicit in AuthContext?
//        .setAuthContext(...) // "Bearer " + token
//				.setHttpMethod("POST")
//        .setUrl("/objects/skills")
//        .build();

    // GenesysRequest should encapsulate:
    // - How to send typical request parameters -> can be dependent on specific API
    // - How to read typical tokenResponse parameters -> can be dependent on specific API
    // - How to process errors -> can be dependent on specific API
    // - The JSON tokenResponse type may depend on the JSON library
//    JsonResponse/*?*/ tokenResponse = getQueuesRequest.execute(); // Should have async counterpart

  }
}
