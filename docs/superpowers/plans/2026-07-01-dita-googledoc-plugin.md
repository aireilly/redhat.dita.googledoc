# DITA OT Google Doc Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a DITA OT 4.x plugin that renders resolved DITA XML as a Google Doc via the Google Docs API, with full support for topics, maps, tables, images, lists, cross-references, and conref/keyref resolution.

**Architecture:** Direct DOM-to-API — the plugin hooks into the DITA OT pipeline after pre-processing, walks the resolved DOM, and builds Google Docs API `batchUpdate` requests. A single Java module translates DITA semantics to Google Docs named styles and structural elements.

**Tech Stack:** Java 17+, DITA OT 4.x, Google Docs API v1, Google Drive API v3, Google Auth Library, Gradle (for build/dependency management), JUnit 5 + Mockito (testing)

## Global Constraints

- DITA OT 4.x (4.2+) — use `plugin.xml` descriptor, Ant `build.xml` for pipeline integration
- Java 17+ — match DITA OT 4.x minimum
- Plugin ID: `org.dita.googledoc`
- Transtype name: `googledoc`
- Ant target: `dita2googledoc`
- Google Docs API write quota: 60 requests/minute/user — batch calls to stay under limit
- Google Docs API named styles: `NORMAL_TEXT`, `HEADING_1` through `HEADING_6`
- All Google API dependencies bundled in plugin `lib/` directory

---

### Task 1: Project Scaffold and Build Configuration

**Files:**
- Create: `build.gradle`
- Create: `settings.gradle`
- Create: `plugin.xml`
- Create: `build.xml`
- Create: `src/main/java/org/dita/googledoc/package-info.java`
- Create: `src/test/java/org/dita/googledoc/package-info.java`
- Create: `.gitignore`

**Interfaces:**
- Consumes: nothing
- Produces: Compilable Gradle project with DITA OT and Google API dependencies on classpath. Plugin descriptor that registers `googledoc` transtype with parameters `googledoc.credentials`, `googledoc.auth.type`, `googledoc.doc.id`, `googledoc.folder.id`, `googledoc.token.dir`, `googledoc.image.max.width`.

- [ ] **Step 1: Create `.gitignore`**

```gitignore
build/
.gradle/
lib/
*.class
*.jar
.idea/
*.iml
tokens/
```

- [ ] **Step 2: Create `settings.gradle`**

```gradle
rootProject.name = 'org.dita.googledoc'
```

- [ ] **Step 3: Create `build.gradle`**

```gradle
plugins {
    id 'java'
}

group = 'org.dita'
version = '1.0.0'

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly 'org.dita-ot:dost:4.2.0'

    implementation 'com.google.api-client:google-api-client:2.9.0'
    implementation 'com.google.apis:google-api-services-docs:v1-rev20260427-2.0.0'
    implementation 'com.google.apis:google-api-services-drive:v3-rev20260428-2.0.0'
    implementation 'com.google.auth:google-auth-library-oauth2-http:1.48.0'
    implementation 'com.google.oauth-client:google-oauth-client-jetty:1.39.0'

    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testImplementation 'org.mockito:mockito-core:5.14.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
}

tasks.register('copyDeps', Copy) {
    from configurations.runtimeClasspath
    into layout.buildDirectory.dir('lib')
}

tasks.register('bundlePlugin', Zip) {
    dependsOn 'jar', 'copyDeps'
    archiveBaseName = 'org.dita.googledoc'
    from('.') {
        include 'plugin.xml'
        include 'build.xml'
    }
    from(layout.buildDirectory.dir('libs')) {
        into 'lib'
    }
    from(layout.buildDirectory.dir('lib')) {
        into 'lib'
    }
}
```

- [ ] **Step 4: Create `plugin.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<plugin id="org.dita.googledoc">
  <require plugin="org.dita.base"/>

  <feature extension="dita.conductor.lib.import" file="lib/org.dita.googledoc-1.0.0.jar"/>
  <feature extension="ant.import" file="build.xml"/>

  <transtype name="googledoc" desc="Google Docs output">
    <param name="googledoc.credentials"
           type="file"
           desc="Path to Google credentials JSON (service account or OAuth client)"
           required="true"/>
    <param name="googledoc.auth.type"
           type="enum"
           desc="Authentication type">
      <val default="true">service</val>
      <val>oauth</val>
    </param>
    <param name="googledoc.doc.id"
           type="string"
           desc="Existing Google Doc ID to update (creates new if omitted)"/>
    <param name="googledoc.folder.id"
           type="string"
           desc="Google Drive folder ID for new docs"/>
    <param name="googledoc.token.dir"
           type="string"
           desc="OAuth token storage directory"/>
    <param name="googledoc.image.max.width"
           type="string"
           desc="Max image width in points (default: 468)"/>
  </transtype>
</plugin>
```

