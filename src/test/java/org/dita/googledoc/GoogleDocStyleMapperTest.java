package org.dita.googledoc;

import com.google.api.services.docs.v1.model.TextStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class GoogleDocStyleMapperTest {

    private final GoogleDocStyleMapper mapper = new GoogleDocStyleMapper();

    @ParameterizedTest
    @CsvSource({
        "title, 0, HEADING_1",
        "title, 1, HEADING_2",
        "title, 2, HEADING_3",
        "title, 3, HEADING_4",
        "title, 4, HEADING_5",
        "title, 5, HEADING_6",
        "title, 6, HEADING_6",
        "section, 0, HEADING_2",
        "section, 1, HEADING_3",
        "p, 0, NORMAL_TEXT",
        "unknown, 0, NORMAL_TEXT"
    })
    void getNamedStyle_returnsCorrectStyle(String element, int depth, String expected) {
        assertEquals(expected, mapper.getNamedStyle(element, depth));
    }

    @Test
    void getTextStyle_bold() {
        TextStyle style = mapper.getTextStyle("b");
        assertTrue(style.getBold());
    }

    @Test
    void getTextStyle_italic() {
        TextStyle style = mapper.getTextStyle("i");
        assertTrue(style.getItalic());
    }

    @Test
    void getTextStyle_codeph_setsMonospace() {
        TextStyle style = mapper.getTextStyle("codeph");
        assertEquals("Courier New", style.getWeightedFontFamily().getFontFamily());
    }

    @Test
    void getTextStyle_unknownElement_returnsNull() {
        assertNull(mapper.getTextStyle("p"));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "note; 'Note: '",
        "warning; 'Warning: '",
        "caution; 'Caution: '",
        "danger; 'Danger: '",
        "tip; 'Tip: '",
        "important; 'Important: '",
        "remember; 'Remember: '",
        "restriction; 'Restriction: '",
        "unknown; 'Note: '"
    }, delimiter = ';', quoteCharacter = '\'')
    void getNotePrefix_returnsCorrectPrefix(String noteType, String expected) {
        assertEquals(expected, mapper.getNotePrefix(noteType));
    }
}
