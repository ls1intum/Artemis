# API cases

A browser only sends what the client builds. When the change touches a REST endpoint's request, response, authorization or behavior, call the endpoint directly as well. That reaches what no screen sends: a role the UI hides the button from, a body the form never builds, an id from another course, a field the client ignores.

## When a feature needs them

Every entry in a feature's `endpoints` whose `changed` is not `none` gets at least one `api` case. Plan these per endpoint, picking what the change makes relevant:

- **Contract.** As the role the UI uses, call the endpoint with the payload the browser sent (copy it from the driver's `net/<case>-<n>.txt`). Check every field the client reads is present at the path it reads it from (the tracer's chain names the readers), and no field leaks that the role must not see (other users' data, hidden solutions, internal ids, tokens).
- **Roles.** Call it as each role one level below the annotation's minimum, as a user of another course, and anonymously. Check the endpoint's documented refusal for each identity and verify there is no data leak. For an authenticated role, a 401 does not prove an authorization refusal: verify the session and rerun or mark it blocked. A 500 is a defect.
- **Ids.** For a path id, a body id, or both: a mismatch between them, an id from another course or exercise, an id that does not exist. Expect 400, 403 or 404, never 200 and never 500.
- **Input.** Missing required fields, `null`, empty string, too long, wrong type, out-of-range numbers, unknown enum values, extra fields the server must ignore. Expect 400 with an Artemis error key (`X-artemisApp-error` header) and no partial write.
- **Writes.** After every accepted write, read the entity back through its GET endpoint and compare. After every refused write, read it back and confirm nothing changed.
- **Old clients.** When the request shape changed, send the old shape once and record what happens. A silent 200 that drops data is a finding.

## Running them

Use `skills/browser-qa/scripts/api.sh`. It signs in through the same authenticate endpoint the client uses, keeps one cookie jar per user in the run dir, checks the current account before each authenticated request, reauthenticates when needed, and saves request and response metadata in `api/<name>.txt` and the complete response body in `api/<name>.body`:

```bash
skills/browser-qa/scripts/api.sh <run> <server-port> artemis_test_user_16 F01-api-01 GET /api/lecture/lectures/<lectureId>
skills/browser-qa/scripts/api.sh <run> <server-port> artemis_test_user_1 F01-api-02 PUT /api/lecture/lectures @<run>/api/F01-api-02.body.json
skills/browser-qa/scripts/api.sh <run> <server-port> anonymous F01-api-03 GET /api/lecture/lectures/<lectureId>
```

Write JSON request bodies with a serializer, such as `JSON.stringify` in Node, not by joining strings. Parse `api/<name>.body` with a suitable parser and check fields one path at a time. The OpenAPI spec at `config/openapi/openapi.yaml` in the checkout names each endpoint's documented shape; a response that differs from it is a finding when the change touched that endpoint.

## Results

An `api` case writes `results/<case>.json` like any other, with `requests` filled from the saved files and `evidence` pointing at the relevant `api/<name>.txt` and any nonempty response body used for assertions. It needs no video. A `fail` here is confirmed in step 7 like a browser failure, and a refused call that the UI also offers must be checked in the browser too: a 403 the UI hits on a normal path is a regression, not a guard.
