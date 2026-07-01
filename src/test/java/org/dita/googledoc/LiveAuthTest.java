package org.dita.googledoc;

import com.google.api.client.http.HttpRequestInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

class LiveAuthTest {

    static boolean gcloudAvailable() {
        return GoogleAuthProvider.hasGcloud();
    }

    @Test
    @EnabledIf("gcloudAvailable")
    void autoAuth_withGcloud_succeeds() throws Exception {
        HttpRequestInitializer credential =
            GoogleAuthProvider.getCredential(null, "auto", null);
        assertNotNull(credential, "Auto-auth should return a credential when gcloud is active");
        System.out.println("AUTO-AUTH SUCCESS: got credential via gcloud cascade");
    }

    @Test
    @EnabledIf("gcloudAvailable")
    void autoAuth_createsDocument() throws Exception {
        HttpRequestInitializer credential =
            GoogleAuthProvider.getCredential(null, "auto", null);
        GoogleDocApiClient client = new GoogleDocApiClient(credential);
        String docId = client.createDocument("DITA Plugin Auth Test (delete me)", null);
        assertNotNull(docId);
        assertFalse(docId.isEmpty());
        System.out.println("LIVE TEST: Created doc https://docs.google.com/document/d/" + docId + "/edit");
    }
}
