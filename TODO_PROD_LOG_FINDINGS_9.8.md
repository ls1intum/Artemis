# Production log findings after the 9.8 release (excluding Iris/Weaviate)

Scope: the four core nodes from their 9.8 restart (2026-08-01 ~08:42 CEST) to ~09:50 CEST, about 70 minutes of
production traffic on commit `42507d3f9a`. 2665 ERROR and 70 WARN lines, collapsing into 21 distinct patterns.

**Out of scope on purpose**: the Iris lecture visibility synchronization and the Weaviate rate limiting behind it. That
was 2478 of the 2665 error lines. Everything below is the remaining ~190 lines.

Nothing here affects availability. Ordered by how much I would actually prioritise it.

---

## 1. WebSocket subscriptions are not deduplicated per destination

**Volume**: 158x `Cannot unsubscribe as no subscription exists for id: <id>` plus 9x
`There already is a subscription for: /topic/...`. These are two symptoms of one cause.

**Cause**: `WebsocketService.subscribe` (`src/main/webapp/app/foundation/service/websocket.service.ts:571`) mints a
fresh subscription id on every call and never reuses an existing one:

```ts
const params: IWatchParams = { destination: channel, subHeaders: { id: this.sessionId + '-' + this.subscriptionCounter++ } };
return this.rxStomp.watch(params).pipe(map(this.handleIncomingMessage<T>()));
```

Two services subscribe to the *same* destination in their constructors:

* `metis.service.ts:83` and `metis-conversation.service.ts:72`, both `/topic/user/${userId}/notifications/conversations`

which is exactly the destination in the broker error
(`There already is a subscription for: /topic/user/28963/notifications/conversations`). ActiveMQ rejects the second
SUBSCRIBE for a destination already subscribed on that session. The id from the rejected frame was never registered, so
when the component is destroyed and rxStomp sends UNSUBSCRIBE for it, the broker answers
`Cannot unsubscribe as no subscription exists for id`. That is why the second message is 17 times more frequent than the
first: every rejected subscribe eventually produces one, and reconnects multiply it.

**Fix**: deduplicate at the transport layer, in `WebsocketService.subscribe`, so N callers of the same destination
produce exactly one STOMP subscription:

```ts
private readonly sharedChannels = new Map<string, Observable<any>>();

subscribe<T = any>(channel: string): Observable<T> {
    if (!channel) return EMPTY;
    if (!this.rxStomp) this.connect();
    if (!this.rxStomp) return EMPTY;

    let shared = this.sharedChannels.get(channel);
    if (!shared) {
        const params: IWatchParams = { destination: channel, subHeaders: { id: this.sessionId + '-' + this.subscriptionCounter++ } };
        shared = this.rxStomp.watch(params).pipe(
            map(this.handleIncomingMessage<any>()),
            finalize(() => this.sharedChannels.delete(channel)),
            share({ resetOnRefCountZero: true }),
        );
        this.sharedChannels.set(channel, shared);
    }
    return shared as Observable<T>;
}
```

`share({ resetOnRefCountZero: true })` sends the UNSUBSCRIBE only when the last consumer leaves, and the `finalize`
clears the map entry so a later subscriber gets a fresh one.

**Why this over the narrow fix**: removing the duplicate subscription from one of the two Metis services would silence
today's instance, but the next pair of components sharing a topic reintroduces it. The transport fix makes it
structurally impossible and is one contained change.

**Risk**: moderate. Every websocket consumer runs through this method, so it needs care around reconnect (rxStomp
re-subscribes internally on reconnect, which the shared observable must not break) and good test coverage. Worth a
dedicated pull request rather than bundling it with log cleanups.

**Recommendation**: fix, as its own PR.

---

## 2. Binary files are read as UTF-8 and logged at ERROR

**Volume**: 7x on node1,
`Content of file: gradle/wrapper/gradle-wrapper.jar could not be loaded and throws the following error: Input length = 1`.

