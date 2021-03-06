/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gcloud;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.compute.ComputeCredential;
import com.google.api.client.googleapis.extensions.appengine.auth.oauth2.AppIdentityCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.Set;

/**
 * Credentials for accessing Google Cloud services.
 */
public abstract class AuthCredentials {

  private static class AppEngineAuthCredentials extends AuthCredentials {

    @Override
    protected HttpRequestInitializer httpRequestInitializer(
        HttpTransport transport, Set<String> scopes) {
      return new AppIdentityCredential(scopes);
    }
  }

  private static class ServiceAccountAuthCredentials extends AuthCredentials {

    private final String account;
    private final PrivateKey privateKey;

    ServiceAccountAuthCredentials(String account, PrivateKey privateKey) {
      this.account = checkNotNull(account);
      this.privateKey = checkNotNull(privateKey);
    }

    ServiceAccountAuthCredentials() {
      account = null;
      privateKey = null;
    }

    @Override
    protected HttpRequestInitializer httpRequestInitializer(
        HttpTransport transport, Set<String> scopes) {
      GoogleCredential.Builder builder = new GoogleCredential.Builder()
          .setTransport(transport)
          .setJsonFactory(new JacksonFactory());
      if (privateKey != null) {
        builder.setServiceAccountPrivateKey(privateKey);
        builder.setServiceAccountId(account);
        builder.setServiceAccountScopes(scopes);
      }
      return builder.build();
    }
  }

  protected abstract HttpRequestInitializer httpRequestInitializer(HttpTransport transport,
      Set<String> scopes);

  public static AuthCredentials createForAppEngine() {
    return new AppEngineAuthCredentials();
  }

  public static AuthCredentials createForComputeEngine()
      throws IOException, GeneralSecurityException {
    final ComputeCredential cred = getComputeCredential();
    return new AuthCredentials() {
      @Override
      protected HttpRequestInitializer httpRequestInitializer(HttpTransport transport,
          Set<String> scopes) {
        return cred;
      }
    };
  }

  /**
   * Returns the Application Default Credentials.
   *
   * <p>Returns the Application Default Credentials which are credentials that identify and
   * authorize the whole application. This is the built-in service account if running on Google
   * Compute Engine or the credentials file from the path in the environment variable
   * GOOGLE_APPLICATION_CREDENTIALS.</p>
   *
   * @return the credentials instance.
   * @throws IOException if the credentials cannot be created in the current environment.
   */
  public static AuthCredentials createApplicationDefaults() throws IOException {
    final GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
    return new AuthCredentials() {
      @Override
      protected HttpRequestInitializer httpRequestInitializer(HttpTransport transport,
          Set<String> scopes) {
        return new HttpCredentialsAdapter(credentials);
      }
    };
  }

  public static AuthCredentials createFor(String account, PrivateKey privateKey) {
    return new ServiceAccountAuthCredentials(account, privateKey);
  }

  public static AuthCredentials noCredentials() {
    return new ServiceAccountAuthCredentials();
  }

  static ComputeCredential getComputeCredential() throws IOException, GeneralSecurityException {
    NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
    // Try to connect using Google Compute Engine service account credentials.
    ComputeCredential credential = new ComputeCredential(transport, new JacksonFactory());
    // Force token refresh to detect if we are running on Google Compute Engine.
    credential.refreshToken();
    return credential;
  }
}
