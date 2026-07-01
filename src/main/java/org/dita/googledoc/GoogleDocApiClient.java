package org.dita.googledoc;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.*;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import java.io.IOException;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class GoogleDocApiClient {

    private static final String APP_NAME = "DITA-OT-GoogleDoc-Plugin";
    private static final int BATCH_CHUNK_SIZE = 500;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private final Docs docsService;
    private final Drive driveService;

    public GoogleDocApiClient(HttpRequestInitializer credential) {
        try {
            var transport = GoogleNetHttpTransport.newTrustedTransport();
            var jsonFactory = GsonFactory.getDefaultInstance();

            this.docsService = new Docs.Builder(transport, jsonFactory, credential)
                .setApplicationName(APP_NAME)
                .build();

            this.driveService = new Drive.Builder(transport, jsonFactory, credential)
                .setApplicationName(APP_NAME)
                .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to initialize Google API clients", e);
        }
    }

    public String createDocument(String title, String folderId) throws IOException {
        if (folderId != null && !folderId.isEmpty()) {
            File fileMetadata = new File();
            fileMetadata.setName(title);
            fileMetadata.setMimeType("application/vnd.google-apps.document");
            fileMetadata.setParents(List.of(folderId));
            File created = driveService.files().create(fileMetadata)
                .setFields("id")
                .execute();
            return created.getId();
        }
        Document doc = new Document().setTitle(title);
        Document created = docsService.documents().create(doc).execute();
        return created.getDocumentId();
    }

    public void clearDocument(String docId) throws IOException {
        Document doc = docsService.documents().get(docId).execute();
        Body body = doc.getBody();
        if (body == null || body.getContent() == null) return;

        int endIndex = 1;
        for (StructuralElement element : body.getContent()) {
            if (element.getEndIndex() != null && element.getEndIndex() > endIndex) {
                endIndex = element.getEndIndex();
            }
        }

        if (endIndex > 2) {
            Request deleteRequest = new Request().setDeleteContentRange(
                new DeleteContentRangeRequest().setRange(
                    new Range().setStartIndex(1).setEndIndex(endIndex - 1)));
            docsService.documents().batchUpdate(docId,
                new BatchUpdateDocumentRequest().setRequests(List.of(deleteRequest)))
                .execute();
        }
    }

    public void executeBatchUpdate(String docId, List<Request> requests) throws IOException {
        if (requests.isEmpty()) return;

        for (List<Request> chunk : chunkRequests(requests, BATCH_CHUNK_SIZE)) {
            executeWithRetry(docId, chunk);
        }
    }

    private void executeWithRetry(String docId, List<Request> requests) throws IOException {
        long backoff = INITIAL_BACKOFF_MS;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                docsService.documents().batchUpdate(docId,
                    new BatchUpdateDocumentRequest().setRequests(requests))
                    .execute();
                return;
            } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
                if (e.getStatusCode() == 429 || e.getStatusCode() >= 500) {
                    if (attempt == MAX_RETRIES - 1) throw e;
                    try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted during retry backoff", ie);
                    }
                    backoff *= 2;
                } else {
                    throw e;
                }
            }
        }
    }

    public String uploadImage(String filePath, String folderId) throws IOException {
        java.io.File imageFile = new java.io.File(filePath);
        String mimeType = URLConnection.guessContentTypeFromName(imageFile.getName());
        if (mimeType == null) mimeType = "application/octet-stream";

        File fileMetadata = new File();
        fileMetadata.setName(imageFile.getName());
        if (folderId != null && !folderId.isEmpty()) {
            fileMetadata.setParents(List.of(folderId));
        }

        FileContent mediaContent = new FileContent(mimeType, imageFile);
        File uploaded = driveService.files().create(fileMetadata, mediaContent)
            .setFields("id,webContentLink")
            .execute();

        driveService.permissions().create(uploaded.getId(),
            new com.google.api.services.drive.model.Permission()
                .setType("anyone")
                .setRole("reader"))
            .execute();

        return "https://drive.google.com/uc?id=" + uploaded.getId();
    }

    public Docs getDocsService() {
        return docsService;
    }

    public Drive getDriveService() {
        return driveService;
    }

    static List<List<Request>> chunkRequests(List<Request> requests, int chunkSize) {
        if (requests.isEmpty()) return List.of();
        List<List<Request>> chunks = new ArrayList<>();
        for (int i = 0; i < requests.size(); i += chunkSize) {
            chunks.add(requests.subList(i, Math.min(i + chunkSize, requests.size())));
        }
        return chunks;
    }
}
