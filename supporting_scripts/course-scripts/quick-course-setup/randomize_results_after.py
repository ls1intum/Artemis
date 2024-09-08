import os
import shutil
from logging_config import logging

test_files_folder: str = "../../../src/main/resources/templates/java/test/testFiles"
default_folder: str = "./testFiles-template/default"
random_test_case_dest: str = os.path.join(test_files_folder, "RandomizedTestCases.java")

def delete_random_test_case() -> None:
    if os.path.exists(random_test_case_dest):
        os.remove(random_test_case_dest)
        logging.info(f"Deleted file: {random_test_case_dest}")
    else:
        logging.info(f"RandomizedTestCases.java file not found at {random_test_case_dest}")

def copy_default_folders() -> None:
    for folder_name in os.listdir(default_folder):
        src_folder_path: str = os.path.join(default_folder, folder_name)
        dest_folder_path: str = os.path.join(test_files_folder, folder_name)
        if os.path.isdir(src_folder_path):
            shutil.copytree(src_folder_path, dest_folder_path)
            logging.info(f"Copied folder {src_folder_path} to {dest_folder_path}")

if __name__ == "__main__":
    # Run this after running the script
    delete_random_test_case()
    copy_default_folders()
