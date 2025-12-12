# Local PR Coverage Report Generator

This script generates a code coverage report for changed files in a PR by running tests locally for only the affected modules. This is faster and more reliable than waiting for CI builds, especially when there are flaky tests in other modules.

## Benefits

- **Faster**: Only runs tests for affected modules (not the entire test suite)
- **More reliable**: No dependency on CI builds or flaky tests in other modules
- **Works offline**: No GitHub token required
- **Immediate feedback**: Can run before pushing

## Prerequisites

- Node.js 24+ (same as project requirement)
- For client tests: `npm install` completed
- For server tests: Java 25+ and Gradle

## Usage

### Quick Start

```bash
# Run from project root
npm run coverage:pr

# Or directly
node supporting_scripts/code-coverage/local-pr-coverage/local-pr-coverage.mjs
```

### Options

| Option | Description |
|--------|-------------|
| `--base-branch <branch>` | Base branch to compare against (default: `origin/develop`) |
| `--skip-tests` | Skip running tests, use existing coverage data |
| `--client-only` | Only run client tests |
| `--server-only` | Only run server tests |
| `--print` | Print results to console (default: copy to clipboard) |
| `--verbose` | Enable verbose logging |
| `--help` | Show help |

### Examples

```bash
# Default: compare against origin/develop, run all tests
npm run coverage:pr

# Compare against a different branch
npm run coverage:pr -- --base-branch origin/main

# Only run client tests
npm run coverage:pr -- --client-only

# Only run server tests
npm run coverage:pr -- --server-only

# Skip tests and use existing coverage data (useful for debugging)
npm run coverage:pr -- --skip-tests

# Print results instead of copying to clipboard
npm run coverage:pr -- --print

# Verbose output for debugging
npm run coverage:pr -- --verbose
```

## How It Works

1. **Detect changed files**: Uses `git diff` to find files changed compared to the base branch
2. **Identify affected modules**: Extracts module names from file paths (e.g., `core`, `exam`, `programming`)
3. **Run module tests**:
   - Client: Runs `npm run prebuild && npx ng test --coverage --test-path-pattern=...` for affected modules
   - Server: Runs `./gradlew test -DincludeModules=<modules> jacocoTestReport`
4. **Parse coverage reports**:
   - Client: Reads `build/test-results/coverage-summary.json` (Jest output)
   - Server: Reads JaCoCo XML reports from `build/reports/jacoco/<module>/jacocoTestReport.xml`
5. **Generate report**: Creates a Markdown table with coverage percentages

## Output Format

The output is a Markdown table compatible with GitHub PR descriptions:

```markdown
#### Client

| Class/File | Line Coverage | Lines | Expects | Ratio |
|------------|-------------:|------:|--------:|------:|
| course.service.ts | 95.2% | 120 | 15 | 12.5 |
| course.component.ts | 87.3% | 85 | 8 | 9.4 |

#### Server

| Class/File | Line Coverage | Lines |
|------------|-------------:|------:|
| CourseService.java | 91.5% | 200 |
| CourseResource.java | 88.2% | 150 |
```

### Column Descriptions

**Client columns:**
- **Line Coverage**: Percentage of source lines executed by tests
- **Lines**: Number of non-empty, non-comment lines in the source file
- **Expects**: Number of `expect()` calls in the corresponding `.spec.ts` file
- **Ratio**: Expects per 100 lines of source code (higher = more thorough testing)

**Server columns:**
- **Line Coverage**: Percentage of source lines executed by tests
- **Lines**: Number of non-empty, non-comment lines in the source file

### Interpreting Client Ratio

The ratio helps identify under-tested client files:
- A file with high coverage but low ratio may just be "executed" without proper verification
- Example: 95% coverage with ratio 2.0 (2 expects per 100 lines) is suspicious
- Example: 85% coverage with ratio 15.0 (15 expects per 100 lines) indicates thorough testing

**Note**: Server tests often use integration tests that cover multiple classes, making assertion attribution unreliable. Manual review of test quality is recommended for server code.

## Excluded Files

The following files are automatically excluded from coverage reporting (they cannot be properly tested):

### Client
- `*.module.ts` - Angular modules
- `*.spec.ts` - Test files
- `*.routes.ts` / `*.route.ts` - Route configuration files
- Root app files (`app.component.ts`, `app.config.ts`, etc.)

### Server
- Test files are not included in coverage reports by default

## Comparison with `generate_code_cov_table`

| Feature | This script | `generate_code_cov_table.py` |
|---------|-------------|------------------------------|
| Runs tests locally | ✅ | ❌ (uses CI artifacts) |
| Requires GitHub token | ❌ | ✅ |
| Works offline | ✅ | ❌ |
| Affected by flaky tests in other modules | ❌ | ✅ |
| Uses CI coverage data | ❌ | ✅ |

Use this script for quick local feedback. Use `generate_code_cov_table.py` when you need the official CI coverage numbers.

## Troubleshooting

### "Coverage data not found"

- Make sure tests have been run with coverage enabled
- For client: Check that `build/test-results/coverage-summary.json` exists
- For server: Check that `build/reports/jacoco/<module>/jacocoTestReport.xml` exists

### Tests are failing

- Run with `--verbose` to see test output
- Try running tests manually to diagnose issues:
  - Client: `npm run prebuild && npx ng test --coverage --test-path-pattern="src/main/webapp/app/<module>/"`
  - Server: `./gradlew test -DincludeModules=<module>`

### Clipboard not working

- Use `--print` to print results to console instead
- On Linux, install `xclip` or `xsel`