- [ ] **Step 5: Create `build.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project name="dita2googledoc">

  <target name="dita2googledoc.init">
    <property name="googledoc.image.max.width" value="468"/>
    <property name="googledoc.token.dir"
              value="${user.home}/.dita-googledoc/tokens"/>
  </target>

  <target name="dita2googledoc"
          depends="build-init,preprocess2,dita2googledoc.init">
    <java classname="org.dita.googledoc.GoogleDocRenderer"
          classpathref="dost.class.path"
          failonerror="true"
          fork="true">
      <arg value="${dita.temp.dir}"/>
      <arg value="${args.input}"/>
      <arg value="${googledoc.credentials}"/>
      <arg value="${googledoc.auth.type}"/>
      <arg value="${googledoc.doc.id}"/>
      <arg value="${googledoc.folder.id}"/>
      <arg value="${googledoc.token.dir}"/>
      <arg value="${googledoc.image.max.width}"/>
    </java>
  </target>

</project>
```

- [ ] **Step 6: Create package-info files**

`src/main/java/org/dita/googledoc/package-info.java`:
```java
package org.dita.googledoc;
```

`src/test/java/org/dita/googledoc/package-info.java`:
```java
package org.dita.googledoc;
```

- [ ] **Step 7: Initialize git and verify build compiles**

```bash
cd /home/aireilly/redhat.dita.gdoc
git init
gradle wrapper --gradle-version 8.12
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL (no source files yet, but dependencies resolve)

- [ ] **Step 8: Commit**

```bash
git add .gitignore settings.gradle build.gradle plugin.xml build.xml \
  src/main/java/org/dita/googledoc/package-info.java \
  src/test/java/org/dita/googledoc/package-info.java \
  gradlew gradlew.bat gradle/
git commit -m "feat: scaffold DITA OT googledoc plugin with build config"
```

---

### Task 2: GoogleAuthProvider — Authentication

**Files:**
- Create: `src/main/java/org/dita/googledoc/GoogleAuthProvider.java`
- Create: `src/test/java/org/dita/googledoc/GoogleAuthProviderTest.java`

**Interfaces:**
- Consumes: credentials file path (String), auth type (`"service"` or `"oauth"`), token dir (String)
- Produces: `GoogleAuthProvider.getCredential(String credentialsPath, String authType, String tokenDir): HttpRequestInitializer` — returns a configured `HttpRequestInitializer` for use with Google API clients

- [ ] **Step 1: Write the failing tests**

```java
package org.dita.googledoc;

import com.google.api.client.http.HttpRequestInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GoogleAuthProviderTest {

    @Test
    void getCredential_serviceAccount_returnsInitializer(@TempDir Path tempDir) throws Exception {
        Path creds = tempDir.resolve("service-account.json");
        // Write a test service account JSON fixture at runtime
        // (see GoogleAuthProviderTest for the actual content —
        //  the private_key field must be a valid RSA key for
        //  ServiceAccountCredentials.fromStream() to parse it)
        Files.writeString(creds, TestFixtures.SERVICE_ACCOUNT_JSON);

        HttpRequestInitializer initializer =
            GoogleAuthProvider.getCredential(creds.toString(), "service", null);

        assertNotNull(initializer);
    }

    @Test
    void getCredential_invalidAuthType_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            GoogleAuthProvider.getCredential("/nonexistent", "invalid", null));
    }

    @Test
    void getCredential_missingFile_throws() {
        assertThrows(IOException.class, () ->
            GoogleAuthProvider.getCredential("/nonexistent/creds.json", "service", null));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "org.dita.googledoc.GoogleAuthProviderTest"
```

Expected: FAIL — `GoogleAuthProvider` class does not exist

- [ ] **Step 3: Implement `GoogleAuthProvider`**

```java
package org.dita.googledoc;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.util.store.FileDataStoreFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

public final class GoogleAuthProvider {

    private static final List<String> SCOPES = List.of(
        "https://www.googleapis.com/auth/documents",
        "https://www.googleapis.com/auth/drive"
    );

    private GoogleAuthProvider() {}

    public static HttpRequestInitializer getCredential(
            String credentialsPath, String authType, String tokenDir)
            throws IOException, GeneralSecurityException {
        return switch (authType) {
            case "service" -> serviceAccountCredential(credentialsPath);
            case "oauth" -> oauthCredential(credentialsPath, tokenDir);
            default -> throw new IllegalArgumentException(
                "Invalid auth type: " + authType + ". Must be 'service' or 'oauth'.");
        };
    }

    private static HttpRequestInitializer serviceAccountCredential(String credentialsPath)
            throws IOException {
        try (FileInputStream stream = new FileInputStream(credentialsPath)) {
            GoogleCredentials credentials = ServiceAccountCredentials
                .fromStream(stream)
                .createScoped(SCOPES);
            return new HttpCredentialsAdapter(credentials);
        }
    }

    private static HttpRequestInitializer oauthCredential(
            String credentialsPath, String tokenDir)
            throws IOException, GeneralSecurityException {
        String effectiveTokenDir = (tokenDir != null && !tokenDir.isEmpty())
            ? tokenDir
            : Path.of(System.getProperty("user.home"), ".dita-googledoc", "tokens").toString();

        GoogleClientSecrets clientSecrets;
        try (var reader = new InputStreamReader(new FileInputStream(credentialsPath))) {
            clientSecrets = GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), reader);
        }

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                clientSecrets,
                SCOPES)
            .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(effectiveTokenDir)))
            .setAccessType("offline")
            .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
            .setPort(8888)
            .build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver)
            .authorize("user");
        return credential;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "org.dita.googledoc.GoogleAuthProviderTest"
