import os
import subprocess
import json

# Function to run cloc and get metrics, install cloc before using `brew install cloc` (macOS) or `sudo apt install cloc` (Linux)
def run_cloc(directory):
    try:
        result = subprocess.run(
            ['cloc', '--json', directory],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        cloc_data = json.loads(result.stdout)
        if 'header' in cloc_data:
            # Sum all code lines from cloc output
            total_lines = sum(lang_info['code'] for lang_info in cloc_data.values() if isinstance(lang_info, dict) and 'code' in lang_info)
            return total_lines
        return 0
    except Exception as e:
        print(f"Error running cloc: {e}")
        return None

# Function to count the number of classes and methods in a Java file
def count_classes_methods(file_path):
    class_count = 0
    method_count = 0
    with open(file_path, 'r') as f:
        for line in f:
            line = line.strip()
            # Check for classes, enums, interfaces, or records
            if any(keyword in line for keyword in ['class ', 'enum ', 'interface ', 'record ']):
                class_count += 1
            if '(' in line and ')' in line and ('{' in line or ';' in line):
                method_count += 1
    return class_count, method_count

# Function to analyze individual module
def analyze_module(module_path):
    total_files = 0
    total_classes = 0
    total_methods = 0

    for root, dirs, files in os.walk(module_path):
        java_files = [f for f in files if f.endswith('.java')]
        total_files += len(java_files)

        for file in java_files:
            file_path = os.path.join(root, file)
            classes, methods = count_classes_methods(file_path)
            total_classes += classes
            total_methods += methods

    # Run cloc for lines of code and sum them up
    total_loc = run_cloc(module_path)

    return {
        'total_files': total_files,
        'total_classes': total_classes,
        'total_methods': total_methods,
        'total_loc': total_loc
    }

# Function to dynamically detect all subfolders (modules) in the base directory
def detect_modules(base_directory):
    modules = []
    for item in os.listdir(base_directory):
        item_path = os.path.join(base_directory, item)
        if os.path.isdir(item_path):
            modules.append(item)
    return sorted(modules)  # Sort the modules alphabetically

# Function to analyze all detected modules
def analyze_all_modules(base_directory):
    modules = detect_modules(base_directory)
    results = {}
    for module in modules:
        module_path = os.path.join(base_directory, module)
        if os.path.exists(module_path):
            print(f"Analyzing module: {module}")
            results[module] = analyze_module(module_path)
        else:
            print(f"Module path does not exist: {module_path}")
    return results

# Specify the base directory of your codebase
base_directory = '../src/main/java/de/tum/cit/aet/artemis'

# Analyze all dynamically detected modules
module_metrics = analyze_all_modules(base_directory)

# Print the results for each module
for module, metrics in module_metrics.items():
    print(f"\nMetrics for module: {module}")
    print(f"Java Files: {metrics['total_files']}")
    print(f"Classes: {metrics['total_classes']} (includes enums, interfaces, records)")
    print(f"Methods: {metrics['total_methods']}")
    print(f"Lines of Code: {metrics['total_loc']}")
