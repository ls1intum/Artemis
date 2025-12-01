# Artemis Course Setup Scripts

This project contains Python scripts that automate the PECV Bench Course setup and management of programming exercises in an Artemis instance.

# Setup

## Prerequisites


- Python 3.13 (other versions might not work due to model incompatibility)
- pip

## 1. Create and activate a virtual environment in **hyperion-benchmark-workflow** folder
It is recommended to use a virtual environment to manage dependencies in isolation from the global Python environment. This approach can prevent version conflicts and keep your system environment clean.

1. Install `virtualenv` if it's not already installed:
   - If you are using [brew](https://brew.sh/) on macOS, you can install `virtualenv` using:
      ```shell
      brew install virtualenv
      ```
   - Otherwise, you can use pip to install `virtualenv`:
      ```shell
      python3 -m pip install virtualenv
      ```

2. Create a virtual environment in **hyperion-benchmark-workflow** folder:
    ```shell
    python3 -m venv venv
    ```

3. Activate the virtual environment:
   - On **macOS/Linux**:
     ```shell
     source venv/bin/activate
     ```
   - On **Windows**:
     ```shell
     venv\Scripts\activate
     ```

Once the virtual environment is activated, you will see the `(venv)` prefix in your terminal prompt. All dependencies will now be installed locally to this environment.

## 2. Install the Required Packages
```shell
pip install -r requirements.txt
```

## 3. Configure the Environment
1. Start your local Artemis instance.
2. Configure the values in [hyperion-benchmark-workflow/config.ini](./config.ini) according to your local setup.


**Note:**
1. Ensure that the [hyperion-benchmark-workflow/config.ini](./config.ini) file is correctly configured before running any scripts.
2. **Always test the scripts on a local setup before running them on a production or test server! ⚠️**

# Usage 
The script will automatically perform all the necessary steps (running from hyperion-benchmark-workflow):

```shell
python3 run_pecv_bench_in_artemis.py
```

1. Authenticate as admin. 
2. Create course.
3. Materialize variants
4. Convert variants to zip files
5. Import exercise from zip file
6. Execute consistency check on each exercise
7. Run reporting scripts to generate quantifiable metrics for analysis and save them in markdown file

Analysis results are stored inside *hyperion-benchmark-workflow/pecv-bench/results/artemis-benchmark/* folder
* report.md
* summary.md/json/tex
* variants_report_plots
* variants_report.json