```

Expected: 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/dita/googledoc/GoogleAuthProvider.java \
  src/test/java/org/dita/googledoc/GoogleAuthProviderTest.java
git commit -m "feat: add GoogleAuthProvider with service account and OAuth2 support"
```

---

### Task 3: GoogleDocApiClient — API Wrapper

**Files:**
- Create: `src/main/java/org/dita/googledoc/GoogleDocApiClient.java`
- Create: `src/test/java/org/dita/googledoc/GoogleDocApiClientTest.java`

**Interfaces:**
- Consumes: `GoogleAuthProvider.getCredential(...)` → `HttpRequestInitializer`
- Produces:
  - `GoogleDocApiClient(HttpRequestInitializer credential)`
  - `String createDocument(String title, String folderId)` — creates a new Google Doc, returns doc ID
  - `void clearDocument(String docId)` — deletes all content from an existing doc
  - `void executeBatchUpdate(String docId, List<Request> requests)` — sends requests in chunks of 500
  - `String uploadImage(String filePath, String folderId)` — uploads image to Drive, returns web content link URI
  - `Docs getDocsService()` — returns the Docs service for direct use if needed
  - `Drive getDriveService()` — returns the Drive service for direct use if needed

- [ ] **Step 1: Write the failing tests**

```java
package org.dita.googledoc;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.drive.Drive;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GoogleDocApiClientTest {

    @Test
    void constructor_createsServicesFromCredential() {
        HttpRequestInitializer mockInit = request -> {};

        GoogleDocApiClient client = new GoogleDocApiClient(mockInit);

        assertNotNull(client.getDocsService());
        assertNotNull(client.getDriveService());
    }

    @Test
    void chunkRequests_splitsAtLimit() {
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < 1200; i++) {
            requests.add(new Request());
        }

        List<List<Request>> chunks = GoogleDocApiClient.chunkRequests(requests, 500);

        assertEquals(3, chunks.size());
        assertEquals(500, chunks.get(0).size());
        assertEquals(500, chunks.get(1).size());
        assertEquals(200, chunks.get(2).size());
    }

    @Test
    void chunkRequests_singleChunkWhenUnderLimit() {
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            requests.add(new Request());
        }

        List<List<Request>> chunks = GoogleDocApiClient.chunkRequests(requests, 500);

        assertEquals(1, chunks.size());
        assertEquals(100, chunks.get(0).size());
    }

    @Test
    void chunkRequests_emptyList() {
        List<List<Request>> chunks = GoogleDocApiClient.chunkRequests(List.of(), 500);
        assertTrue(chunks.isEmpty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "org.dita.googledoc.GoogleDocApiClientTest"
```

Expected: FAIL — `GoogleDocApiClient` class does not exist

- [ ] **Step 3: Implement `GoogleDocApiClient`**

