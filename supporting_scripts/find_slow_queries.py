import os
import re

# Configuration
MAX_FETCH_THRESHOLD = 5  # You can adjust this threshold
SEARCH_DIRECTORIES = ["./src/main/java", "./src/main/kotlin"]  # Paths to scan

# Regex Patterns
entitygraph_pattern = re.compile(r'@EntityGraph\s*\(.*?attributePaths\s*=\s*\{([^\}]*)\}', re.DOTALL)
query_pattern = re.compile(r'@Query\s*\(\s*"""(.*?)"""', re.DOTALL | re.MULTILINE)
join_fetch_pattern = re.compile(r'JOIN\s+FETCH\s+\S+', re.IGNORECASE)

def scan_file(file_path):
    with open(file_path, 'r', encoding='utf-8', errors='ignore') as file:
        content = file.read()

        # Check for @EntityGraph
        for match in entitygraph_pattern.finditer(content):
            paths = match.group(1)
            path_count = len(re.findall(r'"[^"]+"', paths))
            if path_count > MAX_FETCH_THRESHOLD:
                print(f"\n[EntityGraph] Potential over-fetch in {file_path} ({path_count} fetches):\n{match.group(0)}")

        # Check for @Query
        for match in query_pattern.finditer(content):
            query_text = match.group(1)
            fetch_count = len(join_fetch_pattern.findall(query_text))
            if fetch_count > MAX_FETCH_THRESHOLD:
                print(f"\n[@Query] Potential over-fetch in {file_path} ({fetch_count} JOIN FETCHes):\n{query_text.strip()}")

def scan_directory(directory):
    for root, _, files in os.walk(directory):
        for file in files:
            if file.endswith(".java") or file.endswith(".kt"):
                scan_file(os.path.join(root, file))

# Run the scan
for directory in SEARCH_DIRECTORIES:
    scan_directory(directory)

print("\nScan complete.")
