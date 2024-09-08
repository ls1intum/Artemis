import os
import shutil
from logging_config import logging

test_files_folder = "../../../src/main/resources/templates/java/test/testFiles"
random_test_case_file = "./testFiles-template/randomized/RandomizedTestCases.java"
random_test_case_dest = os.path.join(test_files_folder, "RandomizedTestCases.java")

def delete_existing_folders():
    folders_to_delete = ["behavior", "structural"]
    for folder in folders_to_delete:
        folder_path = os.path.join(test_files_folder, folder)
        if os.path.exists(folder_path) and os.path.isdir(folder_path):
            shutil.rmtree(folder_path)
            logging.info(f"Deleted folder: {folder_path}")
        else:
            logging.info(f"Folder not found: {folder_path}")

def copy_random_test_case():
    if os.path.exists(random_test_case_file):
        shutil.copy(random_test_case_file, random_test_case_dest)
        logging.info(f"Copied {random_test_case_file} to {random_test_case_dest}")
    else:
        logging.info(f"RandomizedTestCases.java file not found at {random_test_case_file}")

if __name__ == "__main__":
    # Run this before running the script
    delete_existing_folders()
    copy_random_test_case()
