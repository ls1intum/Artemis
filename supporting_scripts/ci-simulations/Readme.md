# Artemis CI Simulations

## Setup

1. (Recommended) Create and activate a Python virtual environment:
   ```sh
   python -m venv venv
   source venv/bin/activate
   ```
2. Install dependencies:
   ```sh
   pip install -r requirements.txt
   ```
3. Create your `secrets.ini` file (see `secrets.ini.example` for format).
4. Adjust `config.ini` as needed for your server and experiment settings.

## Running Experiments

- Run specific scenario:
  ```sh
  pytest -o log_cli=true --capture=no test_experiments.py::test_c_happy_path_high_load
  ```