```java
package org.dita.googledoc;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.*;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import java.io.IOException;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class GoogleDocApiClient {

    private static final String APP_NAME = "DITA-OT-GoogleDoc-Plugin";
    private static final int BATCH_CHUNK_SIZE = 500;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private final Docs docsService;
    private final Drive driveService;

    public GoogleDocApiClient(HttpRequestInitializer credential) {
        try {
            var transport = GoogleNetHttpTransport.newTrustedTransport();
            var jsonFactory = GsonFactory.getDefaultInstance();

            this.docsService = new Docs.Builder(transport, jsonFactory, credential)
                .setApplicationName(APP_NAME)
                .build();

            this.driveService = new Drive.Builder(transport, jsonFactory, credential)
                .setApplicationName(APP_NAME)
                .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to initialize Google API clients", e);
        }
    }

    public String createDocument(String title, String folderId) throws IOException {
        if (folderId != null && !folderId.isEmpty()) {
            File fileMetadata = new File();
            fileMetadata.setName(title);
            fileMetadata.setMimeType("application/vnd.google-apps.document");
            fileMetadata.setParents(List.of(folderId));
            File created = driveService.files().create(fileMetadata)
                .setFields("id")
                .execute();
            return created.getId();
        }
        Document doc = new Document().setTitle(title);
        Document created = docsService.documents().create(doc).execute();
        return created.getDocumentId();
    }

    public void clearDocument(String docId) throws IOException {
        Document doc = docsService.documents().get(docId).execute();
        Body body = doc.getBody();
        if (body == null || body.getContent() == null) return;

        int endIndex = 1;
        for (StructuralElement element : body.getContent()) {
            if (element.getEndIndex() != null && element.getEndIndex() > endIndex) {
                endIndex = element.getEndIndex();
            }
        }

        if (endIndex > 2) {
            Request deleteRequest = new Request().setDeleteContentRange(
                new DeleteContentRangeRequest().setRange(
                    new Range().setStartIndex(1).setEndIndex(endIndex - 1)));
            docsService.documents().batchUpdate(docId,
                new BatchUpdateDocumentRequest().setRequests(List.of(deleteRequest)))
                .execute();
        }
    }

    public void executeBatchUpdate(String docId, List<Request> requests) throws IOException {
        if (requests.isEmpty()) return;

        for (List<Request> chunk : chunkRequests(requests, BATCH_CHUNK_SIZE)) {
            executeWithRetry(docId, chunk);
        }
    }

    private void executeWithRetry(String docId, List<Request> requests) throws IOException {
        long backoff = INITIAL_BACKOFF_MS;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                docsService.documents().batchUpdate(docId,
                    new BatchUpdateDocumentRequest().setRequests(requests))
                    .execute();
                return;
            } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
                if (e.getStatusCode() == 429 || e.getStatusCode() >= 500) {
                    if (attempt == MAX_RETRIES - 1) throw e;
                    try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted during retry backoff", ie);
                    }
                    backoff *= 2;
                } else {
                    throw e;
                }
            }
        }
    }

    public String uploadImage(String filePath, String folderId) throws IOException {
        java.io.File imageFile = new java.io.File(filePath);
        String mimeType = URLConnection.guessContentTypeFromName(imageFile.getName());
        if (mimeType == null) mimeType = "application/octet-stream";

        File fileMetadata = new File();
        fileMetadata.setName(imageFile.getName());
        if (folderId != null && !folderId.isEmpty()) {
            fileMetadata.setParents(List.of(folderId));
        }

        FileContent mediaContent = new FileContent(mimeType, imageFile);
        File uploaded = driveService.files().create(fileMetadata, mediaContent)
            .setFields("id,webContentLink")
            .execute();

        driveService.permissions().create(uploaded.getId(),
            new com.google.api.services.drive.model.Permission()
                .setType("anyone")
                .setRole("reader"))
            .execute();

        return "https://drive.google.com/uc?id=" + uploaded.getId();
    }

    public Docs getDocsService() {
        return docsService;
    }

    public Drive getDriveService() {
        return driveService;
    }

    static List<List<Request>> chunkRequests(List<Request> requests, int chunkSize) {
        if (requests.isEmpty()) return List.of();
        List<List<Request>> chunks = new ArrayList<>();
        for (int i = 0; i < requests.size(); i += chunkSize) {
            chunks.add(requests.subList(i, Math.min(i + chunkSize, requests.size())));
        }
        return chunks;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "org.dita.googledoc.GoogleDocApiClientTest"
```

Expected: 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/dita/googledoc/GoogleDocApiClient.java \
  src/test/java/org/dita/googledoc/GoogleDocApiClientTest.java
