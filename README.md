# DITA OT Google Doc Plugin

A DITA Open Toolkit plugin that renders DITA XML content as a Google Doc.

## Requirements

- DITA OT 4.2 or later
- Java 17 or later
- Google Cloud project with Docs API and Drive API enabled
- Service account key or OAuth 2.0 client credentials

## Installation

Build the plugin:

    ./gradlew bundlePlugin

Install into DITA OT:

    dita install build/distributions/org.dita.googledoc-1.0.0.zip

## Usage

    dita --input=mymap.ditamap --format=googledoc \
      --googledoc.credentials=/path/to/credentials.json

## Parameters

| Parameter | Required | Default | Description |
|---|---|---|---|
| `googledoc.credentials` | Yes | — | Path to Google credentials JSON |
| `googledoc.auth.type` | No | `service` | `service` or `oauth` |
| `googledoc.doc.id` | No | — | Existing doc ID to update |
| `googledoc.folder.id` | No | — | Drive folder for new docs |
| `googledoc.token.dir` | No | `~/.dita-googledoc/tokens` | OAuth token storage |
| `googledoc.image.max.width` | No | `468` | Max image width in points |

## Authentication

### Service Account (recommended for CI/CD)

1. Create a service account in Google Cloud Console
2. Enable the Google Docs API and Google Drive API
3. Download the JSON key file
4. Pass the path via `--googledoc.credentials`

### OAuth 2.0 (for local use)

1. Create OAuth 2.0 client credentials in Google Cloud Console
2. Download the client secrets JSON
3. Run with `--googledoc.auth.type=oauth --googledoc.credentials=/path/to/client_secret.json`
4. A browser window opens for consent on first run

## Supported DITA Elements

- Topics (topic, concept, task, reference) with nested hierarchy
- Sections with titles
- Paragraphs, inline formatting (bold, italic, underline, monospace)
- Ordered and unordered lists
- Definition lists
- Tables (CALS table model)
- Code blocks
- Images
- Cross-references (external links)
- Notes (note, warning, caution, danger, tip, important)
- Related links
- Task steps

## Development

Run tests:

    ./gradlew test

Run with live Google API (requires credentials):

    GOOGLE_CREDENTIALS_PATH=/path/to/creds.json ./gradlew test

Build:

    ./gradlew bundlePlugin
