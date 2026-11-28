# Local CodeAbility Sharing Platform

This mock runs the platform side of the Artemis connector on loopback. Start a local Artemis instance with its client available at `http://localhost:18280`. Set `server.url` to that origin, `artemis.sharing.enabled` to `true`, `artemis.sharing.serverurl` to `http://localhost:18281`, and `artemis.sharing.apikey` to a local test key. Export that same value as `SHARING_API_KEY`. The mock uses Node 26 and the repository's installed dependencies.

From the checkout root, start the mock with a directory for downloaded exports.

```sh
node supporting_scripts/sharing/mock-platform.mjs "${TMPDIR:-/tmp}/artemis-sharing-qa"
```

Artemis calls the mock's `GET /api/pluginIF/v0.1/reInitialize?apiKey=…` on startup. The mock exchanges that request for an authenticated `GET /api/core/sharing/config` at the configured local Artemis origin. If Artemis started first, trigger the same configuration exchange manually and check the result.

```sh
curl --fail-with-body -X POST http://localhost:18281/connect
curl --fail-with-body http://localhost:18280/api/core/sharing/config/is-enabled
```

Open [the local basket](http://localhost:18281/) and choose **Import basket into Artemis**. The mock works without a platform account. Sign in to Artemis as an instructor with an editable course. The two basket rows use different committed archive fixtures. Position 0 contains “JUnit IO Tests”, and position 1 contains “dtoShapedImport”. The basket expires 24 hours after the mock starts.

For direct API checks, retrieve the signed values and use an authenticated local Artemis cookie jar. The commands below require jq.

```sh
info=$(curl -fsS http://localhost:18281/sharing-info.json)
query=$(jq -rn --argjson info "$info" '$info | to_entries | map((.key | @uri) + "=" + (.value | @uri)) | join("&")')
curl -fsS -b "$ARTEMIS_COOKIES" "http://localhost:18280/api/programming/sharing/import/basket?$query"
printf '%s' "$info" | jq '. + {exercisePosition: 1}' | curl -fsS -b "$ARTEMIS_COOKIES" -H 'Content-Type: application/json' --data-binary @- \
  http://localhost:18280/api/programming/sharing/import/basket/exercise-details
```

Artemis fetches the metadata via `GET /api/pluginIF/v0.1/basket/local-basket`. It downloads the corresponding archive via `GET /api/pluginIF/v0.1/basket/local-basket/repository/{position}?format=artemis`.

To export, use **Export to Sharing** on a local programming exercise. Artemis returns a URL on the mock's `/api/pluginIF/v0.1/exercise/import` endpoint. Opening it consumes the signed Artemis download once. The mock checks the exercise details and repository ZIP contents, saves the original bytes as `export-<sha256>.zip` in the output directory, and shows the title, archive members, repositories, and SHA-256 on its receipt page. Follow **Return to Artemis** to go back to the exercise.

Run the companion tests with `node --test supporting_scripts/sharing/mock-platform.spec.mjs`.
