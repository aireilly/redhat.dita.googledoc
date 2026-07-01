package org.dita.googledoc;

import com.google.api.services.docs.v1.model.TextStyle;
import com.google.api.services.docs.v1.model.WeightedFontFamily;

import java.util.Map;

public class GoogleDocStyleMapper {

    private static final Map<String, String> NOTE_PREFIXES = Map.ofEntries(
        Map.entry("note", "Note: "),
        Map.entry("warning", "Warning: "),
        Map.entry("caution", "Caution: "),
        Map.entry("danger", "Danger: "),
        Map.entry("tip", "Tip: "),
        Map.entry("important", "Important: "),
        Map.entry("remember", "Remember: "),
        Map.entry("restriction", "Restriction: ")
    );

    public String getNamedStyle(String elementName, int depth) {
        return switch (elementName) {
            case "title" -> headingForDepth(depth);
            case "section" -> headingForDepth(depth + 1);
            default -> "NORMAL_TEXT";
        };
    }

    public TextStyle getTextStyle(String elementName) {
        return switch (elementName) {
            case "b", "uicontrol", "term" ->
                new TextStyle().setBold(true);
            case "i", "varname", "cite" ->
                new TextStyle().setItalic(true);
            case "codeph", "apiname", "option", "parmname", "cmdname", "filepath", "systemoutput",
                 "userinput", "msgnum", "msgph" ->
                new TextStyle().setWeightedFontFamily(
                    new WeightedFontFamily().setFontFamily("Courier New"));
            case "u" ->
                new TextStyle().setUnderline(true);
            case "line-through" ->
                new TextStyle().setStrikethrough(true);
            default -> null;
        };
    }

    public String getNotePrefix(String noteType) {
        return NOTE_PREFIXES.getOrDefault(noteType, "Note: ");
    }

    private String headingForDepth(int depth) {
        int level = Math.min(depth + 1, 6);
        return "HEADING_" + level;
    }
}
