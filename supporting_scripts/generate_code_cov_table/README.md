# Generate Code Coverage Table Script
By Felix Dietrich and improved by Paul Schwind

## Setup

1. Optionally create a virtual environment:
```
python3 -m venv venv
source venv/bin/activate
```

2. Install requirements with pip:
```
pip install -r requirements.txt
```

3. Fill in environment variables in `.env` file (copy `env.example`) for convenience:
Alternatively, you can use the command line arguments `--username` (and `--password`) to pass the credentials.
```
cp env.example .env
```
Fill out the following variables:
```
BAMBOO_USERNAME=ab12cd
BAMBOO_PASSWORD=123456
```

## Usage

### Current Branch (default)

Run the script with:
```
python3 generate_code_cov_table.py
```

Use `--help` for additional info.

### Specific Branch

Run the script with:
```
python3 generate_code_cov_table.py --branch-name <branch_name>
```

Use `--help` for additional info.

### Verbose Logging

Run the script with `--verbose` to get more detailed logging:
```
python3 generate_code_cov_table.py --verbose
```

You will see which files are being skipped and which are being processed, as well as the result.
