import os
import re
import argparse
import subprocess
import sys
from collections import defaultdict
from typing import List, Tuple, Set

SEPARATOR_WIDTH = 42
SEPARATOR_CHAR = "="

def print_separator(char: str = SEPARATOR_CHAR, width: int = SEPARATOR_WIDTH):
    """Print a separator line."""
    print(char * width)

def print_analysis_results(violations: List[Tuple[str, int]], module_counts: dict, threshold: int, description: str, unit: str):
    """Print analysis results for a specific violation type."""
    print(f"\nFound {len(violations)} {description} more than {threshold} {unit}:")
    for module, count in module_counts.items():
        print(f" - {module}: {count}")
    for path, value in violations:
        print(f"{path}: {value} {unit}")

def print_threshold_result(violation_count: int, threshold: int, description: str, violation_type: str):
    """Print the result of a threshold check."""
    if violation_count > threshold:
        if violation_type == "complex_beans":
            excess = violation_count - threshold
            print(f"❌ Too many {description}: {violation_count} > {threshold} ({excess} beans need to be refactored)")
        else:
            print(f"❌ Too many {description}: {violation_count} > {threshold}")
        return True
    else:
        print(f"✅ {description.capitalize()} within threshold: {violation_count} <= {threshold}")
        return False

def print_local_run_instructions(args):
    """Print instructions for running the script locally with the same parameters."""
    print()
    print_separator()
    print("TO RUN THIS CHECK LOCALLY:")
    print_separator()
    print("Execute the following command from the repository root:")
    print()

    cmd_parts = ["python supporting_scripts/analyze_java_files.py"]

    if args.dir != 'src/main/java/de/tum/cit/aet/artemis':
        cmd_parts.append(f"--dir {args.dir}")
    if args.max_lines != 1000:
        cmd_parts.append(f"--max-lines {args.max_lines}")
    if args.max_params != 10:
        cmd_parts.append(f"--max-params {args.max_params}")
    if args.include_repo_deps:
        cmd_parts.append("--include-repo-deps")
    if args.max_large_classes is not None:
        cmd_parts.append(f"--max-large-classes {args.max_large_classes}")
    if args.max_complex_beans is not None:
        cmd_parts.append(f"--max-complex-beans {args.max_complex_beans}")
    if args.event_type != 'push':
        cmd_parts.append(f"--event-type {args.event_type}")
    if args.base_branch != 'develop':
        cmd_parts.append(f"--base-branch {args.base_branch}")

    # Print as a single line if short, multiline if long
    full_command = " ".join(cmd_parts)
    if len(full_command) <= 80:
        print(full_command)
    else:
        print(cmd_parts[0] + " \\")
        for part in cmd_parts[1:-1]:
            print(f"  {part} \\")
        print(f"  {cmd_parts[-1]}")

    print()
    print_separator()

def extract_module(path, base_dir):
    # Extract the subfolder name after de/tum/cit/aet/artemis
    relative_path = os.path.relpath(path, base_dir)
    parts = relative_path.split(os.sep)
    return parts[0] if parts else "root"

def analyze_java_files(base_dir, max_lines=1000, max_params=10, include_repo_deps=False):
    large_classes = []
    complex_beans = []
    large_class_counts = defaultdict(int)
    complex_bean_counts = defaultdict(int)

    bean_annotations = ['Component', 'Service', 'Controller', 'Bean', 'RestController', 'Repository']
    annotation_pattern = re.compile(r'@(?:' + '|'.join(bean_annotations) + r')\b')

    for root, _, files in os.walk(base_dir):
        for file in files:
            if not file.endswith('.java'):
                continue

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
            if annotation_pattern.search(code):
                constructors = re.findall(r'(?:public|protected)\s+\w+\s*\(([^)]*)\)', code)
                for params in constructors:
                    # split parameters and strip whitespace
                    param_list = [p.strip() for p in params.split(',') if p.strip()]
                    if not include_repo_deps:
                        # remove parameters whose type ends with 'Repository'
                        param_list = [p for p in param_list if not re.search(r'\b\w+Repository\b', p)]
                    if len(param_list) > max_params:
                        complex_beans.append((path, len(param_list)))
                        complex_bean_counts[module] += 1
                        break

    # Sort results
    large_classes.sort(key=lambda x: x[1], reverse=True)
    complex_beans.sort(key=lambda x: x[1], reverse=True)

    return large_classes, complex_beans, large_class_counts, complex_bean_counts

def get_changed_java_files(event_type: str, base_branch: str) -> Set[str]:
    """Get the list of changed Java files in a PR."""
    if event_type != 'pull_request':
        return set()

    try:
        print(f"Checking files changed in PR against base branch: {base_branch}", file=sys.stderr)
        result = subprocess.run(
            ['git', 'diff', '--name-only', f'origin/{base_branch}...HEAD'],
            capture_output=True,
            text=True,
            check=True
        )
        changed_files = {
            line.strip() for line in result.stdout.splitlines()
            if line.strip().endswith('.java')
        }

        print("Changed Java files in this PR:", file=sys.stderr)
        if changed_files:
            for file in sorted(changed_files):
                print(f"  - {file}", file=sys.stderr)
        else:
            print("  (none)", file=sys.stderr)
        print("", file=sys.stderr)

        return changed_files
    except subprocess.CalledProcessError as e:
        print(f"Error getting changed files: {e}", file=sys.stderr)
        return set()

def is_path_in_changed_files(path: str, changed_files: Set[str]) -> bool:
    """Check if a path matches any of the changed files."""
    normalized_path = os.path.normpath(path)
    return normalized_path in changed_files or any(normalized_path.endswith(changed_file) for changed_file in changed_files)

