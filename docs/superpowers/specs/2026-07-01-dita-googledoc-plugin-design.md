# DITA OT Google Doc Plugin — Design Spec

## Overview

A DITA OT 4.x plugin (`org.dita.googledoc`) that renders resolved DITA XML content as a Google Doc via the Google Docs API. Transtype: `googledoc`.

## Architecture: Direct DOM-to-API

The plugin hooks into the DITA OT pipeline after standard pre-processing (conref/keyref resolution, DITAVAL filtering). A Java module walks the fully resolved DOM and builds Google Docs API `batchUpdate` requests to create/update a Google Doc in a single pass.

## Plugin Structure

```
org.dita.googledoc/
├── plugin.xml
├── build.gradle
├── src/main/java/org/dita/googledoc/
│   ├── GoogleDocTranstypeModule.java   # Registers transtype with DITA OT
│   ├── GoogleDocPipelineModule.java    # Defines pipeline steps
│   ├── GoogleDocRenderer.java          # Entry point: reads map, orchestrates rendering
│   ├── DitaToGoogleDocMapper.java      # DITA DOM → Google Docs API requests
│   ├── GoogleDocStyleMapper.java       # DITA semantics → Google Docs named styles
│   ├── GoogleDocApiClient.java         # Wraps Docs/Drive API (create, batchUpdate, upload)
│   ├── GoogleAuthProvider.java         # Service account + OAuth2 authentication
│   └── ImageHandler.java              # Uploads images to Drive, returns inline object IDs
├── src/main/resources/
│   └── application.properties
└── src/test/java/
```

## Pipeline Flow

### Stage 1 — Pre-processing (built-in DITA OT)

Standard pre-processing: conref resolution, keyref resolution, DITAVAL filtering, map resolution. Output is fully resolved topic XML and a resolved map in the temp directory.

### Stage 2 — Document Setup (`GoogleDocRenderer`)

- Reads the resolved ditamap to determine topic order and hierarchy
- Authenticates via `GoogleAuthProvider`
- If `googledoc.doc.id` is set, clears and updates that doc; otherwise creates a new Google Doc using the map title as the document name
- Returns the doc ID for rendering

### Stage 3 — Content Rendering (`DitaToGoogleDocMapper`)

Walks topics in map order. Depth-first DOM traversal builds a list of `Request` objects for `batchUpdate`. Requests are accumulated and sent in a single batch call (chunked into ~500-request batches for large docs).

### Element Mapping

| DITA Element | Google Docs API Approach |
|---|---|
| Topic title / section title | `InsertTextRequest` + `UpdateParagraphStyleRequest` with `HEADING_1`–`HEADING_6` (depth-based) |
| Paragraph (`p`) | `InsertTextRequest` + normal paragraph style |
| Bold (`b`) | `UpdateTextStyleRequest` with `bold: true` |
| Italic (`i`) | `UpdateTextStyleRequest` with `italic: true` |
| Monospace (`codeph`) | `UpdateTextStyleRequest` with `Courier New` font |
| Code block (`codeblock`) | `InsertTextRequest` + monospace font style |
| Unordered list (`ul/li`) | `CreateParagraphBulletsRequest` with `BULLET_DISC_CIRCLE_SQUARE` |
| Ordered list (`ol/li`) | `CreateParagraphBulletsRequest` with `NUMBERED_DECIMAL_ALPHA_ROMAN` |
| Table | `InsertTableRequest` (row × col), then populate cells by index |
| Image (`image`) | Upload to Drive via `ImageHandler`, then `InsertInlineImageRequest` |
| Link (`xref` external) | `UpdateTextStyleRequest` with `link` field |
| Link (`xref` internal) | Link to heading bookmark within the same doc |
| Note (`note`) | Indented paragraph with bold prefix label ("**Note:** ", "**Warning:** ", etc.) |
| Definition list (`dl`) | Rendered as bold term + indented definition paragraphs |

### Index Tracking

The Google Docs API is index-based (character offsets). The mapper tracks a running insertion index. All inserts happen sequentially top-to-bottom, keeping index math straightforward.

## Authentication

Two modes via `googledoc.auth.type` parameter:

- **`service`** (default): Service account JSON key from `googledoc.credentials`. Scopes: `docs`, `drive`. No user interaction.
- **`oauth`**: OAuth client ID JSON from `googledoc.credentials`. Browser consent flow on first run, refresh token stored in `googledoc.token.dir`. Subsequent runs reuse token silently.

## Parameters

| Parameter | Required | Default | Description |
|---|---|---|---|
| `googledoc.credentials` | Yes | — | Path to Google credentials JSON |
| `googledoc.auth.type` | No | `service` | `service` or `oauth` |
| `googledoc.doc.id` | No | — | Existing doc ID to update (creates new if omitted) |
| `googledoc.folder.id` | No | — | Drive folder ID for new docs |
| `googledoc.token.dir` | No | `~/.dita-googledoc/tokens` | OAuth token storage |
| `googledoc.image.max.width` | No | `468` | Max image width in points |

## Dependencies

Managed via Gradle, bundled into plugin `lib/`:

- `com.google.api-client:google-api-client`
- `com.google.apis:google-api-services-docs`
- `com.google.apis:google-api-services-drive`
- `com.google.auth:google-auth-library-oauth2-http`

## Error Handling

- **Auth failures**: Fail fast with clear message naming cause and parameter to fix.
- **API errors** (rate limits, transient 5xx): Exponential backoff, 3 retries starting at 1s. On exhaustion, fail with API error and doc ID for inspection.
- **Content errors** (unsupported element, missing image): Log warning, render fallback (plain text with `[unsupported: <element>]` marker). Build completes with warnings.

## Testing

- **Unit tests** for `DitaToGoogleDocMapper`: DITA DOM fragments → assert correct `Request` lists. Offline, no API calls.
- **Unit tests** for `GoogleDocStyleMapper`: Element → style mapping verification.
- **Integration test**: End-to-end build of sample ditamap → verify Google Doc creation. Gated behind profile flag for offline builds.
- **Sample data**: Small ditamap with headings, tables, images, lists, code blocks, xrefs, notes.
