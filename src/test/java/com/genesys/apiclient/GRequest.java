package com.genesys.apiclient;

import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.FutureResponseListener;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.util.ajax.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.CompletionHandler;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.SECONDS;

public class GRequest {
  private static Logger log = LoggerFactory.getLogger(GRequest.class);

  private final Request jettyRequest;

  public GRequest(Request jettyRequest) {
    this.jettyRequest = jettyRequest;
  }

  // TODO: Offer send with CompletionHandler and blocking Future
  // TODO: Offer send without parsing output, for large responses (like for Provisioning API).
  public Map<String, Object> send(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException, ExecutionException {
    FutureResponseListener listener = new FutureResponseListener(jettyRequest);
    jettyRequest.send(listener);
    ContentResponse response = listener.get(10, SECONDS);
    String responseStr = response.getContentAsString();
    log.error("Subscription response content: " + responseStr);
    return (Map<String, Object>) JSON.parse(responseStr);
  }

  public static class Builder {
    private final Request jettyRequest;

    public Builder(Request jettyRequest) {
      this.jettyRequest = jettyRequest;
    }

    public Builder setJsonContent(Map<String, Object> content) {
      jettyRequest
          .header(HttpHeader.CONTENT_TYPE, MimeTypes.Type.APPLICATION_JSON.asString())
          .accept(MimeTypes.Type.APPLICATION_JSON.asString())
          .content(new StringContentProvider(JSON.toString(content)));

      return this;
    }

    public Builder customize(Consumer<Request> customizerFunction) {
      customizerFunction.accept(jettyRequest);
      return this;
    }

    public GRequest build() {
      return new GRequest(jettyRequest);
    }
  }
}