git commit -m "feat: add GoogleDocApiClient with batch update chunking and retry"
```

---

### Task 4: GoogleDocStyleMapper — Semantic Style Mapping

**Files:**
- Create: `src/main/java/org/dita/googledoc/GoogleDocStyleMapper.java`
- Create: `src/test/java/org/dita/googledoc/GoogleDocStyleMapperTest.java`

**Interfaces:**
- Consumes: DITA element names (String), nesting depth (int)
- Produces:
  - `String getNamedStyle(String elementName, int depth)` — returns Google Docs named style type (`"HEADING_1"` through `"HEADING_6"`, `"NORMAL_TEXT"`)
  - `TextStyle getTextStyle(String elementName)` — returns a `TextStyle` object for inline elements (bold, italic, monospace, etc.)
  - `String getNotePrefix(String noteType)` — returns prefix label for note elements (`"Note: "`, `"Warning: "`, etc.)

- [ ] **Step 1: Write the failing tests**

```java
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
    @CsvSource({
        "note, Note: ",
        "warning, Warning: ",
        "caution, Caution: ",
        "danger, Danger: ",
        "tip, Tip: ",
        "important, Important: ",
        "remember, Remember: ",
        "restriction, Restriction: ",
        "unknown, Note: "
    })
    void getNotePrefix_returnsCorrectPrefix(String noteType, String expected) {
        assertEquals(expected, mapper.getNotePrefix(noteType));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "org.dita.googledoc.GoogleDocStyleMapperTest"
```

Expected: FAIL — `GoogleDocStyleMapper` class does not exist

- [ ] **Step 3: Implement `GoogleDocStyleMapper`**

```java
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "org.dita.googledoc.GoogleDocStyleMapperTest"
```

Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/dita/googledoc/GoogleDocStyleMapper.java \
  src/test/java/org/dita/googledoc/GoogleDocStyleMapperTest.java
git commit -m "feat: add GoogleDocStyleMapper for DITA-to-Google-Docs style mapping"
```

---

### Task 5: ImageHandler — Image Upload

**Files:**
- Create: `src/main/java/org/dita/googledoc/ImageHandler.java`
- Create: `src/test/java/org/dita/googledoc/ImageHandlerTest.java`

**Interfaces:**
- Consumes: `GoogleDocApiClient.uploadImage(String filePath, String folderId)`, temp directory path (String), max image width (double)
- Produces:
  - `ImageHandler(GoogleDocApiClient apiClient, String tempDir, String folderId, double maxWidthPt)`
  - `ImageInfo resolveImage(String href)` — resolves a DITA image href relative to tempDir, uploads to Drive, returns an `ImageInfo` record
  - `record ImageInfo(String uri, double widthPt, double heightPt)` — image URI and dimensions

- [ ] **Step 1: Write the failing tests**

```java
package org.dita.googledoc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImageHandlerTest {

    @Test
    void imageInfo_holdsValues() {
        ImageHandler.ImageInfo info = new ImageHandler.ImageInfo(
            "https://example.com/img.png", 200.0, 100.0);
        assertEquals("https://example.com/img.png", info.uri());
        assertEquals(200.0, info.widthPt());
        assertEquals(100.0, info.heightPt());
    }

    @Test
    void calculateDimensions_scalesDown(@TempDir Path tempDir) throws IOException {
        BufferedImage img = new BufferedImage(1000, 500, BufferedImage.TYPE_INT_RGB);
        Path imgPath = tempDir.resolve("wide.png");
        ImageIO.write(img, "png", imgPath.toFile());

        double[] dims = ImageHandler.calculateDimensions(imgPath.toString(), 468.0);

        assertEquals(468.0, dims[0], 0.1);
        assertEquals(234.0, dims[1], 0.1);
    }

    @Test
    void calculateDimensions_noScaleWhenSmall(@TempDir Path tempDir) throws IOException {
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Path imgPath = tempDir.resolve("small.png");
        ImageIO.write(img, "png", imgPath.toFile());

        double[] dims = ImageHandler.calculateDimensions(imgPath.toString(), 468.0);

        assertEquals(150.0, dims[0], 0.1);
        assertEquals(75.0, dims[1], 0.1);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "org.dita.googledoc.ImageHandlerTest"
```

Expected: FAIL — `ImageHandler` class does not exist

- [ ] **Step 3: Implement `ImageHandler`**

```java
package org.dita.googledoc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public class ImageHandler {

    private static final double PX_TO_PT = 0.75;

    private final GoogleDocApiClient apiClient;
    private final String tempDir;
    private final String folderId;
    private final double maxWidthPt;

    public record ImageInfo(String uri, double widthPt, double heightPt) {}

    public ImageHandler(GoogleDocApiClient apiClient, String tempDir,
                        String folderId, double maxWidthPt) {
        this.apiClient = apiClient;
        this.tempDir = tempDir;
        this.folderId = folderId;
        this.maxWidthPt = maxWidthPt;
    }

    public ImageInfo resolveImage(String href) throws IOException {
        String absolutePath = Path.of(tempDir).resolve(href).toString();

        double[] dims = calculateDimensions(absolutePath, maxWidthPt);
        String uri = apiClient.uploadImage(absolutePath, folderId);

        return new ImageInfo(uri, dims[0], dims[1]);
    }

    static double[] calculateDimensions(String imagePath, double maxWidthPt) throws IOException {
        BufferedImage img = ImageIO.read(new java.io.File(imagePath));
        if (img == null) {
            return new double[]{maxWidthPt, maxWidthPt};
        }

        double widthPt = img.getWidth() * PX_TO_PT;
        double heightPt = img.getHeight() * PX_TO_PT;

        if (widthPt > maxWidthPt) {
            double scale = maxWidthPt / widthPt;
            widthPt = maxWidthPt;
            heightPt = heightPt * scale;
        }

        return new double[]{widthPt, heightPt};
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "org.dita.googledoc.ImageHandlerTest"
```

Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/dita/googledoc/ImageHandler.java \
  src/test/java/org/dita/googledoc/ImageHandlerTest.java
git commit -m "feat: add ImageHandler for image upload and dimension scaling"
```

---

### Task 6: DitaToGoogleDocMapper — Core DOM-to-API Translation

**Files:**
- Create: `src/main/java/org/dita/googledoc/DitaToGoogleDocMapper.java`
- Create: `src/test/java/org/dita/googledoc/DitaToGoogleDocMapperTest.java`

**Interfaces:**
- Consumes: `GoogleDocStyleMapper`, `ImageHandler` (nullable for tests)
- Produces:
  - `DitaToGoogleDocMapper(GoogleDocStyleMapper styleMapper, ImageHandler imageHandler)`
  - `List<Request> mapTopic(org.w3c.dom.Document topicDoc, int topicDepth)` — converts a resolved DITA topic DOM into a list of Google Docs API requests
  - Internal index tracking: starts at 1 (Google Docs body starts at index 1), accumulates across calls to `mapTopic()`
  - `void resetIndex()` — resets the insertion index to 1 (for use when starting a fresh document)

- [ ] **Step 1: Write the failing tests**

```java
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "org.dita.googledoc.DitaToGoogleDocMapperTest"
```

Expected: FAIL — `DitaToGoogleDocMapper` class does not exist

- [ ] **Step 3: Implement `DitaToGoogleDocMapper`**

```java
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

    public DitaToGoogleDocMapper(GoogleDocStyleMapper styleMapper, ImageHandler imageHandler) {
        this.styleMapper = styleMapper;
        this.imageHandler = imageHandler;
    }

    public void resetIndex() {
        this.currentIndex = 1;
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

    private void processListItem(Element element, int topicDepth,
                                 List<Request> requests, String listType) {
        int startIndex = currentIndex;
        processInlineChildren(element, requests);
        insertText("\n", requests);

        String bulletPreset = "ol".equals(listType)
            ? "NUMBERED_DECIMAL_ALPHA_ROMAN"
            : "BULLET_DISC_CIRCLE_SQUARE";

        requests.add(new Request().setCreateParagraphBullets(
            new CreateParagraphBulletsRequest()
                .setRange(new Range().setStartIndex(startIndex).setEndIndex(currentIndex - 1))
                .setBulletPreset(bulletPreset)));
    }

    private void processStep(Element element, int topicDepth,
                             List<Request> requests, String listType) {
        int startIndex = currentIndex;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                if ("cmd".equals(childElement.getTagName())) {
                    processInlineChildren(childElement, requests);
                } else if ("info".equals(childElement.getTagName()) ||
                           "stepresult".equals(childElement.getTagName())) {
                    insertText(" ", requests);
                    processInlineChildren(childElement, requests);
                }
            }
        }
        insertText("\n", requests);

        requests.add(new Request().setCreateParagraphBullets(
            new CreateParagraphBulletsRequest()
                .setRange(new Range().setStartIndex(startIndex).setEndIndex(currentIndex - 1))
                .setBulletPreset("NUMBERED_DECIMAL_ALPHA_ROMAN")));
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
                currentIndex += 2;
            }
        }

        currentIndex += 1;
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "org.dita.googledoc.DitaToGoogleDocMapperTest"
```

Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/dita/googledoc/DitaToGoogleDocMapper.java \
  src/test/java/org/dita/googledoc/DitaToGoogleDocMapperTest.java
git commit -m "feat: add DitaToGoogleDocMapper for DITA DOM to Google Docs API translation"
```

