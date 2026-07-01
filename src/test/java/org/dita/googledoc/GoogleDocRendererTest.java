package org.dita.googledoc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GoogleDocRendererTest {

    @Test
    void parseTopicRefs_extractsHrefsInOrder(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("test.ditamap");
        Files.writeString(mapFile, """
            <?xml version="1.0" encoding="UTF-8"?>
            <map>
              <title>Test Map</title>
              <topicref href="topic1.dita">
                <topicref href="topic1a.dita"/>
              </topicref>
              <topicref href="topic2.dita"/>
            </map>
            """);

        List<GoogleDocRenderer.TopicRef> refs =
            GoogleDocRenderer.parseTopicRefs(mapFile.toString());

        assertEquals(3, refs.size());
        assertEquals("topic1.dita", refs.get(0).href());
        assertEquals(0, refs.get(0).depth());
        assertEquals("topic1a.dita", refs.get(1).href());
        assertEquals(1, refs.get(1).depth());
        assertEquals("topic2.dita", refs.get(2).href());
        assertEquals(0, refs.get(2).depth());
    }

    @Test
    void parseTopicRefs_extractsMapTitle(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("test.ditamap");
        Files.writeString(mapFile, """
            <?xml version="1.0" encoding="UTF-8"?>
            <map>
              <title>My Doc Title</title>
              <topicref href="t.dita"/>
            </map>
            """);

        String title = GoogleDocRenderer.parseMapTitle(mapFile.toString());
        assertEquals("My Doc Title", title);
    }

    @Test
    void parseTopicRefs_emptyMap(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("empty.ditamap");
        Files.writeString(mapFile, """
            <?xml version="1.0" encoding="UTF-8"?>
            <map>
              <title>Empty</title>
            </map>
            """);

        List<GoogleDocRenderer.TopicRef> refs =
            GoogleDocRenderer.parseTopicRefs(mapFile.toString());
        assertTrue(refs.isEmpty());
    }
}
