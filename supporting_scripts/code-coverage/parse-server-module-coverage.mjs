#!/usr/bin/env node

import fs from 'fs';
import path from 'path';

const [inputDirectory, outputFile] = process.argv.slice(2);

if (!inputDirectory || !outputFile) {
    console.error('Usage: node parse-server-module-coverage.mjs <input_directory> <output_file>');
    process.exit(1);
}

function getReportByModule(inputDir) {
    const results = [];
    if (!fs.existsSync(inputDir)) {
        console.log(`Directory ${inputDir} does not exist. Skipping...`);
        return results;
    }

    for (const moduleFolder of fs.readdirSync(inputDir)) {
        const modulePath = path.join(inputDir, moduleFolder);
        if (!fs.statSync(modulePath).isDirectory()) {
            continue;
        }

        const reportFile = path.join(modulePath, 'jacocoTestReport.xml');
        if (fs.existsSync(reportFile)) {
            results.push({ module: moduleFolder, reportFile });
        } else {
            console.log(`No XML report file found for module: ${moduleFolder}. Skipping...`);
        }
    }

    return results;
}

function parseCounter(xmlContent, type) {
    const regex = new RegExp(`<counter[^>]*type=\"${type}\"[^>]*>`, 'gi');
    const matches = [...xmlContent.matchAll(regex)];
    if (matches.length === 0) {
        return null;
    }

    const counterTag = matches[matches.length - 1][0];
    const missedMatch = counterTag.match(/missed=\"(\d+)\"/);
    const coveredMatch = counterTag.match(/covered=\"(\d+)\"/);

    if (!missedMatch || !coveredMatch) {
        return null;
    }

    return {
        missed: parseInt(missedMatch[1], 10),
        covered: parseInt(coveredMatch[1], 10),
    };
}

function extractCoverage(reports) {
    const results = [];

    for (const report of reports) {
        try {
            const xmlContent = fs.readFileSync(report.reportFile, 'utf-8');
            const instructionCounter = parseCounter(xmlContent, 'INSTRUCTION');
            const classCounter = parseCounter(xmlContent, 'CLASS');

            if (!instructionCounter || !classCounter) {
                continue;
            }

            const totalInstructions = instructionCounter.covered + instructionCounter.missed;
            const instructionCoverage = totalInstructions > 0 ? (instructionCounter.covered / totalInstructions) * 100 : 0.0;

            results.push({
                module: report.module,
                instructionCoverage,
                missedClasses: classCounter.missed,
            });
        } catch (error) {
            console.log(`Error processing ${report.module}: ${error.message}`);
        }
    }

    return results.sort((a, b) => a.module.localeCompare(b.module));
}

function writeSummaryToFile(coverageByModule, outputPath) {
    const lines = ['## Coverage Results', '', '| Module Name | Instruction Coverage (%) | Missed Classes |', '|-------------|---------------------------|----------------|'];

    for (const result of coverageByModule) {
        lines.push(`| ${result.module} | ${result.instructionCoverage.toFixed(2)} | ${result.missedClasses} |`);
    }

    lines.push('');
    lines.push('**Note**: the module with the name `aggregated` represents the aggregated coverage of all executed test modules.');

    fs.writeFileSync(outputPath, lines.join('\n'));
}

const reports = getReportByModule(inputDirectory);
const coverageByModule = extractCoverage(reports);
writeSummaryToFile(coverageByModule, outputFile);