---

### Task 7: GoogleDocRenderer — Main Entry Point

**Files:**
- Create: `src/main/java/org/dita/googledoc/GoogleDocRenderer.java`
- Create: `src/test/java/org/dita/googledoc/GoogleDocRendererTest.java`

**Interfaces:**
- Consumes: `GoogleAuthProvider.getCredential(...)`, `GoogleDocApiClient(HttpRequestInitializer)`, `DitaToGoogleDocMapper(GoogleDocStyleMapper, ImageHandler)`, `ImageHandler(GoogleDocApiClient, String, String, double)`
- Produces:
  - `GoogleDocRenderer.main(String[] args)` — CLI entry point called from Ant `build.xml`. Args: `[tempDir, inputMap, credentialsPath, authType, docId, folderId, tokenDir, imageMaxWidth]`
  - Reads the resolved ditamap, walks topics in order, calls `DitaToGoogleDocMapper.mapTopic()` for each, sends the accumulated requests via `GoogleDocApiClient.executeBatchUpdate()`, prints the doc URL to stdout

- [ ] **Step 1: Write the failing tests**

```java
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "org.dita.googledoc.GoogleDocRendererTest"
```

Expected: FAIL — `GoogleDocRenderer` class does not exist

- [ ] **Step 3: Implement `GoogleDocRenderer`**

