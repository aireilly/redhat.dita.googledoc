package org.dita.googledoc;

import com.google.api.services.docs.v1.model.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationTest {

    private GoogleDocStyleMapper styleMapper;
    private DitaToGoogleDocMapper mapper;

    @BeforeEach
    void setUp() {
        styleMapper = new GoogleDocStyleMapper();
        mapper = new DitaToGoogleDocMapper(styleMapper, null);
    }

    @Test
    void fullMapProcessing_generatesRequests() throws Exception {
        URL mapUrl = getClass().getResource("/sample/sample.ditamap");
        assertNotNull(mapUrl, "sample.ditamap not found on classpath");

        String mapPath = Path.of(mapUrl.toURI()).toString();
        String mapTitle = GoogleDocRenderer.parseMapTitle(mapPath);
        List<GoogleDocRenderer.TopicRef> refs = GoogleDocRenderer.parseTopicRefs(mapPath);

        assertEquals("Sample Google Doc Output", mapTitle);
        assertEquals(4, refs.size());
        assertEquals("intro.dita", refs.get(0).href());
        assertEquals(0, refs.get(0).depth());
        assertEquals("features.dita", refs.get(1).href());
        assertEquals(1, refs.get(1).depth());
        assertEquals("task-demo.dita", refs.get(2).href());
        assertEquals(0, refs.get(2).depth());
        assertEquals("elements-demo.dita", refs.get(3).href());
        assertEquals(0, refs.get(3).depth());

        List<Request> allRequests = new ArrayList<>();
        String mapDir = Path.of(mapPath).getParent().toString();

        for (GoogleDocRenderer.TopicRef ref : refs) {
            String topicPath = Path.of(mapDir, ref.href()).toString();
            org.w3c.dom.Document topicDoc = javax.xml.parsers.DocumentBuilderFactory
                .newInstance().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(topicPath));
            allRequests.addAll(mapper.mapTopic(topicDoc, ref.depth()));
        }

        assertFalse(allRequests.isEmpty());
        assertTrue(allRequests.size() > 20,
            "Expected >20 requests for sample content, got " + allRequests.size());

        assertTrue(allRequests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("Introduction")));

        assertTrue(allRequests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("Features")));

        assertTrue(allRequests.stream().anyMatch(r ->
            r.getInsertTable() != null));

        assertTrue(allRequests.stream().anyMatch(r ->
            r.getCreateParagraphBullets() != null));

        assertTrue(allRequests.stream().anyMatch(r ->
            r.getUpdateTextStyle() != null &&
            r.getUpdateTextStyle().getTextStyle().getLink() != null));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GOOGLE_CREDENTIALS_PATH", matches = ".+")
    void liveGoogleDocsCreation() throws Exception {
        String credentialsPath = System.getenv("GOOGLE_CREDENTIALS_PATH");
        String authType = System.getenv().getOrDefault("GOOGLE_AUTH_TYPE", "service");
        String folderId = System.getenv("GOOGLE_FOLDER_ID");

        URL mapUrl = getClass().getResource("/sample/sample.ditamap");
        String mapPath = Path.of(mapUrl.toURI()).toString();
        String mapDir = Path.of(mapPath).getParent().toString();

        GoogleDocRenderer.run(
            mapDir, mapPath, credentialsPath, authType,
            null, folderId, null, 468.0);
    }
}
