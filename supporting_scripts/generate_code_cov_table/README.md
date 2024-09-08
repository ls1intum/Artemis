# Generate Code Coverage Table Script

This script generates a code coverage report for changed files in a GitHub build, comparing a specified Git branch against the develop branch. 
The generated report is copied to the clipboard and pasted into a pull request or documentation.

## Setup

### Prerequisites

- Python 3.12 or higher
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
TOKEN=ab12cd
```

Alternatively, you can use the command line argument `--token` to pass the credentials.
### Token
The token you must provide is a GitHub token with "Public Repository Access" checked.

**Recommended for security, but not for convenience:**  
Don't store the `TOKEN` in the `.env` file, but let the script prompt you for it.

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

### Printing

The output can be printed to the commandline using the `--print-results` option.

### Linux

On Linux, `pyperclip` requires either `xclip` or `xsel` to be installed. 
Alternatively, the Python modules `qtpy` or `PyQT5` have to be present. 
If no option to insert text into the clipboard is found, the script falls back to printing to stdout.

**You will have to manually adjust the confirmation column for each file!**
