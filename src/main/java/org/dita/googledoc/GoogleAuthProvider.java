package org.dita.googledoc;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
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
import java.util.concurrent.TimeUnit;

/**
 * Authenticates with Google APIs using a cascading strategy:
 *
 * 1. Explicit credentials file (service account or OAuth client JSON)
 * 2. gcloud CLI token (non-interactive)
 * 3. Application Default Credentials (ADC)
 * 4. Interactive gcloud login (opens browser)
 */
public final class GoogleAuthProvider {

    private static final List<String> SCOPES = List.of(
        "https://www.googleapis.com/auth/documents",
        "https://www.googleapis.com/auth/drive"
    );

    private static final int PROCESS_TIMEOUT_SECONDS = 30;

    private GoogleAuthProvider() {}

    public static HttpRequestInitializer getCredential(
            String credentialsPath, String authType, String tokenDir)
            throws IOException, GeneralSecurityException {

        if (credentialsPath != null && !credentialsPath.isEmpty()) {
            String effectiveAuthType = (authType == null || authType.isEmpty() || "auto".equals(authType))
                ? "service" : authType;
            return switch (effectiveAuthType) {
                case "service" -> serviceAccountCredential(credentialsPath);
                case "oauth" -> oauthCredential(credentialsPath, tokenDir);
                default -> throw new IllegalArgumentException(
                    "Invalid auth type: " + effectiveAuthType + ". Must be 'auto', 'service', or 'oauth'.");
            };
        }

        if (authType != null && !"auto".equals(authType) && !"".equals(authType)) {
            throw new IllegalArgumentException(
                "Auth type '" + authType + "' requires googledoc.credentials to be set.");
        }

        return autoCredential();
    }

    private static HttpRequestInitializer autoCredential() throws IOException {
        boolean gcloudAvailable = hasGcloud();

        if (gcloudAvailable) {
            String token = tryGcloudToken();
            if (token != null) {
                System.out.println("Authenticated via gcloud CLI.");
                return tokenCredential(token);
            }
        }

        HttpRequestInitializer adc = tryAdcCredential();
        if (adc != null) {
            System.out.println("Authenticated via Application Default Credentials.");
            return adc;
        }

        if (gcloudAvailable) {
            System.out.println("No active credentials found. Authenticating with Google...");
            String token = gcloudInteractiveLogin();
            if (token != null) {
                return tokenCredential(token);
            }
        }

        throw new IOException(
            "No authentication method available.\n" +
            "  Option 1: Install gcloud CLI and run 'gcloud auth login --enable-gdrive-access'\n" +
            "  Option 2: Set GOOGLE_APPLICATION_CREDENTIALS env var to a service account JSON\n" +
            "  Option 3: Pass --googledoc.credentials=/path/to/credentials.json");
    }

    static boolean hasGcloud() {
        try {
            ProcessBuilder pb = new ProcessBuilder("gcloud", "version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    static String tryGcloudToken() {
        try {
            ProcessBuilder pb = new ProcessBuilder("gcloud", "auth", "print-access-token");
            pb.redirectErrorStream(false);
            Process process = pb.start();
            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) return null;

            String token = new String(process.getInputStream().readAllBytes()).trim();
            return token.isEmpty() ? null : token;
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    static HttpRequestInitializer tryAdcCredential() {
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped(SCOPES);
            credentials.refreshIfExpired();
            return new HttpCredentialsAdapter(credentials);
        } catch (Exception e) {
            return null;
        }
    }

    private static String gcloudInteractiveLogin() {
        try {
            ProcessBuilder loginPb = new ProcessBuilder(
                "gcloud", "auth", "login", "--enable-gdrive-access");
            loginPb.inheritIO();
            Process loginProcess = loginPb.start();
            int exitCode = loginProcess.waitFor();
            if (exitCode != 0) {
                System.err.println("Authentication was cancelled or failed.");
                return null;
            }
            return tryGcloudToken();
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to run gcloud auth login: " + e.getMessage());
            return null;
        }
    }

    private static HttpRequestInitializer tokenCredential(String accessToken) {
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(accessToken, null));
        return new HttpCredentialsAdapter(credentials);
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
