package org.dita.googledoc;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.docs.v1.model.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LiveRenderTest {

    static boolean gcloudAvailable() {
        return GoogleAuthProvider.hasGcloud() &&
            GoogleAuthProvider.tryGcloudToken() != null;
    }

    @Test
    @EnabledIf("gcloudAvailable")
    void renderSampleToLiveGoogleDoc() throws Exception {
        URL mapUrl = getClass().getResource("/sample/sample.ditamap");
        assertNotNull(mapUrl);
        String mapPath = Path.of(mapUrl.toURI()).toString();
        String mapDir = Path.of(mapPath).getParent().toString();

        HttpRequestInitializer credential =
            GoogleAuthProvider.getCredential(null, "auto", null);
        GoogleDocApiClient apiClient = new GoogleDocApiClient(credential);

        String docId = apiClient.createDocument(
            "DITA Plugin Demo — Tables, Lists, Code & Formatting", null);

        GoogleDocStyleMapper styleMapper = new GoogleDocStyleMapper();
        ImageHandler imageHandler = new ImageHandler(apiClient, mapDir, null, 468.0);
        DitaToGoogleDocMapper mapper = new DitaToGoogleDocMapper(styleMapper, imageHandler);

        String mapTitle = GoogleDocRenderer.parseMapTitle(mapPath);
        List<GoogleDocRenderer.TopicRef> refs = GoogleDocRenderer.parseTopicRefs(mapPath);
        List<Request> allRequests = new ArrayList<>();

        for (GoogleDocRenderer.TopicRef ref : refs) {
            String topicPath = Path.of(mapDir, ref.href()).toString();
            Document topicDoc = parseXml(topicPath);
            allRequests.addAll(mapper.mapTopic(topicDoc, ref.depth()));
        }

        assertFalse(allRequests.isEmpty());
        apiClient.executeBatchUpdate(docId, allRequests);

        String url = "https://docs.google.com/document/d/" + docId + "/edit";
        System.out.println("LIVE RENDER: " + url);
        System.out.println("Request count: " + allRequests.size());
    }

    private Document parseXml(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature(
            "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory.newDocumentBuilder().parse(new InputSource(path));
    }
}
