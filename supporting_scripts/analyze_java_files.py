import os
import re
import argparse
from collections import defaultdict

def extract_module(path, base_dir):
    # Extract the subfolder name after de/tum/cit/aet/artemis
    relative_path = os.path.relpath(path, base_dir)
    parts = relative_path.split(os.sep)
    return parts[0] if parts else "root"

def analyze_java_files(base_dir, max_lines=1000, max_params=10):
    large_classes = []
    complex_beans = []
    large_class_counts = defaultdict(int)
    complex_bean_counts = defaultdict(int)

    for root, _, files in os.walk(base_dir):
        for file in files:
            if file.endswith(".java"):
                path = os.path.join(root, file)
                with open(path, encoding='utf-8') as f:
                    code = f.read()

                module = extract_module(path, base_dir)

                # Check for large classes
                lines = code.splitlines()
                if len(lines) > max_lines:
                    large_classes.append((path, len(lines)))
                    large_class_counts[module] += 1

                # Check for Spring beans with complex constructors
                if re.search(r'@(Component|Service|Repository|Controller|Bean|RestController)', code):
                    constructors = re.findall(r'(public|protected)\s+\w+\s*\(([^)]*)\)', code)
                    for _, params in constructors:
                        param_count = len([p for p in params.split(',') if p.strip()])
                        if param_count > max_params:
                            complex_beans.append((path, param_count))
                            complex_bean_counts[module] += 1
                            break

    # Sort results
    large_classes.sort(key=lambda x: x[1], reverse=True)
    complex_beans.sort(key=lambda x: x[1], reverse=True)

    return large_classes, complex_beans, large_class_counts, complex_bean_counts

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Analyze Java code for large classes and complex Spring beans.")
    parser.add_argument("--dir", default="src/main/java/de/tum/cit/aet/artemis", help="Base directory to analyze")
    # TODO in the future, we want to lower those thresholds to 800 and 8
    parser.add_argument("--max-lines", type=int, default=1000, help="Maximum allowed lines per class")
    parser.add_argument("--max-params", type=int, default=10, help="Maximum allowed constructor parameters")
    args = parser.parse_args()

    base_dir = args.dir
    large_classes, complex_beans, large_counts, bean_counts = analyze_java_files(base_dir, args.max_lines, args.max_params)

    # Print summary
    print(f"\nFound {len(large_classes)} classes with more than {args.max_lines} lines:")
    for module, count in large_counts.items():
        print(f" - {module}: {count}")

    for path, lines in large_classes:
        print(f"{path}: {lines} lines")

    print(f"\nFound {len(complex_beans)} Spring Beans with constructors having more than {args.max_params} parameters:")
    for module, count in bean_counts.items():
        print(f" - {module}: {count}")

    for path, params in complex_beans:
        print(f"{path}: {params} parameters")
