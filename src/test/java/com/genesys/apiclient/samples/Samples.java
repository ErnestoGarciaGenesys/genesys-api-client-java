package com.genesys.apiclient.samples;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Scanner;

import static com.genesys.apiclient.samples.Environment.*;

public class Samples {
  private static final Logger log = LogManager.getLogger();

  static final String PROVISIONING_API_PATH = "/provisioning/v3";

  @Test
  public void subscribe_to_Statistics_API() throws Exception {
    //GenesysApiContext apiContext = GenesysApiContext.builder()
    //    .setApiKey(API_KEY)
    //    .setApiBaseUrl(API_BASE_URL)
    //    .build(); // Can this also be just a request modifier?


    SslContextFactory.Client sslContextFactory = new SslContextFactory.Client(); // Needed to accept https
    HttpClient eclipseHttpClient = new HttpClient(sslContextFactory);
    eclipseHttpClient.start();

    String tokenUrl = API_BASE_URL + "/auth/v3" + "/oauth/token";
    Request tokenRequest = eclipseHttpClient.POST(tokenUrl);

    tokenRequest.header("x-api-key", API_KEY);
    BasicAuthentication.BasicResult basicAuth = new BasicAuthentication.BasicResult(new URI(tokenUrl), CLIENT_ID, CLIENT_SECRET);
    basicAuth.apply(tokenRequest);

    tokenRequest.accept(MimeTypes.Type.APPLICATION_JSON.toString());

    Fields fields = new Fields();
    fields.put("grant_type", "password");
    fields.put("client_id", CLIENT_ID);
    fields.put("username", TENANT + "\\" + USER_NAME);
    fields.put("password", PASSWORD);
    log.debug("Fields are: " + fields);
    tokenRequest.content(new FormContentProvider(fields));

    // More alternatives for efficiently handling the response in:
    // https://www.eclipse.org/jetty/documentation/current/http-client-api.html
    tokenRequest.send(new BufferingResponseListener() {
      @Override
      public void onComplete(Result result) {
        System.out.println(result);
        if (result.isSucceeded()) {
          log.debug("Response: " + getContentAsString());
        } else {
          throw new RuntimeException(result.getFailure());
        }
      }
    });

    new Scanner(System.in).nextLine();

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
    // - How to read typical response parameters -> can be dependent on specific API
    // - How to process errors -> can be dependent on specific API
    // - The JSON response type may depend on the JSON library
//    JsonResponse/*?*/ response = getQueuesRequest.execute(); // Should have async counterpart

  }
}
