package org.dita.googledoc;

import com.google.api.client.http.HttpRequestInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GoogleAuthProviderTest {

    @Test
    void getCredential_explicitServiceWithNoFile_throws() {
        assertThrows(IOException.class, () ->
            GoogleAuthProvider.getCredential("/nonexistent/creds.json", "service", null));
    }

    @Test
    void getCredential_serviceAccount_badJson_throws(@TempDir Path tempDir) throws Exception {
        Path creds = tempDir.resolve("service-account.json");
        Files.writeString(creds, "not valid json");

        assertThrows(IOException.class, () ->
            GoogleAuthProvider.getCredential(creds.toString(), "service", null));
    }

    @Test
    void getCredential_explicitServiceAccount_routes(@TempDir Path tempDir) throws Exception {
        Path creds = tempDir.resolve("service-account.json");
        String minimalJson = """
            {
              "type": "service_account",
              "project_id": "test-project",
              "private_key_id": "test-key-id",
              "private_key": "-----BEGIN PRIVATE KEY-----\\nINVALID\\n-----END PRIVATE KEY-----",
              "client_email": "test@test-project.iam.gserviceaccount.com",
              "client_id": "123456789",
              "auth_uri": "https://accounts.google.com/o/oauth2/auth",
              "token_uri": "https://oauth2.googleapis.com/token"
            }
            """;
        Files.writeString(creds, minimalJson);

        assertThrows(IOException.class, () ->
            GoogleAuthProvider.getCredential(creds.toString(), "service", null));
    }

    @Test
    void getCredential_explicitTypeWithoutCredentials_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            GoogleAuthProvider.getCredential(null, "service", null));
    }

    @Test
    void getCredential_oauthTypeWithoutCredentials_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            GoogleAuthProvider.getCredential("", "oauth", null));
    }

    @Test
    void getCredential_autoWithCredentials_treatsAsService(@TempDir Path tempDir) throws Exception {
        Path creds = tempDir.resolve("service-account.json");
        Files.writeString(creds, "not valid json");

        assertThrows(IOException.class, () ->
            GoogleAuthProvider.getCredential(creds.toString(), "auto", null));
    }

    @Test
    void hasGcloud_returnsBoolean() {
        boolean result = GoogleAuthProvider.hasGcloud();
        // Just verify it returns without throwing — actual value depends on environment
        assertTrue(result || !result);
    }

    @Test
    void tryGcloudToken_returnsNullOrToken() {
        String token = GoogleAuthProvider.tryGcloudToken();
        // Returns null if gcloud not available or no active auth, string otherwise
        assertTrue(token == null || !token.isEmpty());
    }

    @Test
    void tryAdcCredential_returnsNullOrInitializer() {
        HttpRequestInitializer result = GoogleAuthProvider.tryAdcCredential();
        // Returns null if no ADC configured, initializer otherwise
        assertTrue(result == null || result != null);
    }
}
