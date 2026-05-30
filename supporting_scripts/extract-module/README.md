# Module extraction scripts

Reusable helpers for the four module-extraction PRs planned in
`docs/superpowers/plans/2026-05-26-notification-and-programming-module-extractions.md`
(notification, jenkins, localvc, localci).

## `move-package.sh`

Moves a list of individual Java files from one package to another. For each
file, it rewrites the `package ...;` declaration before moving and then
rewrites every `import old.pkg.X;` occurrence across `src/` to
`import new.pkg.X;`.

```
move-package.sh <old_pkg> <new_pkg> <file1> [file2 ...]
```

## `move-subpackage.sh`

Moves a whole sub-package directory. Useful when you want to relocate a
nested package like `communication.domain.course_notifications` into a new
parent like `notification.domain.course_notifications` in one step. Rewrites
package declarations recursively in every Java file inside and updates
imports across the codebase.

```
move-subpackage.sh <old_pkg> <new_pkg> <src_dir>
```

## Notes

- Both scripts use `git mv`, preserving history.
- Both rely on a working `grep -rl` and BSD-compatible `sed -i ''` (macOS).
  On Linux they fall back to GNU `sed -i`.
- They do **not** touch comments, Javadoc `{@link}` references, properties
  files, or HTML/TypeScript. After running, run `./gradlew compileJava -x webapp`
  to surface anything the import-rewrite missed.
