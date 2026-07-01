package org.dita.googledoc;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.util.store.FileDataStoreFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

public final class GoogleAuthProvider {

    private static final List<String> SCOPES = List.of(
        "https://www.googleapis.com/auth/documents",
        "https://www.googleapis.com/auth/drive"
    );

    private GoogleAuthProvider() {}

    public static HttpRequestInitializer getCredential(
            String credentialsPath, String authType, String tokenDir)
            throws IOException, GeneralSecurityException {
        return switch (authType) {
            case "service" -> serviceAccountCredential(credentialsPath);
            case "oauth" -> oauthCredential(credentialsPath, tokenDir);
            default -> throw new IllegalArgumentException(
                "Invalid auth type: " + authType + ". Must be 'service' or 'oauth'.");
        };
    }

    private static HttpRequestInitializer serviceAccountCredential(String credentialsPath)
            throws IOException {
        try (FileInputStream stream = new FileInputStream(credentialsPath)) {
            GoogleCredentials credentials = ServiceAccountCredentials
                .fromStream(stream)
                .createScoped(SCOPES);
            return new HttpCredentialsAdapter(credentials);
        }
    }

    private static HttpRequestInitializer oauthCredential(
            String credentialsPath, String tokenDir)
            throws IOException, GeneralSecurityException {
        String effectiveTokenDir = (tokenDir != null && !tokenDir.isEmpty())
            ? tokenDir
            : Path.of(System.getProperty("user.home"), ".dita-googledoc", "tokens").toString();

        GoogleClientSecrets clientSecrets;
        try (var reader = new InputStreamReader(new FileInputStream(credentialsPath))) {
            clientSecrets = GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), reader);
        }

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                clientSecrets,
                SCOPES)
            .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(effectiveTokenDir)))
            .setAccessType("offline")
            .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
            .setPort(8888)
            .build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver)
            .authorize("user");
        return credential;
    }
}
