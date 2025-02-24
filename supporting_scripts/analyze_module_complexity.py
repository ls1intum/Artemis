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

# Function to count REST Controllers, services, DTOs, and entity classes
def analyze_java_file(file_path):
    rest_controller_count = 0
    service_count = 0
    entity_count = 0
    repository_count = 0
    record_dto_count = 0
    class_dto_count = 0
    http_methods = {
        'GET': 0,
        'PUT': 0,
        'POST': 0,
        'PATCH': 0,
        'DELETE': 0
    }
    queries = {
        '@Query': 0,
        '@EntityGraph': 0
    }

    with open(file_path, 'r') as f:
        for line in f:
            line = line.strip()
            if '@RestController' in line:
                rest_controller_count += 1
            if '@Service' in line:
                service_count += 1
            if '@Entity' in line:
                entity_count += 1
            if '@Repository' in line:
                repository_count += 1
            if 'class ' in line and 'DTO' in line:
                class_dto_count += 1
            if 'record ' in line and 'DTO' in line:
                record_dto_count += 1
            if '@GetMapping' in line or 'RequestMethod.GET' in line:
                http_methods['GET'] += 1
            if '@PutMapping' in line or 'RequestMethod.PUT' in line:
                http_methods['PUT'] += 1
            if '@PostMapping' in line or 'RequestMethod.POST' in line:
                http_methods['POST'] += 1
            if '@PatchMapping' in line or 'RequestMethod.PATCH' in line:
                http_methods['PATCH'] += 1
            if '@DeleteMapping' in line or 'RequestMethod.DELETE' in line:
                http_methods['DELETE'] += 1
            if '@Query' in line:
                queries['@Query'] += 1
            if '@EntityGraph' in line:
                queries['@EntityGraph'] += 1

    return {
        'rest_controller_count': rest_controller_count,
        'service_count': service_count,
        'repository_count': repository_count,
        'dto_count': class_dto_count + record_dto_count,
        'record_dto_count': record_dto_count,
        'class_dto_count': class_dto_count,
        'entity_count': entity_count,
        'http_methods': http_methods,
        'queries': queries
    }

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
    total_services = 0
    total_repositories = 0
    total_dtos = 0
    total_record_dtos = 0
    total_class_dtos = 0
    total_entities = 0
    total_rest_controllers = 0
    http_methods = {
        'GET': 0,
        'PUT': 0,
        'POST': 0,
        'PATCH': 0,
        'DELETE': 0
    }
    queries = {
        '@Query': 0,
        '@EntityGraph': 0
    }

    for root, dirs, files in os.walk(module_path):
        java_files = [f for f in files if f.endswith('.java')]
        total_files += len(java_files)

        for file in java_files:
            file_path = os.path.join(root, file)
            classes, methods = count_classes_methods(file_path)
            total_classes += classes
            total_methods += methods

            analysis = analyze_java_file(file_path)
            total_rest_controllers += analysis['rest_controller_count']
            total_services += analysis['service_count']
            total_repositories += analysis['repository_count']
            total_entities += analysis['entity_count']
            total_dtos += analysis['dto_count']
            total_record_dtos += analysis['record_dto_count']
            total_class_dtos += analysis['class_dto_count']

            for method in http_methods:
                http_methods[method] += analysis['http_methods'][method]

            for query in queries:
                queries[query] += analysis['queries'][query]

    # Run cloc for lines of code
    total_loc = run_cloc(module_path)

    return {
        'total_files': total_files,
        'total_classes': total_classes,
        'total_methods': total_methods,
        'total_loc': total_loc,
        'total_rest_controllers': total_rest_controllers,
        'total_services': total_services,
        'total_repositories': total_repositories,
        'total_entities': total_entities,
        'total_dtos': total_dtos,
        'record_dtos': total_record_dtos,
        'class_dtos': total_class_dtos,
        'http_methods': http_methods,
        'queries': queries
    }

# Function to dynamically detect all subfolders (modules) in the base directory
def detect_modules(base_directory):
    modules = []
    for item in os.listdir(base_directory):
        item_path = os.path.join(base_directory, item)
        if os.path.isdir(item_path):
            modules.append(item)
    return sorted(modules)  # Sort the modules alphabetically

# Function to calculate cyclomatic complexity
def calculate_cyclomatic_complexity(file_path):
    complexity = 1  # Start at 1 for the method itself
    decision_points = ['if', 'for', 'while', 'case', '&&', '||', 'catch', 'switch']

    with open(file_path, 'r') as f:
        for line in f:
            line = line.strip()
            # Check for decision points to increment complexity
            if any(dp in line for dp in decision_points):
                complexity += 1
    return complexity

