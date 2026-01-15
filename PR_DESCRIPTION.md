<!-- Thanks for contributing to Artemis! Before you submit your pull request, please make sure to check all tasks by putting an x in the [ ] (don't: [x ], [ x], do: [x]). Remove not applicable tasks and do not leave them unchecked -->

### Checklist
#### General
- [ ] I tested **all** changes and their related features with **all** corresponding user types on a test server.
- [ ] Language: I followed the [guidelines for inclusive, diversity-sensitive, and appreciative language](https://docs.artemis.cit.tum.de/dev/guidelines/language-guidelines/).
- [ ] I chose a title conforming to the [naming conventions for pull requests](https://docs.artemis.cit.tum.de/dev/development-process/development-process.html#naming-conventions-for-github-pull-requests).


#### Server
- [ ] **Important**: I implemented the changes with a [very good performance](https://docs.artemis.cit.tum.de/dev/guidelines/performance/) and prevented too many (unnecessary) and too complex database calls.
- [ ] I **strictly** followed the principle of **data economy** for all database calls.
- [ ] I **strictly** followed the [server coding and design guidelines](https://docs.artemis.cit.tum.de/dev/guidelines/server/).
- [ ] I added multiple integration tests (Spring) related to the features (with a high test coverage).
- [ ] I documented the Java code using JavaDoc style.


### Motivation and Context

This PR adds the foundational Weaviate vector database integration to Artemis, enabling semantic search capabilities for lectures, FAQs, and other content. Weaviate is required for BM25 keyword search and hybrid search (combining keyword and vector similarity) used by Iris for intelligent content retrieval.

**Key requirements addressed:**
- Artemis needs to write lecture metadata directly to Weaviate for BM25 search
- Schema definitions must be compatible with the Iris repository to ensure data interoperability
- Schema validation prevents silent compatibility issues between Artemis and Iris

Related to: Global search feature implementation


### Description

This PR implements the Weaviate Java client integration with automatic schema validation against the Iris repository schemas.

**New Components:**

1. **Weaviate Client Configuration**
   - `WeaviateConfigurationProperties`: Configuration for host, port, gRPC port, and schema validation settings
   - `WeaviateClientConfiguration`: Creates the Weaviate client bean
   - New Spring profile `weaviate` to enable the integration

2. **Schema Definitions** (matching Iris Python schemas)
   - `Lectures` (LectureUnitPageChunk) - Lecture slide content
   - `LectureTranscriptions` - Video transcription segments
   - `LectureUnitSegments` - Combined segment summaries
   - `LectureUnits` - Lecture unit metadata
   - `Faqs` - FAQ entries

3. **Schema Validation**
   - `IrisSchemaFetcher`: Fetches schema definitions from Iris GitHub repository
   - `WeaviateSchemaValidator`: Validates Artemis schemas against Iris on startup
   - `WeaviateSchemaValidationFailureAnalyzer`: Provides helpful error messages on validation failure

4. **WeaviateService**
   - Creates collections on startup if they don't exist
   - Provides methods for inserting lecture and FAQ data
   - Health check functionality

**Configuration Options:**
```yaml
artemis:
  weaviate:
    host: localhost
    port: 8001
    grpc-port: 50051
    secure: false
    schema-validation:
      enabled: true      # Enable/disable validation
      strict: true       # true=fail on mismatch, false=warn only
```

**Schema Validation Behavior:**
- `strict: true` (default): Server fails to start if schemas don't match Iris
- `strict: false`: Logs warnings but allows startup (not recommended for production)

**Files Changed:**
- `gradle.properties`: Added `weaviate_client_version=6.0.0`
- `build.gradle`: Added Weaviate client dependency
- `docker/weaviate.yml`: Upgraded to Weaviate 1.33.0 (required for Java client v6)
- `Constants.java`: Added `PROFILE_WEAVIATE`
- `spring.factories`: Registered failure analyzer
- `application-artemis.yml`: Added weaviate configuration example
- New `application-weaviate.yml`: Profile-specific configuration


### Steps for Testing

Prerequisites:
- Docker installed
- Weaviate running (use `docker compose -f docker/weaviate.yml up -d`)

**Test 1: Basic Startup with Weaviate Profile**
1. Start Weaviate: `docker compose -f docker/weaviate.yml up -d`
2. Start Artemis with the weaviate profile: `--spring.profiles.active=dev,localci,localvc,artemis,scheduling,core,weaviate`
3. Verify in logs:
   - "Starting Weaviate schema validation against Iris repository..."
   - "Weaviate schema validation passed: Artemis schemas match Iris schemas"
   - "Initializing Weaviate collections..."
   - "Successfully connected to Weaviate"

**Test 2: Schema Validation Failure (Strict Mode)**
1. Temporarily modify a schema in `WeaviateSchemas.java` (e.g., rename a property)
2. Start Artemis with the weaviate profile
3. Verify server fails to start with a helpful error message from the failure analyzer

**Test 3: Schema Validation Warning (Non-Strict Mode)**
1. Set `artemis.weaviate.schema-validation.strict: false`
2. Temporarily modify a schema in `WeaviateSchemas.java`
3. Start Artemis with the weaviate profile
4. Verify server starts but logs warnings about schema mismatches

**Test 4: Disabled Weaviate**
1. Start Artemis without the weaviate profile
2. Verify no Weaviate-related logs appear and startup succeeds


### Testserver States
You can manage test servers using [Helios](https://helios.aet.cit.tum.de/). Check environment statuses in the [environment list](https://helios.aet.cit.tum.de/repo/69562331/environment/list). To deploy to a test server, go to the [CI/CD](https://helios.aet.cit.tum.de/repo/69562331/ci-cd) page, find your PR or branch, and trigger the deployment.

### Review Progress

#### Performance Review
- [ ] I (as a reviewer) confirm that the server changes (in particular related to database calls) are implemented with a very good performance even for very large courses with more than 2000 students.

#### Code Review
- [ ] Code Review 1
- [ ] Code Review 2

#### Manual Tests
- [ ] Test 1
- [ ] Test 2


### Test Coverage
<!-- Tests to be added -->

| Class/File | Line Coverage | Confirmation (assert/expect) |
|------------|--------------:|-----------------------------:|
| WeaviateSchemaValidator.java | TBD | ⏳ |
| WeaviateService.java | TBD | ⏳ |
| IrisSchemaFetcher.java | TBD | ⏳ |


### Screenshots
<!-- No UI changes in this PR -->
N/A - This PR contains only server-side changes with no UI modifications.