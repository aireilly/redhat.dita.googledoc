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
    void getCredential_invalidAuthType_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            GoogleAuthProvider.getCredential("/nonexistent", "invalid", null));
    }

    @Test
    void getCredential_missingFile_throws() {
        assertThrows(IOException.class, () ->
            GoogleAuthProvider.getCredential("/nonexistent/creds.json", "service", null));
    }

    @Test
    void getCredential_serviceAccount_badJson_throws(@TempDir Path tempDir) throws Exception {
        Path creds = tempDir.resolve("service-account.json");
        // Write invalid JSON to verify error handling
        Files.writeString(creds, "not valid json");

        assertThrows(IOException.class, () ->
            GoogleAuthProvider.getCredential(creds.toString(), "service", null));
    }

    @Test
    void getCredential_serviceAccount_structureOnly(@TempDir Path tempDir) throws Exception {
        Path creds = tempDir.resolve("service-account.json");
        // Write minimal valid JSON structure (will fail at credential creation, not parsing)
        // This verifies the method routes to the service account path correctly
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

        // This should throw during credential creation, not file reading
        // confirming the method correctly routes to service account flow
        assertThrows(IOException.class, () ->
            GoogleAuthProvider.getCredential(creds.toString(), "service", null));
    }
}