# Function to count methods in a Java file
def count_methods(file_path):
    method_count = 0
    with open(file_path, 'r') as f:
        for line in f:
            line = line.strip()
            # Count lines that define methods
            if '(' in line and ')' in line and ('{' in line or ';' in line):
                method_count += 1
    return method_count

# Updated function to analyze services and entities
def analyze_services_entities(module_path):
    service_complexities = []
    entity_complexities = []

    for root, dirs, files in os.walk(module_path):
        java_files = [f for f in files if f.endswith('.java')]

        for file in java_files:
            file_path = os.path.join(root, file)

            # Read file content to look for annotations
            with open(file_path, 'r') as f:
                file_content = f.read()

            # Check if it's a service or an entity by looking for the annotation inside the file
            if '@Service' in file_content:
                complexity = calculate_cyclomatic_complexity(file_path)
                method_count = count_methods(file_path)
                loc = run_cloc(file_path)  # Optional, if you want to include lines of code
                service_complexities.append({
                    'file': file,
                    'complexity': complexity,
                    'methods': method_count,
                    'loc': loc
                })

            elif '@Entity' in file_content:
                complexity = calculate_cyclomatic_complexity(file_path)
                method_count = count_methods(file_path)
                loc = run_cloc(file_path)  # Optional, if you want to include lines of code
                entity_complexities.append({
                    'file': file,
                    'complexity': complexity,
                    'methods': method_count,
                    'loc': loc
                })

    return service_complexities, entity_complexities

# Function to calculate the average of multiple values (complexity, methods, loc) and round them
def calculate_average_metrics(items):
    if not items:
        return 0, 0, 0
    total_complexity = sum(item['complexity'] for item in items)
    total_methods = sum(item['methods'] for item in items)
    total_loc = sum(item['loc'] for item in items)
    count = len(items)

    # Round the averages to one decimal place
    return round(total_complexity / count, 1), round(total_methods / count, 1), round(total_loc / count, 1)

# Updated function to analyze individual module and include complexity
def analyze_module_with_complexity(module_path):
    module_metrics = analyze_module(module_path)

    avg_service_complexity, avg_entity_complexity = analyze_services_entities(module_path)

    module_metrics['avg_service_complexity'] = avg_service_complexity
    module_metrics['avg_entity_complexity'] = avg_entity_complexity

    return module_metrics

# Function to analyze all modules
def analyze_all_modules(base_directory):
    modules = detect_modules(base_directory)
    results = {}

    for module in modules:
        module_path = os.path.join(base_directory, module)
        if os.path.exists(module_path):
            print(f"Analyzing module: {module}")
            results[module] = analyze_module(module_path)
            service_complexities, entity_complexities = analyze_services_entities(module_path)
            results[module].update({
                'service_complexities': service_complexities,
                'entity_complexities': entity_complexities,
            })
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
    print(f"REST Controllers: {metrics['total_rest_controllers']}")

    # Calculate averages for services
    avg_service_complexity, avg_service_methods, avg_service_loc = calculate_average_metrics(metrics['service_complexities'])
    # Calculate averages for entities
    avg_entity_complexity, avg_entity_methods, avg_entity_loc = calculate_average_metrics(metrics['entity_complexities'])
    print(f"Services: {metrics['total_services']}")
    # Print the average metrics for services
    print(f"Average Service Complexity: cyclic: {avg_service_complexity}, methods: {avg_service_methods}, loc: {avg_service_loc}")

    print(f"Repositories: {metrics['total_repositories']}")
    print(f"Database Queries: {metrics['queries']}")

    print(f"Entities: {metrics['total_entities']}")
    # Print the average metrics for entities
    print(f"Average Entity Complexity: cyclic: {avg_entity_complexity}, methods: {avg_entity_methods}, loc: {avg_entity_loc}")

    print(f"DTOs: {metrics['total_dtos']} (Records: {metrics['record_dtos']}, Classes: {metrics['class_dtos']})")
    print(f"HTTP Methods: {metrics['http_methods']}")
    # print("Services:")
    # for service in metrics['service_complexities']:
    #     print(f"  Service: {service['file']}, Complexity: {service['complexity']}, Methods: {service['methods']}, LOC: {service['loc']}")
    # print("Entities:")
    # for entity in metrics['entity_complexities']:
    #     print(f"  Entity: {entity['file']}, Complexity: {entity['complexity']}, Methods: {entity['methods']}, LOC: {entity['loc']}")
