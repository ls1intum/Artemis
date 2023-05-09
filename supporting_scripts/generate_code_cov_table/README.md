# Generate Code Coverage Table Script

This script generates a code coverage report for changed files in a Bamboo build, comparing a specified Git branch against the develop branch. 
The generated report is copied to the clipboard, ready for pasting into a pull request or documentation.

## Setup

### Prerequisites

- Python 3.6 or higher
- pip
- git

### Installation

1. Optionally, create and activate a virtual environment:
```
python3 -m venv venv
```
On Linux or macOS:
```
source venv/bin/activate
```
On Windows (CMD):
```
venv\Scripts\activate.bat
```
On Windows (PowerShell):
```
venv\Scripts\Activate.ps1
```

2. Install the required packages:
```
pip install -r requirements.txt
```

3. Configure environment variables by copying the `env.example` file to `.env` (use with caution, security risk!):
```
cp env.example .env
```
Fill out the following variables:
```
BAMBOO_USERNAME=ab12cd
BAMBOO_PASSWORD=123456
```

Alternatively, you can use the command line arguments `--username` (and `--password`) to pass the credentials.

**Recommended for security, but not for convenience:**  
Don't store the `BAMBOO_PASSWORD` in the `.env` file, but let the script prompt you for it.

> **Note:**
> Ideally, we would use a Bamboo API personal access token instead of the password, but this did not seem to work for
> requesting the artifact URLs. The artifact also could not be downloaded directly from the Bamboo server, so we had to
> use user authentication. (PAT would therefore only work partially for this scrip => User authentication is required)

## Usage

The branch can be specified using the `--branch-name` option. If no branch is specified, the current branch will be used.
You can also use remote branches, e.g. `origin/feature/xyz`.

### Generate Code Coverage Report for Current Branch (default)

Run the script:
```
python3 generate_code_cov_table.py
```
For additional options, use `--help`.

### Generate Code Coverage Report for a Specific Branch

Run the script with the --branch-name option:
```
python3 generate_code_cov_table.py
```
For additional options, use `--help`.

### Enable Verbose Logging

Use the `--verbose` option for more detailed logging:
```
python3 generate_code_cov_table.py --verbose
```
With verbose logging, you will see which files are being skipped and processed, as well as the generated result.

## Output

After running the script, the code coverage report will be copied to your clipboard. 
The output will be separated into client and server sections, each containing a table with the following columns:

- Class/File: The changed file with a link to its coverage report
- Line Coverage: Percentage of lines covered by tests
- Confirmation (assert/expect): A checkbox for manual confirmation of test coverage

**You will have to manually adjust the confirmation column for each file!**