```java
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
        if (args.length < 4) {
            System.err.println(
                "Usage: GoogleDocRenderer <tempDir> <inputMap> <credentials> <authType> " +
                "[docId] [folderId] [tokenDir] [imageMaxWidth]");
            System.exit(1);
        }

        String tempDir = args[0];
        String inputMap = args[1];
        String credentialsPath = args[2];
        String authType = args[3];
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

    private static void collectTopicRefs(Element element, List<TopicRef> refs, int depth) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el) {
                if ("topicref".equals(el.getTagName()) ||
                    "chapter".equals(el.getTagName()) ||
                    "appendix".equals(el.getTagName())) {
                    String href = el.getAttribute("href");
                    if (!href.isEmpty()) {
                        refs.add(new TopicRef(href, depth));
                    }
                    collectTopicRefs(el, refs, depth + 1);
                } else if (!"title".equals(el.getTagName())) {
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "org.dita.googledoc.GoogleDocRendererTest"
```

Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/dita/googledoc/GoogleDocRenderer.java \
  src/test/java/org/dita/googledoc/GoogleDocRendererTest.java
git commit -m "feat: add GoogleDocRenderer entry point with map parsing and orchestration"
```

---

### Task 8: Sample Data and Integration Test

**Files:**
- Create: `src/test/resources/sample/sample.ditamap`
- Create: `src/test/resources/sample/intro.dita`
- Create: `src/test/resources/sample/features.dita`
- Create: `src/test/java/org/dita/googledoc/IntegrationTest.java`

**Interfaces:**
- Consumes: All previously built classes
- Produces: End-to-end test that processes sample DITA content through the mapper and verifies correct request generation. A separate profile-gated test for live Google API calls.

- [ ] **Step 1: Create `src/test/resources/sample/sample.ditamap`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<map>
  <title>Sample Google Doc Output</title>
  <topicref href="intro.dita">
    <topicref href="features.dita"/>
  </topicref>
</map>
```

- [ ] **Step 2: Create `src/test/resources/sample/intro.dita`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<topic id="intro">
  <title>Introduction</title>
  <body>
    <p>This is a <b>sample</b> document that demonstrates the
    <codeph>googledoc</codeph> output format.</p>
    <p>It includes <i>various</i> DITA elements to verify rendering.</p>
    <note type="tip">This plugin converts DITA to Google Docs automatically.</note>
    <ul>
      <li>Headings and paragraphs</li>
      <li>Bold, italic, and monospace text</li>
      <li>Lists and tables</li>
    </ul>
    <codeblock>dita --input=mymap.ditamap --format=googledoc \
  --googledoc.credentials=creds.json</codeblock>
    <p>See <xref href="https://www.dita-ot.org" format="html"
    scope="external">DITA-OT</xref> for more information.</p>
  </body>
</topic>
```

- [ ] **Step 3: Create `src/test/resources/sample/features.dita`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<topic id="features">
  <title>Features</title>
  <body>
    <section>
      <title>Content Support</title>
      <p>The plugin supports the following content types:</p>
      <table>
        <tgroup cols="2">
          <thead>
            <row>
              <entry>Element</entry>
              <entry>Rendering</entry>
            </row>
          </thead>
          <tbody>
            <row>
              <entry>topic/title</entry>
              <entry>Google Docs heading</entry>
            </row>
            <row>
              <entry>p</entry>
              <entry>Normal paragraph</entry>
            </row>
            <row>
              <entry>codeblock</entry>
              <entry>Monospace text</entry>
            </row>
          </tbody>
        </tgroup>
      </table>
    </section>
    <section>
      <title>Authentication</title>
      <dl>
        <dlentry>
          <dt>Service Account</dt>
          <dd>For CI/CD pipelines and automated builds.</dd>
        </dlentry>
        <dlentry>
          <dt>OAuth 2.0</dt>
          <dd>For interactive local usage with browser consent.</dd>
        </dlentry>
      </dl>
    </section>
  </body>
</topic>
```