def check_violations_in_changed_files(violations: List[Tuple[str, int]], changed_files: Set[str]) -> bool:
    """Check if any violations are in the changed files."""
    if not changed_files:
        return False

    for path, _ in violations:
        if is_path_in_changed_files(path, changed_files):
            return True
    return False

def display_pr_violation_details(violation_type: str, violations: List[Tuple[str, int]], changed_files: Set[str]):
    """Display violations that were introduced in the PR."""
    if violation_type == "large_classes":
        print("❌ Files you edited that are too large:")
        unit = "lines"
    else:
        print("❌ Files you edited that have too many constructor parameters:")
        unit = "parameters"

    for path, value in violations:
        if is_path_in_changed_files(path, changed_files):
            normalized_path = os.path.normpath(path)
            print(f"  - {normalized_path}: {value} {unit}")
    print()

def display_pr_context(
    event_type: str,
    large_class_violation: bool,
    complex_bean_violation: bool,
    pr_has_large_violations: bool,
    pr_has_complex_violations: bool,
    large_classes: List[Tuple[str, int]],
    complex_beans: List[Tuple[str, int]],
    changed_files: Set[str],
    max_large_classes: int,
    max_complex_beans: int
):
    """Display context-specific messages for PR or push events."""
    print_separator()

    if event_type == 'pull_request':
        if not pr_has_large_violations and not pr_has_complex_violations:
            print("ℹ️  None of the violating files were modified in this PR.")
            print("The violations likely come from another PR that was merged recently.")
            print()
            print("You can bump the thresholds in .github/workflows/quality.yml:")
            if large_class_violation:
                print(f"  - max_large_classes: {max_large_classes} → {len(large_classes)}")
            if complex_bean_violation:
                print(f"  - max_complex_beans: {max_complex_beans} → {len(complex_beans)}")
            print()
            print("Before bumping the threshold, please make sure you have merged the latest develop version into your feature branch.")
        else:
            print("⚠️  ATTENTION: Your PR modified files that are violating the quality standards!")
            print()
            if pr_has_large_violations:
                display_pr_violation_details("large_classes", large_classes, changed_files)
            if pr_has_complex_violations:
                display_pr_violation_details("complex_beans", complex_beans, changed_files)
            print("Please refactor these classes to meet the code quality standards!")
    else:
        print("Please refactor the classes listed above to meet the code quality standards!")

    print_separator()

if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description='Analyze Java code for large classes and complex Spring beans.'
    )
    parser.add_argument(
        '--dir',
        default='src/main/java/de/tum/cit/aet/artemis',
        help='Base directory to analyze'
    )
    # TODO in the future, we want to lower those thresholds to 800 and 8
    parser.add_argument(
        '--max-lines',
        type=int,
        default=1000,
        help='Maximum allowed lines per class'
    )
    parser.add_argument(
        '--max-params',
        type=int,
        default=10,
        help='Maximum allowed constructor parameters'
    )
    parser.add_argument(
        '--include-repo-deps',
        action='store_true',
        help='Include constructor parameters whose type ends with "Repository" in the dependency count',
        dest='include_repo_deps'
    )
    parser.add_argument(
        '--max-large-classes',
        type=int,
        default=None,
        help='Maximum allowed number of large classes (fails if exceeded)'
    )
    parser.add_argument(
        '--max-complex-beans',
        type=int,
        default=None,
        help='Maximum allowed number of complex beans (fails if exceeded)'
    )
    parser.add_argument(
        '--event-type',
        type=str,
        default='push',
        choices=['push', 'pull_request'],
        help='GitHub event type (push or pull_request)'
    )
    parser.add_argument(
        '--base-branch',
        type=str,
        default='develop',
        help='Base branch for PR diff comparison'
    )
    args = parser.parse_args()

    # Run the analysis
    large_classes, complex_beans, large_counts, bean_counts = analyze_java_files(
        args.dir,
        max_lines=args.max_lines,
        max_params=args.max_params,
        include_repo_deps=args.include_repo_deps
    )

    # Print initial analysis results
    print_analysis_results(large_classes, large_counts, args.max_lines, "classes with", "lines")
    print_analysis_results(complex_beans, bean_counts, args.max_params, "Spring beans with constructors having", "parameters")

    # If no thresholds specified, just print results and exit
    if args.max_large_classes is None and args.max_complex_beans is None:
        sys.exit(0)

    # Check thresholds
    print()
    print_separator()
    print("CODE QUALITY ANALYSIS SUMMARY")
    print_separator()
    print(f"Large classes found: {len(large_classes)} (max allowed: {args.max_large_classes})")
    print(f"Complex beans found: {len(complex_beans)} (max allowed: {args.max_complex_beans})")
    print()

    large_class_violation = print_threshold_result(len(large_classes), args.max_large_classes, "large classes", "large_classes")
    complex_bean_violation = print_threshold_result(len(complex_beans), args.max_complex_beans, "complex beans", "complex_beans")

    print_separator()

    # If violations found, check PR context and display details
    if large_class_violation or complex_bean_violation:
        print("::error::Code quality check failed")
        print()

        # Get changed files for PR context
        changed_files = get_changed_java_files(args.event_type, args.base_branch)

        # Check if violations are in PR changes
        pr_has_large_violations = check_violations_in_changed_files(large_classes, changed_files) if large_class_violation else False
        pr_has_complex_violations = check_violations_in_changed_files(complex_beans, changed_files) if complex_bean_violation else False

        display_pr_context(
            args.event_type,
            large_class_violation,
            complex_bean_violation,
            pr_has_large_violations,
            pr_has_complex_violations,
            large_classes,
            complex_beans,
            changed_files,
            args.max_large_classes,
            args.max_complex_beans
        )

        print_local_run_instructions(args)

        sys.exit(1)
    else:
        print("✅ Code quality check passed")
        sys.exit(0)
