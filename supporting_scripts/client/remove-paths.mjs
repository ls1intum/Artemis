/**
 * Removes the files and directories given as arguments, recursively. A path that does not exist is not an error.
 * Used by the `clean-www` and `docs:build:prepared` scripts instead of a package; pass every path, there is no globbing.
 */
import { rmSync } from 'node:fs';

for (const path of process.argv.slice(2)) {
    rmSync(path, { recursive: true, force: true });
}
