package org.dita.googledoc;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.docs.v1.model.Request;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class GoogleDocRenderer {

    public record TopicRef(String href, int depth) {}

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println(
                "Usage: GoogleDocRenderer <tempDir> <inputMap> [credentials] [authType] " +
                "[docId] [folderId] [tokenDir] [imageMaxWidth]");
            System.exit(1);
        }

        String tempDir = args[0];
        String inputMap = args[1];
        String credentialsPath = args.length > 2 && !args[2].isEmpty() ? args[2] : null;
        String authType = args.length > 3 && !args[3].isEmpty() ? args[3] : "auto";
        String docId = args.length > 4 && !args[4].isEmpty() ? args[4] : null;
        String folderId = args.length > 5 && !args[5].isEmpty() ? args[5] : null;
        String tokenDir = args.length > 6 && !args[6].isEmpty() ? args[6] : null;
        double imageMaxWidth = args.length > 7 && !args[7].isEmpty()
            ? Double.parseDouble(args[7]) : 468.0;

        try {
            run(tempDir, inputMap, credentialsPath, authType,
                docId, folderId, tokenDir, imageMaxWidth);
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    static void run(String tempDir, String inputMap, String credentialsPath,
                    String authType, String docId, String folderId,
                    String tokenDir, double imageMaxWidth)
            throws IOException, GeneralSecurityException {

        String resolvedMapPath = findResolvedMap(tempDir, inputMap);

        String mapTitle = parseMapTitle(resolvedMapPath);
        List<TopicRef> topicRefs = parseTopicRefs(resolvedMapPath);

        System.out.println("Resolved " + topicRefs.size() + " topic(s) from map: " + mapTitle);

        HttpRequestInitializer credential =
            GoogleAuthProvider.getCredential(credentialsPath, authType, tokenDir);
        GoogleDocApiClient apiClient = new GoogleDocApiClient(credential);

        if (docId != null) {
            System.out.println("Updating existing document: " + docId);
            apiClient.clearDocument(docId);
        } else {
            docId = apiClient.createDocument(mapTitle, folderId);
            System.out.println("Created new document: " + docId);
        }

        ImageHandler imageHandler = new ImageHandler(apiClient, tempDir, folderId, imageMaxWidth);
        GoogleDocStyleMapper styleMapper = new GoogleDocStyleMapper();
        DitaToGoogleDocMapper mapper = new DitaToGoogleDocMapper(styleMapper, imageHandler);

        List<Request> allRequests = new ArrayList<>();
        String mapDir = Path.of(resolvedMapPath).getParent().toString();

        for (TopicRef ref : topicRefs) {
            String topicPath = Path.of(mapDir, ref.href()).toString();
            File topicFile = new File(topicPath);
            if (!topicFile.exists()) {
                System.err.println("WARNING: Topic file not found: " + topicPath);
                continue;
            }

            try {
                Document topicDoc = parseXml(topicPath);
                List<Request> requests = mapper.mapTopic(topicDoc, ref.depth());
                allRequests.addAll(requests);
            } catch (Exception e) {
                System.err.println("WARNING: Failed to process topic " + ref.href()
                    + ": " + e.getMessage());
            }
        }

        if (!allRequests.isEmpty()) {
            apiClient.executeBatchUpdate(docId, allRequests);
        }

        System.out.println("Google Doc URL: https://docs.google.com/document/d/" + docId + "/edit");
    }

    static String findResolvedMap(String tempDir, String inputMap) {
        String mapFilename = Path.of(inputMap).getFileName().toString();
        File resolved = new File(tempDir, mapFilename);
        if (resolved.exists()) return resolved.getAbsolutePath();

        File inputFile = new File(inputMap);
        if (inputFile.exists()) return inputFile.getAbsolutePath();

        return resolved.getAbsolutePath();
    }

    static String parseMapTitle(String mapPath) throws IOException {
        try {
            Document doc = parseXml(mapPath);
            Element root = doc.getDocumentElement();
            NodeList titles = root.getElementsByTagName("title");
            if (titles.getLength() > 0) {
                return titles.item(0).getTextContent().trim();
            }
            return Path.of(mapPath).getFileName().toString().replaceFirst("\\.ditamap$", "");
        } catch (Exception e) {
            throw new IOException("Failed to parse map: " + mapPath, e);
        }
    }

    static List<TopicRef> parseTopicRefs(String mapPath) throws IOException {
        try {
            Document doc = parseXml(mapPath);
            Element root = doc.getDocumentElement();
            List<TopicRef> refs = new ArrayList<>();
            collectTopicRefs(root, refs, 0);
            return refs;
        } catch (Exception e) {
            throw new IOException("Failed to parse map: " + mapPath, e);
        }
    }

    private static final java.util.Set<String> TOPICREF_ELEMENTS = java.util.Set.of(
        "topicref", "chapter", "appendix",
        "part", "preface", "notices", "dedication", "colophon",
        "amendments", "glossarylist"
    );

    private static void collectTopicRefs(Element element, List<TopicRef> refs, int depth) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el) {
                String tag = el.getTagName();
                if (TOPICREF_ELEMENTS.contains(tag)) {
                    String href = el.getAttribute("href");
                    if (!href.isEmpty()) {
                        refs.add(new TopicRef(href, depth));
                    }
                    collectTopicRefs(el, refs, depth + 1);
                } else if (!"title".equals(tag) && !"booktitle".equals(tag)
                           && !"bookmeta".equals(tag)) {
                    collectTopicRefs(el, refs, depth);
                }
            }
        }
    }

    private static Document parseXml(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature(
            "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory.newDocumentBuilder().parse(new InputSource(path));
    }
}
