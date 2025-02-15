import os
import xml.etree.ElementTree as ET
import argparse

def get_report_by_module(input_directory):
    results = []
    if not os.path.exists(input_directory):
        print(f"Directory {input_directory} does not exist. Skipping...")
        return results

    for module_folder in os.listdir(input_directory):
        module_path = os.path.join(input_directory, module_folder)

        if os.path.isdir(module_path):
            report_file = os.path.join(module_path, f"jacocoTestReport.xml")

            if os.path.exists(report_file):
                results.append({
                    "module": module_folder,
                    "report_file": report_file
                })
            else:
                print(f"No XML report file found for module: {module_folder}. Skipping...")

    return results


def extract_coverage(reports):
    results = []

    for report in reports:
        try:
            tree = ET.parse(report['report_file'])
            root = tree.getroot()

            instruction_counter = root.find("./counter[@type='INSTRUCTION']")
            class_counter = root.find("./counter[@type='CLASS']")

            if instruction_counter == None or class_counter == None:
                continue

            instruction_covered = int(instruction_counter.get('covered', 0))
            instruction_missed = int(instruction_counter.get('missed', 0))
            total_instructions = instruction_covered + instruction_missed
            instruction_coverage = (instruction_covered / total_instructions * 100) if total_instructions > 0 else 0.0

            missed_classes = int(class_counter.get('missed', 0))

            results.append({
                "module": report['module'],
                "instruction_coverage": instruction_coverage,
                "missed_classes": missed_classes
            })
        except Exception as e:
            print(f"Error processing {report['module']}: {e}")

    results = sorted(results, key=lambda x: x['module'])
    return results


def write_summary_to_file(coverage_by_module, output_file):
    with open(output_file, "w") as f:
        f.write("## Coverage Results\n\n")
        f.write("| Module Name | Instruction Coverage (%) | Missed Classes |\n")
        f.write("|-------------|---------------------------|----------------|\n")
        for result in coverage_by_module:
            f.write(f"| {result['module']} | {result['instruction_coverage']:.2f} | {result['missed_classes']} |\n")
        f.write("\n**Note**: the module with the name `aggregated` represents the aggregated coverage of all executed test modules.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Process JaCoCo coverage reports.")
    parser.add_argument("input_directory", type=str, help="Root directory containing JaCoCo coverage reports")
    parser.add_argument("output_file", type=str, help="Output file to save the coverage results")

    args = parser.parse_args()

    reports = get_report_by_module(args.input_directory)
    coverage_by_module = extract_coverage(reports)
    write_summary_to_file(coverage_by_module, args.output_file)