**Cause**: `RepositoryService.getFilesContentFromWorkingCopy`
(`src/main/java/de/tum/cit/aet/artemis/programming/service/RepositoryService.java:145`) reads every file as UTF-8:

```java
files.forEach(file -> {
    try {
        fileListWithContent.put(file.toString(), Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }
    catch (IOException e) {
        log.error("Content of file: {} could not be loaded and throws the following error: {}", file, e.getMessage());
    }
});
```

The method already takes `omitBinaries`, which is forwarded to `gitService.listFilesAndFolders(repository, omitBinaries)`
and filtered there with `isBinaryFile` (`GitService.java:1032`). `.jar` is in that set
(`BinaryFileExtensionConfiguration:28`). So the error only fires when a caller passes `omitBinaries = false`, which the
REST layer does on request (`RepositoryProgrammingExerciseParticipationResource:301`,
`ProgrammingExerciseRetrievalResource:349` and `:372`). A UTF-8 read of a `.jar` can never succeed, so the file is
dropped from the map and an ERROR is logged for a completely predictable outcome.

**Fix**: skip files that are known to be binary before attempting the read, independently of `omitBinaries`, since the
result is identical (absent from the map) and demote the remaining decode failures:

```java
files.forEach(file -> {
    if (isBinaryFile(file.getName())) {
        // A binary file can never be decoded as UTF-8; it would be dropped below anyway, so skip it quietly.
        return;
    }
    try {
        fileListWithContent.put(file.toString(), Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }
    catch (IOException e) {
        log.debug("Content of file {} could not be read as UTF-8: {}", file, e.getMessage());
    }
});
```

**Check before merging**: confirm no caller distinguishes "absent because binary" from "absent because unreadable". The
Javadoc already states that files causing an IOException are logged but not included, so behaviour is unchanged.

**Risk**: low. **Recommendation**: fix.

---

## 3. Metrics stop recording new endpoints

**Volume**: 1x, `MaximumAllowableTagsMeterFilter: Reached the maximum number of 'uri' tags for 'http.server.requests'`.

**Impact**: once the cap (Spring Boot default 100) is reached, Micrometer stops creating timers for URIs first seen
afterwards. Monitoring silently loses coverage. One line in the log, but it degrades observability cluster-wide.

**Cause, most likely**: LocalVC serves git over a plain servlet registered at `/git/*`
(`src/main/java/de/tum/cit/aet/artemis/localvc/service/JGitServletConfiguration.java:42`):

```java
ServletRegistrationBean<GitServlet> registration = new ServletRegistrationBean<>(artemisGitServlet, "/git/*");
```

Because those requests never hit a Spring MVC handler, there is no URI template to tag them with, so every repository
path becomes its own tag value (`/git/EIST26BAKERY/eist26bakery-go56maj.git/info/refs`, and one more per
`git-upload-pack`, `git-receive-pack`). With thousands of repositories on production, the 100 tag budget is exhausted
almost immediately, and it is exhausted by values nobody wants to graph.

**I could not confirm this on the running system**: `/management/prometheus` requires authentication and
`/actuator/prometheus` is swallowed by the Angular catch-all route. Confirm first with an authenticated scrape:

```bash
curl -s -u <admin> https://artemis.tum.de/management/prometheus \
  | grep '^http_server_requests_seconds_count' \
  | sed -E 's/.*uri="([^"]*)".*/\1/' | sort | uniq -c | sort -rn | head -30
```

**Fix**, once confirmed: register a `MeterFilter` that collapses the git paths into a single tag, so the budget is spent
on real API endpoints:

```java
@Bean
MeterFilter gitUriTagFilter() {
    return MeterFilter.replaceTagValues("uri", uri -> uri.startsWith("/git/") ? "/git/**" : uri);
}
```

Raising `management.metrics.web.server.max-uri-tags` instead would only postpone the problem and inflate the time series
database.

**Risk**: low. **Recommendation**: confirm, then fix.

---

## 4. Deprecated `@Valid` placement

**Volume**: 8x, `HV000271: Using @Valid on a container (java.util.List) is deprecated ... IrisMessageRequestDTO#context`.

