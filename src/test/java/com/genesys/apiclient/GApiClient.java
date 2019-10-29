package com.genesys.apiclient;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;

import java.util.function.Consumer;

public class GApiClient {
  private final String baseUrl;
  private final String apiKey;

  private final HttpClient jettyHttpClient;
  private volatile Consumer<Request> authentication;

  private GApiClient(
      HttpClient jettyHttpClient,
      String baseUrl,
      String apiKey) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;

    this.jettyHttpClient = jettyHttpClient;
    this.authentication = request -> {};
  }

  private void setAuthentication(Consumer<Request> authentication) {
    this.authentication = authentication;
  }

  public OAuthPasswordGrantAuthentication.Builder buildOAuthPasswordGrantAuthentication() {
    return new OAuthPasswordGrantAuthentication.Builder(
        jettyHttpClient.POST(baseUrl + "/auth/v3/oauth/token")
            .header("x-api-key", apiKey),
        this::setAuthentication);
  }

  public GRequest.Builder buildRequest(String httpMethod, String path) {
    Request jettyRequest = jettyHttpClient.newRequest(baseUrl + path)
        .method(httpMethod);
    applyToRequest(jettyRequest);
    return new GRequest.Builder(jettyRequest);
  }

  // TODO: this should be removed at some point.
  public void applyToRequest(Request jettyRequest) {
    jettyRequest.header("x-api-key", apiKey);
    authentication.accept(jettyRequest);
  }

  public static class Builder {
    private final HttpClient jettyHttpClient;
    private String baseUrl;
    private String apiKey;

    public Builder(HttpClient jettyHttpClient) {
      this.jettyHttpClient = jettyHttpClient;
    }

    public Builder setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    public Builder setApiKey(String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    public GApiClient build() {
      assert apiKey != null;
      return new GApiClient(
          jettyHttpClient,
          baseUrl,
          apiKey);
    }
  }
}
