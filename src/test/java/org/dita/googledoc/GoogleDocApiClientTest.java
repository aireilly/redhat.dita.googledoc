package org.dita.googledoc;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.drive.Drive;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GoogleDocApiClientTest {

    @Test
    void constructor_createsServicesFromCredential() {
        HttpRequestInitializer mockInit = request -> {};

        GoogleDocApiClient client = new GoogleDocApiClient(mockInit);

        assertNotNull(client.getDocsService());
        assertNotNull(client.getDriveService());
    }

    @Test
    void chunkRequests_splitsAtLimit() {
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < 1200; i++) {
            requests.add(new Request());
        }

        List<List<Request>> chunks = GoogleDocApiClient.chunkRequests(requests, 500);

        assertEquals(3, chunks.size());
        assertEquals(500, chunks.get(0).size());
        assertEquals(500, chunks.get(1).size());
        assertEquals(200, chunks.get(2).size());
    }

    @Test
    void chunkRequests_singleChunkWhenUnderLimit() {
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            requests.add(new Request());
        }

        List<List<Request>> chunks = GoogleDocApiClient.chunkRequests(requests, 500);

        assertEquals(1, chunks.size());
        assertEquals(100, chunks.get(0).size());
    }

    @Test
    void chunkRequests_emptyList() {
        List<List<Request>> chunks = GoogleDocApiClient.chunkRequests(List.of(), 500);
        assertTrue(chunks.isEmpty());
    }
}