This is a Bean Validation annotation issue, unrelated to the Pyris synchronization excluded above.

**Cause**: `src/main/java/de/tum/cit/aet/artemis/iris/dto/IrisMessageRequestDTO.java:24` (and the same pattern at `:39`):

```java
@Valid @Nullable List<IrisMessageContextDTO> context
```

**Fix**: move the annotation onto the type argument, which is both the non-deprecated form and the one that actually
validates the elements:

```java
@Nullable List<@Valid IrisMessageContextDTO> context
```

**Note**: this genuinely changes behaviour, since element-level validation was probably not running before. Check that
existing payloads still validate, otherwise previously accepted requests start returning 400.

**Risk**: low, but not zero because of the behaviour change. **Recommendation**: fix, with a test for a payload
containing an invalid context element.

---

## 5. Students failing to clone with an empty password (pre-existing, not a 9.8 regression)

**Volume**: 40x `LocalVC fetch rejected for /git/... -> HTTP 401 (LocalVCAuthException: No password provided)`, plus 4x
`BadCredentialsException: Wrong credentials` and 4x `User <login> was not found in the database`.

**My earlier read was wrong and I am correcting it.** I first assumed this was git's normal unauthenticated probe and
suggested downgrading it to debug. It is not:

* The expected probe throws a *different* message, `No authorization header provided`
  (`LocalVCServletService.java:249`), and `LocalVCFetchFilter.java:49` already suppresses exactly that one.
* `No password provided` comes from `SecurityUtils.java:62`, which means the client *did* send an Authorization header,
  with an empty password.
* Only 8 repositories and 4 students are affected, each retrying 1 to 8 times, and none of them was observed succeeding
  afterwards. These look like students who genuinely cannot clone.

**Also**: it predates the release. Counting the same message before and after the restart in the same log file gives
144 occurrences under 9.7.1 on node1 versus 34 under 9.8, so the rate went slightly down. It is not caused by the new
access token work in #13278.

**Recommendation**: do **not** suppress this log line, it is doing its job. Instead treat it as a product question:
find out why these students send an empty password (most plausibly they copy the HTTPS clone URL and press enter at the
password prompt instead of pasting a token) and improve the guidance in the clone dialog. Worth its own small
investigation rather than a logging change.

---

## 6. Hazelcast split-brain during the deployment

**Volume**: 3x each of `is merging [tcp/ip] to`, `is merging to ... instructed by master`, `Resetting local member UUID`
on node2, node3 and node4.

**Cause**: nodes 2 to 4 were started simultaneously, formed separate clusters before discovering each other, and then
merged. Self-healing, and the cluster is healthy.

**Fix**: no code change. If you want it gone, stagger the start of nodes 2 to 4 by 15 to 20 seconds in the deployment
procedure. Note this is a direct consequence of the "start the rest simultaneously" step, so it is a deliberate
trade-off between deployment speed and a clean log.

**Recommendation**: accept, or adjust the deployment script.

---

## 7. No action needed

* **11x `Failed to forward DISCONNECT session=...`**: the relay connection was already gone. Expected around a restart.
* **2x `Failed to parse TextMessage payload=[SUBSCRIBE`**: a malformed STOMP frame from one client. Revisit only if it
  recurs outside a deploy window.
* **1x `HttpRequestMethodNotSupportedException: Request method 'POST' is not supported'`, 1x `PageNotFound`**: almost
  certainly scanners or a stale bookmark.
* **1x `403: Invalid token!`, 1x `403: You are not allowed to access this resource`**: correct rejections.

---

## Suggested order

1. Item 2 (binary file ERROR) and item 4 (`@Valid`), both a few lines, can share one pull request
2. Item 3, confirm the cardinality source with an authenticated scrape, then add the `MeterFilter`
3. Item 1, the websocket deduplication, as its own pull request with tests
4. Item 5, as a product investigation rather than a logging change
5. Item 6, a one-line change to the deployment procedure if you want it
