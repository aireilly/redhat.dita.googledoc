package org.dita.googledoc;

import com.google.api.services.docs.v1.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DitaToGoogleDocMapperTest {

    private DitaToGoogleDocMapper mapper;
    private GoogleDocStyleMapper styleMapper;

    @BeforeEach
    void setUp() {
        styleMapper = new GoogleDocStyleMapper();
        mapper = new DitaToGoogleDocMapper(styleMapper, null);
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory.newDocumentBuilder()
            .parse(new InputSource(new StringReader(xml)));
    }

    @Test
    void mapTopic_titleBecomeHeading() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>Hello World</title>
              <body><p>Some text.</p></body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("Hello World")));

        assertTrue(requests.stream().anyMatch(r ->
            r.getUpdateParagraphStyle() != null &&
            "HEADING_1".equals(r.getUpdateParagraphStyle()
                .getParagraphStyle().getNamedStyleType())));
    }

    @Test
    void mapTopic_paragraphText() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body><p>Body text here.</p></body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("Body text here.")));
    }

    @Test
    void mapTopic_boldInline() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body><p>This is <b>bold</b> text.</p></body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getUpdateTextStyle() != null &&
            Boolean.TRUE.equals(r.getUpdateTextStyle().getTextStyle().getBold())));
    }

    @Test
    void mapTopic_italicInline() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body><p>This is <i>italic</i> text.</p></body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getUpdateTextStyle() != null &&
            Boolean.TRUE.equals(r.getUpdateTextStyle().getTextStyle().getItalic())));
    }

    @Test
    void mapTopic_codeph() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body><p>Use <codeph>myFunc()</codeph> here.</p></body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getUpdateTextStyle() != null &&
            r.getUpdateTextStyle().getTextStyle().getWeightedFontFamily() != null &&
            "Courier New".equals(r.getUpdateTextStyle().getTextStyle()
                .getWeightedFontFamily().getFontFamily())));
    }

    @Test
    void mapTopic_unorderedList() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body>
                <ul>
                  <li>Item one</li>
                  <li>Item two</li>
                </ul>
              </body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getCreateParagraphBullets() != null &&
            "BULLET_DISC_CIRCLE_SQUARE".equals(
                r.getCreateParagraphBullets().getBulletPreset())));
    }

    @Test
    void mapTopic_orderedList() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body>
                <ol>
                  <li>Step one</li>
                  <li>Step two</li>
                </ol>
              </body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getCreateParagraphBullets() != null &&
            "NUMBERED_DECIMAL_ALPHA_ROMAN".equals(
                r.getCreateParagraphBullets().getBulletPreset())));
    }

    @Test
    void mapTopic_codeblock() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body><codeblock>int x = 42;</codeblock></body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("int x = 42;")));

        assertTrue(requests.stream().anyMatch(r ->
            r.getUpdateTextStyle() != null &&
            r.getUpdateTextStyle().getTextStyle().getWeightedFontFamily() != null &&
            "Courier New".equals(r.getUpdateTextStyle().getTextStyle()
                .getWeightedFontFamily().getFontFamily())));
    }

    @Test
    void mapTopic_note() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body><note type="warning">Be careful.</note></body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("Warning: ")));
    }

    @Test
    void mapTopic_externalXref() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body><p>See <xref href="https://example.com" format="html" scope="external">example</xref>.</p></body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getUpdateTextStyle() != null &&
            r.getUpdateTextStyle().getTextStyle().getLink() != null &&
            "https://example.com".equals(
                r.getUpdateTextStyle().getTextStyle().getLink().getUrl())));
    }

    @Test
    void mapTopic_table() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body>
                <table>
                  <tgroup cols="2">
                    <thead>
                      <row><entry>Col A</entry><entry>Col B</entry></row>
                    </thead>
                    <tbody>
                      <row><entry>A1</entry><entry>B1</entry></row>
                    </tbody>
                  </tgroup>
                </table>
              </body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getInsertTable() != null &&
            r.getInsertTable().getRows() == 2 &&
            r.getInsertTable().getColumns() == 2));
    }

    @Test
    void mapTopic_sectionHeading() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body>
                <section>
                  <title>Section Title</title>
                  <p>Section content.</p>
                </section>
              </body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("Section Title")));

        assertTrue(requests.stream().anyMatch(r ->
            r.getUpdateParagraphStyle() != null &&
            "HEADING_2".equals(r.getUpdateParagraphStyle()
                .getParagraphStyle().getNamedStyleType())));
    }

    @Test
    void mapTopic_nestedTopicDepth() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>Nested Title</title>
              <body><p>Text.</p></body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 2);

        assertTrue(requests.stream().anyMatch(r ->
            r.getUpdateParagraphStyle() != null &&
            "HEADING_3".equals(r.getUpdateParagraphStyle()
                .getParagraphStyle().getNamedStyleType())));
    }

    @Test
    void mapTopic_definitionList() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body>
                <dl>
                  <dlentry>
                    <dt>Term</dt>
                    <dd>Definition text</dd>
                  </dlentry>
                </dl>
              </body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("Term")));

        assertTrue(requests.stream().anyMatch(r ->
            r.getUpdateTextStyle() != null &&
            Boolean.TRUE.equals(r.getUpdateTextStyle().getTextStyle().getBold())));
    }

    @Test
    void mapTopic_nestedUnorderedList() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body>
                <ul>
                  <li>Parent item
                    <ul>
                      <li>Nested item</li>
                    </ul>
                  </li>
                </ul>
              </body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("Parent item")));

        assertTrue(requests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("Nested item")));

        long bulletCount = requests.stream().filter(r ->
            r.getCreateParagraphBullets() != null &&
            "BULLET_DISC_CIRCLE_SQUARE".equals(
                r.getCreateParagraphBullets().getBulletPreset())).count();
        assertEquals(2, bulletCount);

        assertTrue(requests.stream().anyMatch(r ->
            r.getUpdateParagraphStyle() != null &&
            r.getUpdateParagraphStyle().getParagraphStyle().getIndentStart() != null &&
            r.getUpdateParagraphStyle().getParagraphStyle().getIndentStart()
                .getMagnitude() == 36.0));
    }

    @Test
    void mapTopic_nestedOrderedList() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body>
                <ol>
                  <li>Parent step
                    <ol>
                      <li>Nested step</li>
                    </ol>
                  </li>
                </ol>
              </body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        long numberedCount = requests.stream().filter(r ->
            r.getCreateParagraphBullets() != null &&
            "NUMBERED_DECIMAL_ALPHA_ROMAN".equals(
                r.getCreateParagraphBullets().getBulletPreset())).count();
        assertEquals(2, numberedCount);

        assertTrue(requests.stream().anyMatch(r ->
            r.getUpdateParagraphStyle() != null &&
            r.getUpdateParagraphStyle().getParagraphStyle().getIndentStart() != null &&
            r.getUpdateParagraphStyle().getParagraphStyle().getIndentStart()
                .getMagnitude() == 36.0));
    }

    @Test
    void mapTopic_mixedNestedList() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body>
                <ul>
                  <li>Bullet parent
                    <ol>
                      <li>Numbered nested</li>
                    </ol>
                  </li>
                </ul>
              </body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getCreateParagraphBullets() != null &&
            "BULLET_DISC_CIRCLE_SQUARE".equals(
                r.getCreateParagraphBullets().getBulletPreset())));

        assertTrue(requests.stream().anyMatch(r ->
            r.getCreateParagraphBullets() != null &&
            "NUMBERED_DECIMAL_ALPHA_ROMAN".equals(
                r.getCreateParagraphBullets().getBulletPreset())));
    }

    @Test
    void mapTopic_paragraphInsideListItem() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body>
                <ul>
                  <li>
                    <p>First paragraph</p>
                    <p>Second paragraph</p>
                  </li>
                </ul>
              </body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("First paragraph")));

        assertTrue(requests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("Second paragraph")));

        long bulletCount = requests.stream().filter(r ->
            r.getCreateParagraphBullets() != null).count();
        assertEquals(2, bulletCount);
    }

    @Test
    void mapTopic_threeDeepNesting() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body>
                <ul>
                  <li>Level 0
                    <ul>
                      <li>Level 1
                        <ul>
                          <li>Level 2</li>
                        </ul>
                      </li>
                    </ul>
                  </li>
                </ul>
              </body>
            </topic>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        long bulletCount = requests.stream().filter(r ->
            r.getCreateParagraphBullets() != null).count();
        assertEquals(3, bulletCount);

        assertTrue(requests.stream().anyMatch(r ->
            r.getUpdateParagraphStyle() != null &&
            r.getUpdateParagraphStyle().getParagraphStyle().getIndentStart() != null &&
            r.getUpdateParagraphStyle().getParagraphStyle().getIndentStart()
                .getMagnitude() == 36.0));

        assertTrue(requests.stream().anyMatch(r ->
            r.getUpdateParagraphStyle() != null &&
            r.getUpdateParagraphStyle().getParagraphStyle().getIndentStart() != null &&
            r.getUpdateParagraphStyle().getParagraphStyle().getIndentStart()
                .getMagnitude() == 72.0));
    }

    @Test
    void mapTopic_substeps() throws Exception {
        Document doc = parseXml("""
            <task id="t1">
              <title>T</title>
              <taskbody>
                <steps>
                  <step>
                    <cmd>Main step</cmd>
                    <substeps>
                      <substep><cmd>Sub-action A</cmd></substep>
                      <substep><cmd>Sub-action B</cmd></substep>
                    </substeps>
                  </step>
                </steps>
              </taskbody>
            </task>
            """);

        List<Request> requests = mapper.mapTopic(doc, 0);

        assertTrue(requests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("Main step")));

        assertTrue(requests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("Sub-action A")));

        assertTrue(requests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("Sub-action B")));

        long numberedCount = requests.stream().filter(r ->
            r.getCreateParagraphBullets() != null &&
            "NUMBERED_DECIMAL_ALPHA_ROMAN".equals(
                r.getCreateParagraphBullets().getBulletPreset())).count();
        assertEquals(3, numberedCount);

        long indentCount = requests.stream().filter(r ->
            r.getUpdateParagraphStyle() != null &&
            r.getUpdateParagraphStyle().getParagraphStyle().getIndentStart() != null &&
            r.getUpdateParagraphStyle().getParagraphStyle().getIndentStart()
                .getMagnitude() == 36.0).count();
        assertEquals(2, indentCount);
    }

    @Test
    void resetIndex_resetsToOne() throws Exception {
        Document doc = parseXml("""
            <topic id="t1">
              <title>T</title>
              <body><p>Text.</p></body>
            </topic>
            """);

        mapper.mapTopic(doc, 0);
        mapper.resetIndex();
        List<Request> requests = mapper.mapTopic(doc, 0);

        Request firstInsert = requests.stream()
            .filter(r -> r.getInsertText() != null)
            .findFirst().orElseThrow();
        assertEquals(1, firstInsert.getInsertText().getLocation().getIndex());
    }
}
