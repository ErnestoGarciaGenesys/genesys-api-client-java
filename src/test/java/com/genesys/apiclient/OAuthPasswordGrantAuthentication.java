package com.genesys.apiclient;

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.ajax.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;

public class OAuthPasswordGrantAuthentication {
  private static Logger log = LoggerFactory.getLogger(OAuthPasswordGrantAuthentication.class);

  private final Request tokenRequest;
  private final Consumer<Consumer<Request>> authenticationSetter;

  private String accessToken;

  private OAuthPasswordGrantAuthentication(Request tokenRequest, Consumer<Consumer<Request>> authenticationSetter) {
    this.tokenRequest = tokenRequest;
    this.authenticationSetter = authenticationSetter;
  }

  public void renewToken(long timeout, TimeUnit timeoutUnit) throws InterruptedException, ExecutionException, TimeoutException, InvalidResponseException, RequestFailedException, ResponseFailedException {
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

    Result tokenResult = tokenResultFuture.get(timeout, timeoutUnit);
    log.debug("Result: " + tokenResult);

    // This is using Jetty Client JSON parser
    Map tokenResponse = null;
    try {
      tokenResponse = (Map) JSON.parse(new InputStreamReader(tokenListener.getContentAsInputStream(), UTF_8));
    } catch (IOException e) {
      throw new InvalidResponseException(e);
    }

    log.debug("Response: " + tokenResponse);

    if (tokenResult.getRequestFailure() != null)
      throw new RequestFailedException(tokenResult.getRequestFailure());

    if (tokenResult.getResponseFailure() != null)
      throw new ResponseFailedException(tokenResult.getResponseFailure());

    // TODO: use refresh_token when access_token expires. This can be done by:
    // - scheduling refresh before expiration
    // - refreshing on failed token
    this.accessToken = (String) tokenResponse.get("access_token");

    authenticationSetter.accept(this::applyToRequest);
  }

  private void applyToRequest(Request request) {
    request.header(HttpHeader.AUTHORIZATION, "Bearer " + accessToken);
  }

  public static class Builder {
    private final Request tokenRequest;
    private final Consumer<Consumer<Request>> authenticationSetter;

    private String clientId;
    private String clientSecret;
    private String tenant;
    private String userName;
    private String userPassword;
    private long renewTokenTimeout;
    private TimeUnit renewTokenTimeoutUnit;

    Builder(Request tokenRequest, Consumer<Consumer<Request>> authenticationSetter) {
      this.tokenRequest = tokenRequest;
      this.authenticationSetter = authenticationSetter;
    }

    public Builder setClientCredentials(String clientId, String clientSecret) {
      this.clientId = clientId;
      this.clientSecret = clientSecret;
      return this;
    }

    public Builder setUserCredentials(String tenant, String userName, String userPassword) {
      this.tenant = tenant;
      this.userName = userName;
      this.userPassword =  userPassword;
      return this;
    }

    public Builder setRenewTokenTimeout(long timeout, TimeUnit unit) {
      this.renewTokenTimeout = timeout;
      this.renewTokenTimeoutUnit = unit;
      return this;
    }

    public OAuthPasswordGrantAuthentication build() {
      tokenRequest.header(
          HttpHeader.AUTHORIZATION,
          "Basic " + B64Code.encode(
              clientId + ":" + clientSecret,
              StandardCharsets.ISO_8859_1));

      tokenRequest.accept(MimeTypes.Type.APPLICATION_JSON.toString());

      Fields fields = new Fields();
      fields.put("grant_type", "password");
      fields.put("client_id", clientId);
      fields.put("username", tenant == null? userName : tenant + '\\' + userName);
      fields.put("password", userPassword);
      log.debug("Fields are: " + fields);
      tokenRequest.content(new FormContentProvider(fields));

      tokenRequest.timeout(renewTokenTimeout, renewTokenTimeoutUnit);

      return new OAuthPasswordGrantAuthentication(tokenRequest, authenticationSetter);
    }
  }
}
