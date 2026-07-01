package org.dita.googledoc;

import com.google.api.services.docs.v1.model.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DitaToGoogleDocMapper {

    private final GoogleDocStyleMapper styleMapper;
    private final ImageHandler imageHandler;
    private int currentIndex = 1;
    private int nestingLevel = 0;

    public DitaToGoogleDocMapper(GoogleDocStyleMapper styleMapper, ImageHandler imageHandler) {
        this.styleMapper = styleMapper;
        this.imageHandler = imageHandler;
    }

    public void resetIndex() {
        this.currentIndex = 1;
        this.nestingLevel = 0;
    }

    public List<Request> mapTopic(Document topicDoc, int topicDepth) {
        List<Request> requests = new ArrayList<>();
        Element root = topicDoc.getDocumentElement();
        processElement(root, topicDepth, requests, null);
        return requests;
    }

    private void processElement(Element element, int topicDepth,
                                List<Request> requests, String listType) {
        String tagName = element.getTagName();

        switch (tagName) {
            case "topic", "concept", "task", "reference" ->
                processTopicElement(element, topicDepth, requests);
            case "title" ->
                processTitle(element, topicDepth, requests);
            case "section" ->
                processSection(element, topicDepth, requests);
            case "body", "conbody", "taskbody", "refbody" ->
                processChildren(element, topicDepth, requests, listType);
            case "p" ->
                processParagraph(element, topicDepth, requests);
            case "ul" ->
                processList(element, topicDepth, requests, "ul");
            case "ol" ->
                processList(element, topicDepth, requests, "ol");
            case "li" ->
                processListItem(element, topicDepth, requests, listType);
            case "table", "simpletable" ->
                processTable(element, requests);
            case "codeblock" ->
                processCodeblock(element, requests);
            case "note" ->
                processNote(element, topicDepth, requests);
            case "dl" ->
                processDefinitionList(element, topicDepth, requests);
            case "image" ->
                processImage(element, requests);
            case "xref" ->
                processXref(element, requests);
            case "fig" ->
                processChildren(element, topicDepth, requests, listType);
            case "pre" ->
                processCodeblock(element, requests);
            case "steps", "steps-unordered" ->
                processList(element, topicDepth, requests,
                    "steps".equals(tagName) ? "ol" : "ul");
            case "step" ->
                processStep(element, topicDepth, requests, listType);
            case "cmd" ->
                processInlineChildren(element, requests);
            case "related-links" ->
                processRelatedLinks(element, requests);
            default ->
                processChildren(element, topicDepth, requests, listType);
        }
    }

    private void processTopicElement(Element element, int topicDepth,
                                     List<Request> requests) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                String childTag = childElement.getTagName();
                if ("title".equals(childTag)) {
                    processTitle(childElement, topicDepth, requests);
                } else if ("topic".equals(childTag) || "concept".equals(childTag) ||
                           "task".equals(childTag) || "reference".equals(childTag)) {
                    processElement(childElement, topicDepth + 1, requests, null);
                } else {
                    processElement(childElement, topicDepth, requests, null);
                }
            }
        }
    }

    private void processTitle(Element element, int topicDepth, List<Request> requests) {
        Element parent = (Element) element.getParentNode();
        String parentTag = parent != null ? parent.getTagName() : "";

        String styleName;
        if ("section".equals(parentTag)) {
            styleName = styleMapper.getNamedStyle("section", topicDepth);
        } else {
            styleName = styleMapper.getNamedStyle("title", topicDepth);
        }

        String text = getTextContent(element);
        int startIndex = currentIndex;
        insertText(text + "\n", requests);

        requests.add(new Request().setUpdateParagraphStyle(
            new UpdateParagraphStyleRequest()
                .setParagraphStyle(new ParagraphStyle().setNamedStyleType(styleName))
                .setRange(new Range().setStartIndex(startIndex).setEndIndex(currentIndex - 1))
                .setFields("namedStyleType")));
    }

    private void processSection(Element element, int topicDepth, List<Request> requests) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                processElement(childElement, topicDepth, requests, null);
            }
        }
    }

    private void processParagraph(Element element, int topicDepth, List<Request> requests) {
        int startIndex = currentIndex;
        processInlineChildren(element, requests);
        insertText("\n", requests);

        requests.add(new Request().setUpdateParagraphStyle(
            new UpdateParagraphStyleRequest()
                .setParagraphStyle(new ParagraphStyle().setNamedStyleType("NORMAL_TEXT"))
                .setRange(new Range().setStartIndex(startIndex).setEndIndex(currentIndex - 1))
                .setFields("namedStyleType")));
    }

    private void processList(Element element, int topicDepth,
                             List<Request> requests, String type) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                processElement(childElement, topicDepth, requests, type);
            }
        }
    }

    private void applyBullet(int startIndex, int endIndex,
                             String listType, List<Request> requests) {
        if (startIndex >= endIndex) return;

        String bulletPreset = "ol".equals(listType)
            ? "NUMBERED_DECIMAL_ALPHA_ROMAN"
            : "BULLET_DISC_CIRCLE_SQUARE";

        requests.add(new Request().setCreateParagraphBullets(
            new CreateParagraphBulletsRequest()
                .setRange(new Range().setStartIndex(startIndex).setEndIndex(endIndex))
                .setBulletPreset(bulletPreset)));

        if (nestingLevel > 0) {
            double indent = 36.0 * nestingLevel;
            requests.add(new Request().setUpdateParagraphStyle(
                new UpdateParagraphStyleRequest()
                    .setParagraphStyle(new ParagraphStyle()
                        .setIndentStart(new Dimension().setMagnitude(indent).setUnit("PT"))
                        .setIndentFirstLine(new Dimension().setMagnitude(indent).setUnit("PT")))
                    .setRange(new Range().setStartIndex(startIndex).setEndIndex(endIndex))
                    .setFields("indentFirstLine,indentStart")));
        }
    }

    private void processListItem(Element element, int topicDepth,
                                 List<Request> requests, String listType) {
        int startIndex = currentIndex;
        boolean hasInline = false;

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getTextContent();
                if (!text.trim().isEmpty()) {
                    insertText(text, requests);
                    hasInline = true;
                }
            } else if (child instanceof Element childElement) {
                String tag = childElement.getTagName();

                if ("ul".equals(tag) || "ol".equals(tag)) {
                    if (hasInline) {
                        insertText("\n", requests);
                        applyBullet(startIndex, currentIndex - 1, listType, requests);
                        hasInline = false;
                    }
                    nestingLevel++;
                    processList(childElement, topicDepth, requests, tag);
                    nestingLevel--;
                    startIndex = currentIndex;
                } else if ("p".equals(tag)) {
                    if (hasInline) {
                        insertText("\n", requests);
                        applyBullet(startIndex, currentIndex - 1, listType, requests);
                        hasInline = false;
                    }
                    startIndex = currentIndex;
                    processInlineChildren(childElement, requests);
                    insertText("\n", requests);
                    applyBullet(startIndex, currentIndex - 1, listType, requests);
                    startIndex = currentIndex;
                } else {
                    processInlineElement(childElement, requests);
                    hasInline = true;
                }
            }
        }

        if (hasInline) {
            insertText("\n", requests);
            applyBullet(startIndex, currentIndex - 1, listType, requests);
        }
    }

    private void processStep(Element element, int topicDepth,
                             List<Request> requests, String listType) {
        int startIndex = currentIndex;
        boolean flushed = false;
        String effectiveType = listType != null ? listType : "ol";

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                String tag = childElement.getTagName();
                switch (tag) {
                    case "cmd" -> processInlineChildren(childElement, requests);
                    case "info", "stepresult" -> {
                        if (!flushed) {
                            insertText(" ", requests);
                        }
                        processInlineChildren(childElement, requests);
                    }
                    case "substeps" -> {
                        if (currentIndex > startIndex && !flushed) {
                            insertText("\n", requests);
                            applyBullet(startIndex, currentIndex - 1, effectiveType, requests);
                            flushed = true;
                        }
                        nestingLevel++;
                        NodeList substepNodes = childElement.getChildNodes();
                        for (int j = 0; j < substepNodes.getLength(); j++) {
                            Node sn = substepNodes.item(j);
                            if (sn instanceof Element se
                                && "substep".equals(se.getTagName())) {
                                processStep(se, topicDepth, requests, "ol");
                            }
                        }
                        nestingLevel--;
                        startIndex = currentIndex;
                    }
                }
            }
        }

        if (!flushed && currentIndex > startIndex) {
            insertText("\n", requests);
            applyBullet(startIndex, currentIndex - 1, effectiveType, requests);
        }
    }

    private void processTable(Element element, List<Request> requests) {
        int rows = 0;
        int cols = 0;
        List<List<String>> cellContents = new ArrayList<>();

        Element tgroup = getFirstChildByTag(element, "tgroup");
        if (tgroup != null) {
            String colsAttr = tgroup.getAttribute("cols");
            if (!colsAttr.isEmpty()) {
                cols = Integer.parseInt(colsAttr);
            }

            Element thead = getFirstChildByTag(tgroup, "thead");
            if (thead != null) {
                collectRows(thead, cellContents);
            }
            Element tbody = getFirstChildByTag(tgroup, "tbody");
            if (tbody != null) {
                collectRows(tbody, cellContents);
            }
        } else {
            Element sthead = getFirstChildByTag(element, "sthead");
            if (sthead != null) {
                List<String> row = collectEntries(sthead, "stentry");
                cellContents.add(row);
                if (cols == 0) cols = row.size();
            }
            NodeList strows = element.getElementsByTagName("strow");
            for (int i = 0; i < strows.getLength(); i++) {
                List<String> row = collectEntries((Element) strows.item(i), "stentry");
                cellContents.add(row);
                if (cols == 0) cols = row.size();
            }
        }

        rows = cellContents.size();
        if (rows == 0 || cols == 0) return;

        requests.add(new Request().setInsertTable(
            new InsertTableRequest()
                .setRows(rows)
                .setColumns(cols)
                .setLocation(new Location().setIndex(currentIndex))));

        currentIndex += 4;

        for (int r = 0; r < rows; r++) {
            List<String> row = cellContents.get(r);
            for (int c = 0; c < cols; c++) {
                String cellText = c < row.size() ? row.get(c) : "";
                if (!cellText.isEmpty()) {
                    insertText(cellText, requests);
                }
                if (c < cols - 1) {
                    currentIndex += 2;
                }
            }
            if (r < rows - 1) {
                currentIndex += 3;
            }
        }

        currentIndex += 2;
    }

    private void collectRows(Element parent, List<List<String>> cellContents) {
        NodeList rows = parent.getElementsByTagName("row");
        for (int i = 0; i < rows.getLength(); i++) {
            cellContents.add(collectEntries((Element) rows.item(i), "entry"));
        }
    }

    private List<String> collectEntries(Element row, String entryTag) {
        List<String> entries = new ArrayList<>();
        NodeList children = row.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el && entryTag.equals(el.getTagName())) {
                entries.add(getTextContent(el));
            }
        }
        return entries;
    }

    private void processCodeblock(Element element, List<Request> requests) {
        String text = element.getTextContent();
        int startIndex = currentIndex;
        insertText(text + "\n", requests);

        requests.add(new Request().setUpdateTextStyle(
            new UpdateTextStyleRequest()
                .setTextStyle(new TextStyle().setWeightedFontFamily(
                    new WeightedFontFamily().setFontFamily("Courier New")))
                .setRange(new Range().setStartIndex(startIndex).setEndIndex(currentIndex - 1))
                .setFields("weightedFontFamily")));
    }

    private void processNote(Element element, int topicDepth, List<Request> requests) {
        String noteType = element.getAttribute("type");
        if (noteType.isEmpty()) noteType = "note";

        String prefix = styleMapper.getNotePrefix(noteType);
        int startIndex = currentIndex;
        int prefixStart = currentIndex;
        insertText(prefix, requests);
        int prefixEnd = currentIndex;

        processInlineChildren(element, requests);
        insertText("\n", requests);

        requests.add(new Request().setUpdateTextStyle(
            new UpdateTextStyleRequest()
                .setTextStyle(new TextStyle().setBold(true))
                .setRange(new Range().setStartIndex(prefixStart).setEndIndex(prefixEnd))
                .setFields("bold")));

        requests.add(new Request().setUpdateParagraphStyle(
            new UpdateParagraphStyleRequest()
                .setParagraphStyle(new ParagraphStyle()
                    .setIndentFirstLine(new Dimension().setMagnitude(36.0).setUnit("PT"))
                    .setIndentStart(new Dimension().setMagnitude(36.0).setUnit("PT")))
                .setRange(new Range().setStartIndex(startIndex).setEndIndex(currentIndex - 1))
                .setFields("indentFirstLine,indentStart")));
    }

    private void processDefinitionList(Element element, int topicDepth,
                                       List<Request> requests) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element dlentry && "dlentry".equals(dlentry.getTagName())) {
                Element dt = getFirstChildByTag(dlentry, "dt");
                Element dd = getFirstChildByTag(dlentry, "dd");

                if (dt != null) {
                    int dtStart = currentIndex;
                    insertText(getTextContent(dt) + "\n", requests);
                    requests.add(new Request().setUpdateTextStyle(
                        new UpdateTextStyleRequest()
                            .setTextStyle(new TextStyle().setBold(true))
                            .setRange(new Range().setStartIndex(dtStart)
                                .setEndIndex(currentIndex - 1))
                            .setFields("bold")));
                }

                if (dd != null) {
                    int ddStart = currentIndex;
                    processInlineChildren(dd, requests);
                    insertText("\n", requests);

                    requests.add(new Request().setUpdateParagraphStyle(
                        new UpdateParagraphStyleRequest()
                            .setParagraphStyle(new ParagraphStyle()
                                .setIndentFirstLine(
                                    new Dimension().setMagnitude(36.0).setUnit("PT"))
                                .setIndentStart(
                                    new Dimension().setMagnitude(36.0).setUnit("PT")))
                            .setRange(new Range().setStartIndex(ddStart)
                                .setEndIndex(currentIndex - 1))
                            .setFields("indentFirstLine,indentStart")));
                }
            }
        }
    }

    private void processImage(Element element, List<Request> requests) {
        if (imageHandler == null) {
            String href = element.getAttribute("href");
            insertText("[image: " + href + "]\n", requests);
            return;
        }

        String href = element.getAttribute("href");
        try {
            ImageHandler.ImageInfo info = imageHandler.resolveImage(href);
            requests.add(new Request().setInsertInlineImage(
                new InsertInlineImageRequest()
                    .setUri(info.uri())
                    .setObjectSize(new Size()
                        .setWidth(new Dimension()
                            .setMagnitude(info.widthPt()).setUnit("PT"))
                        .setHeight(new Dimension()
                            .setMagnitude(info.heightPt()).setUnit("PT")))
                    .setLocation(new Location().setIndex(currentIndex))));
            currentIndex += 1;
        } catch (IOException e) {
            insertText("[image not found: " + href + "]\n", requests);
        }
    }

    private void processXref(Element element, List<Request> requests) {
        String href = element.getAttribute("href");
        String scope = element.getAttribute("scope");
        String text = getTextContent(element);
        if (text.isEmpty()) text = href;

        int startIndex = currentIndex;
        insertText(text, requests);

        if ("external".equals(scope) || href.startsWith("http://") || href.startsWith("https://")) {
            requests.add(new Request().setUpdateTextStyle(
                new UpdateTextStyleRequest()
                    .setTextStyle(new TextStyle().setLink(new Link().setUrl(href)))
                    .setRange(new Range().setStartIndex(startIndex).setEndIndex(currentIndex))
                    .setFields("link")));
        }
    }

    private void processRelatedLinks(Element element, List<Request> requests) {
        int startIndex = currentIndex;
        insertText("Related Links\n", requests);
        requests.add(new Request().setUpdateParagraphStyle(
            new UpdateParagraphStyleRequest()
                .setParagraphStyle(new ParagraphStyle().setNamedStyleType("HEADING_3"))
                .setRange(new Range().setStartIndex(startIndex).setEndIndex(currentIndex - 1))
                .setFields("namedStyleType")));

        NodeList links = element.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            String href = link.getAttribute("href");
            Element linktext = getFirstChildByTag(link, "linktext");
            String text = linktext != null ? getTextContent(linktext) : href;

            int linkStart = currentIndex;
            insertText(text + "\n", requests);

            if (href.startsWith("http://") || href.startsWith("https://")) {
                requests.add(new Request().setUpdateTextStyle(
                    new UpdateTextStyleRequest()
                        .setTextStyle(new TextStyle().setLink(new Link().setUrl(href)))
                        .setRange(new Range().setStartIndex(linkStart)
                            .setEndIndex(currentIndex - 1))
                        .setFields("link")));
            }

            requests.add(new Request().setCreateParagraphBullets(
                new CreateParagraphBulletsRequest()
                    .setRange(new Range().setStartIndex(linkStart).setEndIndex(currentIndex - 1))
                    .setBulletPreset("BULLET_DISC_CIRCLE_SQUARE")));
        }
    }

    private void processInlineChildren(Element element, List<Request> requests) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getTextContent();
                if (!text.isEmpty()) {
                    insertText(text, requests);
                }
            } else if (child instanceof Element childElement) {
                processInlineElement(childElement, requests);
            }
        }
    }

    private void processInlineElement(Element childElement, List<Request> requests) {
        String tag = childElement.getTagName();
        TextStyle style = styleMapper.getTextStyle(tag);

        if ("xref".equals(tag)) {
            processXref(childElement, requests);
        } else if ("image".equals(tag)) {
            processImage(childElement, requests);
        } else if (style != null) {
            int startIndex = currentIndex;
            processInlineChildren(childElement, requests);
            int endIndex = currentIndex;

            String fields = buildFieldMask(style);
            requests.add(new Request().setUpdateTextStyle(
                new UpdateTextStyleRequest()
                    .setTextStyle(style)
                    .setRange(new Range().setStartIndex(startIndex).setEndIndex(endIndex))
                    .setFields(fields)));
        } else {
            processInlineChildren(childElement, requests);
        }
    }

    private String buildFieldMask(TextStyle style) {
        List<String> fields = new ArrayList<>();
        if (style.getBold() != null) fields.add("bold");
        if (style.getItalic() != null) fields.add("italic");
        if (style.getUnderline() != null) fields.add("underline");
        if (style.getStrikethrough() != null) fields.add("strikethrough");
        if (style.getWeightedFontFamily() != null) fields.add("weightedFontFamily");
        if (style.getLink() != null) fields.add("link");
        return String.join(",", fields);
    }

    private void insertText(String text, List<Request> requests) {
        requests.add(new Request().setInsertText(
            new InsertTextRequest()
                .setText(text)
                .setLocation(new Location().setIndex(currentIndex))));
        currentIndex += text.length();
    }

    private String getTextContent(Element element) {
        StringBuilder sb = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                sb.append(child.getTextContent());
            } else if (child instanceof Element) {
                sb.append(getTextContent((Element) child));
            }
        }
        return sb.toString();
    }

    private Element getFirstChildByTag(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el && tagName.equals(el.getTagName())) {
                return el;
            }
        }
        return null;
    }

    private void processChildren(Element element, int topicDepth,
                                 List<Request> requests, String listType) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                processElement(childElement, topicDepth, requests, listType);
            }
        }
    }
}
