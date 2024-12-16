import os
import xml.etree.ElementTree as ET
import argparse

def process_reports(input_directory, output_file):
    results = []

    for module_folder in os.listdir(input_directory):
        module_path = os.path.join(input_directory, module_folder)

        if os.path.isdir(module_path):
            report_file = os.path.join(module_path, f"jacocoCoverageReport.xml")

            if os.path.exists(report_file):
                try:
                    tree = ET.parse(report_file)
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
                        "module": module_folder,
                        "instruction_coverage": instruction_coverage,
                        "missed_classes": missed_classes
                    })
                except Exception as e:
                    print(f"Error processing {module_folder}: {e}")

    results = sorted(results, key=lambda x: x['module'])

    with open(output_file, "w") as f:
        f.write("## Coverage Results\n\n")
        f.write("| Module Name | Instruction Coverage (%) | Missed Classes |\n")
        f.write("|-------------|---------------------------|----------------|\n")
        for result in results:
            f.write(f"| {result['module']} | {result['instruction_coverage']:.2f} | {result['missed_classes']} |\n")

    print(f"Coverage results written to {output_file}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Process JaCoCo coverage reports.")
    parser.add_argument("input_directory", type=str, help="Root directory containing JaCoCo coverage reports")
    parser.add_argument("output_file", type=str, help="Output file to save the coverage results")

    args = parser.parse_args()

    process_reports(args.input_directory, args.output_file)
