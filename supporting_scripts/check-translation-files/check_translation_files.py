import os
import logging
import sys
import json

translation_file_dir = './src/main/webapp/i18n'
languages = ['en', 'de']
exclude_files = ['participationState.json', 'ltiOutcomeUrl.json', 'dragAndDropAssignment.json']  # todo recheck this


def find_translation_files_in_directory(directory):
    translation_files = []

    for _, _, files in os.walk(directory):
        for file in files:
            if file not in exclude_files:
                translation_files.append(file)

    return translation_files


def find_translation_files():
    translation_files = {}
    for language in languages:
        translation_files[language] = find_translation_files_in_directory(f'./src/main/webapp/i18n/{language}')
    return translation_files


def find_missing_translation_files(translation_files):
    all_files = set()
    for files in translation_files.values():
        all_files.update(files)

    missing_files = {language: [] for language in languages}
    for file in all_files:
        for language in languages:
            if file not in translation_files[language]:
                missing_files[language].append(file)

    return missing_files


def check_if_file_exists_for_all_languages(translation_files):
    logging.info('Checking if translation files exist for all languages')
    missing_files = find_missing_translation_files(translation_files)

    passed = True

    for language, files in missing_files.items():
        if len(files) > 0:
            logging.error(f'Missing translation files for {language}: {files}')
            passed = False
        else:
            logging.info(f'All translation files for {language} exist')

    if not passed:
        sys.exit(1)


def read_translation_files_in_directory(directory):
    translation_data = {}

    for root, _, files in os.walk(directory):
        for file in files:
            if file in exclude_files:
                continue
            if file.endswith('.json'):
                file_path = os.path.join(root, file)
                with open(file_path, 'r', encoding='utf-8') as f:
                    translation_data[file] = json.load(f)

    return translation_data


def get_keys(json):
    keys = []
    for key in json.keys():
        if type(json[key]) == dict:
            keys.extend([f'{key}.{sub_key}' for sub_key in get_keys(json[key])])
        else:
            keys.append(key)
    return keys


def get_all_keys(translation_data, file):
    all_keys = set()
    for language in languages:
        all_keys.update(get_keys(translation_data[language][file]))
    return all_keys


def get_value(translation_data, language, file, key):
    keys = key.split('.')
    value = translation_data[language][file]
    for key in keys:
        value = value[key]
    return value


def read_translation_files():
    translation_data = {}
    for language in languages:
        translation_data[language] = read_translation_files_in_directory(f'{translation_file_dir}/{language}')
    return translation_data


def check_consistency_of_translation_for_file(translation_data, file):
    passed = True

    all_keys = get_all_keys(translation_data, file)

    for language in languages:
        keys = get_keys(translation_data[language][file])
        for key in all_keys:
            if key not in keys:
                logging.error(f'Missing key {key} in {language} translation file {file}')
                passed = False
        if len(keys) != len(all_keys):
            for key in keys:
                if key not in all_keys:
                    logging.error(f'Extra key {key} in {language} translation file {file}')
                    passed = False

    return passed


def check_consistency_of_translation_files(translation_data):
    logging.info('Checking consistency of translation files')

    files = translation_data[languages[0]].keys()

    passed = True
    for file in files:
        passed &= check_consistency_of_translation_for_file(translation_data, file)

    if not passed:
        sys.exit(1)


def check_duplicate_values_for_file(translation_data, language, file):
    values = {}
    keys = get_keys(translation_data[language][file])
    passed = True
    for key in keys:
        value = get_value(translation_data, language, file, key)
        if value in values:
            logging.error(
                f'Duplicate value {value} for key {key} and {values[value]} in {language} translation file {file}')
            passed = False
        values[value] = key
    return passed


def get_value_to_key_map(translation_data, language, file):
    keys = get_keys(translation_data[language][file])
    value_to_key_map = {}
    for key in keys:
        value = get_value(translation_data, language, file, key)
        value_to_key_map[value] = key
    return value_to_key_map


def check_duplicate_values_for_files(translation_data, language, file1, file2):
    value_to_key_map1 = get_value_to_key_map(translation_data, language, file1)
    value_to_key_map2 = get_value_to_key_map(translation_data, language, file2)
    passed = False
    for value in value_to_key_map1.keys():
        if value in value_to_key_map2:
            logging.error(
                f'Duplicate value {value} for keys {value_to_key_map1[value]} and {value_to_key_map2[value]} in {language} translation files {file1} and {file2}')
            passed = False
    return passed


def check_duplicate_values_for_language(translation_data, language):
    passed = True
    for file in translation_data[language].keys():
        passed &= check_duplicate_values_for_file(translation_data, language, file)

    files = list(translation_data[language].keys())
    for i in range(len(files)):
        for j in range(i + 1, len(files)):
            passed &= check_duplicate_values_for_files(translation_data, language, files[i], files[j])

    return passed


def check_duplicate_values(translation_data):
    logging.info('Checking for duplicate values in translation files')
    passed = True
    for language in languages:
        check_duplicate_values_for_language(translation_data, language);

    if not passed:
        sys.exit(1)


def main():
    translation_files = find_translation_files()

    print(translation_files)

    check_if_file_exists_for_all_languages(translation_files)
    translation_data = read_translation_files()
    check_consistency_of_translation_files(translation_data)
    check_duplicate_values(translation_data)


if __name__ == "__main__":
    main()
