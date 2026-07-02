# DITA OT Google Doc Plugin

> **Caveat:** This plugin was generated with LLM assistance and is likely
> broken in non-trivial ways. Treat it as a proof of concept rather than
> production-ready software. Contributions and bug reports are welcome.

A DITA Open Toolkit plugin that renders DITA XML content as a Google Doc.

## Requirements

- DITA OT 4.2 or later
- Java 17 or later
- Google Cloud project with Docs API and Drive API enabled

## Installation

Build the plugin:

    ./gradlew bundlePlugin

Install into DITA OT:

    dita install build/distributions/redhat.dita.googledoc-1.0.0.zip

## Usage

If you're already logged in with `gcloud`, no extra configuration is needed:

    dita --input=mymap.ditamap --format=googledoc

To update an existing document instead of creating a new one:

    dita --input=mymap.ditamap --format=googledoc \
      --googledoc.doc.id=1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgVE2upms

## Authentication

The plugin authenticates automatically using a cascade of methods (tried in order):

1. **gcloud CLI** (preferred) — uses your existing `gcloud auth` session. No files needed.
2. **Application Default Credentials** — honors the `GOOGLE_APPLICATION_CREDENTIALS` environment variable or credentials configured via `gcloud auth application-default login`.
3. **Interactive login** — if gcloud is installed but no session is active, opens a browser for Google sign-in.
4. **Explicit credentials file** — pass a service account or OAuth client JSON directly.

### Zero-config (gcloud)

If you have the [Google Cloud CLI](https://cloud.google.com/sdk/docs/install) installed and authenticated:

    gcloud auth login --enable-gdrive-access
    dita --input=mymap.ditamap --format=googledoc

### Service account (CI/CD)

    dita --input=mymap.ditamap --format=googledoc \
      --googledoc.auth.type=service \
      --googledoc.credentials=/path/to/service-account.json

### OAuth 2.0 (interactive, with refresh token)

    dita --input=mymap.ditamap --format=googledoc \
      --googledoc.auth.type=oauth \
      --googledoc.credentials=/path/to/client_secret.json

Opens a browser on first run. Stores a refresh token in `~/.dita-googledoc/tokens/` for subsequent runs.

## Parameters

| Parameter | Required | Default | Description |
|---|---|---|---|
| `googledoc.credentials` | No | — | Path to Google credentials JSON. If omitted, uses gcloud/ADC. |
| `googledoc.auth.type` | No | `auto` | `auto`, `service`, or `oauth`. `auto` tries gcloud, ADC, then interactive login. |
| `googledoc.doc.id` | No | — | Existing doc ID to update |
| `googledoc.folder.id` | No | — | Drive folder for new docs |
| `googledoc.token.dir` | No | `~/.dita-googledoc/tokens` | OAuth token storage |
| `googledoc.image.max.width` | No | `468` | Max image width in points |

## DITAVAL Filtering

The plugin supports DITAVAL filtering via the standard DITA-OT `--filter` parameter:

    dita --input=mymap.ditamap --format=googledoc \
      --filter=path/to/conditions.ditaval

Filtering (include/exclude) is handled by DITA-OT's preprocessing pipeline before
the Google Doc rendering stage. All standard DITAVAL filtering attributes
(audience, platform, product, etc.) are supported.

Note: DITAVAL flagging (visual styling of content based on conditions) is not
currently supported. Only include/exclude filtering is applied.

## Supported DITA Elements

- Topics (topic, concept, task, reference) with nested hierarchy
- Abstract and short description
- Sections and examples with titles
- Paragraphs, inline formatting (bold, italic, underline, monospace)
- Ordered and unordered lists with nesting support
- Definition lists
- Tables (CALS and simpletable models)
- Choice tables
- Code blocks and screen output
- Long quotes
- Images
- Cross-references (external links)
- Footnotes (inline rendering)
- Notes (note, warning, caution, danger, tip, important)
- Hazard statements (danger, warning, caution, notice)
- Task elements: prerequisites, context, steps, substeps, choices, result, post-requisites
- Related links

## Development

Run tests:

    ./gradlew test

Build:

    ./gradlew bundlePlugin
