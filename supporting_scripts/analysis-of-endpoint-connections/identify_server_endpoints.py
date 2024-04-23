import os
import re
import sys

# Annotations used on server sided endpoints
annotations = ['@GetMapping', '@PostMapping', '@PutMapping', '@DeleteMapping']

# Regex pattern to find the mapping URLs
pattern = re.compile(r'@.*Mapping\("(.*?)"\)')

# Get the list of files from the command line arguments
files = sys.argv[1:]

# Scan each file
for filepath in files:
    # Only scan .java files
    if filepath.endswith('.java'):
        with open(filepath, 'r') as file:
            for line in file:
                # If the line contains one of the annotations
                if any(annotation in line for annotation in annotations):
                    # Find the URL in the annotation
                    match = pattern.search(line)
                    if match:
                        print(f'Endpoint found: {match.group(1)} in file {filepath}')