- [ ] **Step 4: Create integration test**

```java
package org.dita.googledoc;

import com.google.api.services.docs.v1.model.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationTest {

    private GoogleDocStyleMapper styleMapper;
    private DitaToGoogleDocMapper mapper;

    @BeforeEach
    void setUp() {
        styleMapper = new GoogleDocStyleMapper();
        mapper = new DitaToGoogleDocMapper(styleMapper, null);
    }

    @Test
    void fullMapProcessing_generatesRequests() throws Exception {
        URL mapUrl = getClass().getResource("/sample/sample.ditamap");
        assertNotNull(mapUrl, "sample.ditamap not found on classpath");

        String mapPath = Path.of(mapUrl.toURI()).toString();
        String mapTitle = GoogleDocRenderer.parseMapTitle(mapPath);
        List<GoogleDocRenderer.TopicRef> refs = GoogleDocRenderer.parseTopicRefs(mapPath);

        assertEquals("Sample Google Doc Output", mapTitle);
        assertEquals(2, refs.size());
        assertEquals("intro.dita", refs.get(0).href());
        assertEquals(0, refs.get(0).depth());
        assertEquals("features.dita", refs.get(1).href());
        assertEquals(1, refs.get(1).depth());

        List<Request> allRequests = new ArrayList<>();
        String mapDir = Path.of(mapPath).getParent().toString();

        for (GoogleDocRenderer.TopicRef ref : refs) {
            String topicPath = Path.of(mapDir, ref.href()).toString();
            org.w3c.dom.Document topicDoc = javax.xml.parsers.DocumentBuilderFactory
                .newInstance().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(topicPath));
            allRequests.addAll(mapper.mapTopic(topicDoc, ref.depth()));
        }

        assertFalse(allRequests.isEmpty());
        assertTrue(allRequests.size() > 20,
            "Expected >20 requests for sample content, got " + allRequests.size());

        assertTrue(allRequests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("Introduction")));

        assertTrue(allRequests.stream().anyMatch(r ->
            r.getInsertText() != null &&
            r.getInsertText().getText().contains("Features")));

        assertTrue(allRequests.stream().anyMatch(r ->
            r.getInsertTable() != null));

        assertTrue(allRequests.stream().anyMatch(r ->
            r.getCreateParagraphBullets() != null));

        assertTrue(allRequests.stream().anyMatch(r ->
            r.getUpdateTextStyle() != null &&
            r.getUpdateTextStyle().getTextStyle().getLink() != null));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GOOGLE_CREDENTIALS_PATH", matches = ".+")
    void liveGoogleDocsCreation() throws Exception {
        String credentialsPath = System.getenv("GOOGLE_CREDENTIALS_PATH");
        String authType = System.getenv().getOrDefault("GOOGLE_AUTH_TYPE", "service");
        String folderId = System.getenv("GOOGLE_FOLDER_ID");

        URL mapUrl = getClass().getResource("/sample/sample.ditamap");
        String mapPath = Path.of(mapUrl.toURI()).toString();
        String mapDir = Path.of(mapPath).getParent().toString();

        GoogleDocRenderer.run(
            mapDir, mapPath, credentialsPath, authType,
            null, folderId, null, 468.0);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew test --tests "org.dita.googledoc.IntegrationTest"
```

Expected: `fullMapProcessing_generatesRequests` PASS. `liveGoogleDocsCreation` SKIPPED (no env var).

- [ ] **Step 6: Run full test suite**

```bash
./gradlew test
```

Expected: All tests PASS

- [ ] **Step 7: Build the plugin bundle**

```bash
./gradlew bundlePlugin
```

Expected: `build/distributions/org.dita.googledoc-1.0.0.zip` created

- [ ] **Step 8: Commit**

```bash
git add src/test/resources/sample/ \
  src/test/java/org/dita/googledoc/IntegrationTest.java
git commit -m "feat: add sample DITA content and integration tests"
```

---

### Task 9: README and Documentation

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: all prior tasks
- Produces: user-facing documentation

- [ ] **Step 1: Create `README.md`**

```markdown
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
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add README with installation and usage instructions"
```
