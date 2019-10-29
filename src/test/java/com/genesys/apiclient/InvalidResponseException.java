package com.genesys.apiclient;

public class InvalidResponseException extends RuntimeException {
  public InvalidResponseException(Exception cause) {
    super(cause);
  }
}
