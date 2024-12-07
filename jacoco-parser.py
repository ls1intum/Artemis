import xml.etree.ElementTree as ET

def parse_jacoco_report(xml_file, package_path):
    """
    Parse a JaCoCo XML report to extract coverage metrics for a specific package.

    :param xml_file: Path to the JaCoCo XML report.
    :param package_path: The package path to search for.
    :return: A dictionary containing coverage metrics for the package.
    """
    tree = ET.parse(xml_file)
    root = tree.getroot()

    # Search for the specific package
    for package in root.findall(".//package"):
        if package.get('name') == package_path:
            coverage_metrics = {}
            # Extract coverage metrics from <counter> elements
            for counter in package.findall("counter"):
                metric = counter.get("type")
                covered = int(counter.get("covered"))
                missed = int(counter.get("missed"))
                total = covered + missed
                coverage_percentage = (covered / total) * 100 if total > 0 else 0
                coverage_metrics[metric] = {
                    "covered": covered,
                    "missed": missed,
                    "total": total,
                    "coverage_percentage": coverage_percentage
                }
            return coverage_metrics

    return None

xml_file_path = "build/reports/jacoco/test/jacocoTestReport.xml"
package = "de/tum/cit/aet/artemis/buildagent"
metrics = parse_jacoco_report(xml_file_path, package)

if metrics:
    print(f"Coverage metrics for package '{package}':")
    for metric, data in metrics.items():
        print(f"{metric}: {data['coverage_percentage']:.2f}% (Covered: {data['covered']}, Missed: {data['missed']}, Total: {data['total']})")
else:
    print(f"Package '{package}' not found in the report.")
