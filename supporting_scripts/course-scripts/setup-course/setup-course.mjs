#!/usr/bin/env node

/**
 * Artemis Course Setup Script
 *
 * This script creates a comprehensive test course with:
 * - 5 programming exercises (different languages and configurations)
 * - 2 modeling exercises
 * - 2 text exercises
 * - 2 quiz exercises
 * - 1 file upload exercise
 * - Lectures with different content types
 * - Tutorial groups with schedule
 * - Competencies and prerequisites
 * - FAQs
 *
 * It also has students participate and submit solutions, with automatic
 * assessment for programming/quiz and manual assessment for others.
 *
 * Usage: npm run setupCourse -- [options]
 *   --server-url=<url>         Server URL (default: http://localhost:8080)
 *   --admin-user=<username>    Admin username (default: artemis_admin)
 *   --admin-password=<pass>    Admin password (default: artemis_admin)
 *   --student-password=<pass>  Password for created students/tutors (default: Password123!)
 *   --student-count=<n>        Number of students (default: 5)
 *   --course-id=<id>           Use existing course ID instead of creating new
 *   --skip-participations      Skip student participations
 */

import { createCourseSetup } from './lib/course-setup.mjs';
import { parseArgs, printUsage } from './lib/cli.mjs';

const args = parseArgs(process.argv.slice(2));

if (args.help) {
    printUsage();
    process.exit(0);
}

const config = {
    serverUrl: args['server-url'] || process.env.ARTEMIS_SERVER_URL || 'http://localhost:8080',
    adminUser: args['admin-user'] || process.env.ARTEMIS_ADMIN_USER || 'artemis_admin',
    adminPassword: args['admin-password'] || process.env.ARTEMIS_ADMIN_PASSWORD || 'artemis_admin',
    studentPassword: args['student-password'] || process.env.ARTEMIS_STUDENT_PASSWORD || 'Password123!',
    studentCount: parseInt(args['student-count'] || '5', 10),
    tutorCount: 2,
    existingCourseId: args['course-id'] ? parseInt(args['course-id'], 10) : null,
    skipParticipations: args['skip-participations'] === 'true' || args['skip-participations'] === '',
};

console.log('='.repeat(60));
console.log('Artemis Course Setup Script');
console.log('='.repeat(60));
console.log(`Server URL: ${config.serverUrl}`);
console.log(`Admin User: ${config.adminUser}`);
console.log(`Student Count: ${config.studentCount}`);
console.log(`Skip Participations: ${config.skipParticipations}`);
if (config.existingCourseId) {
    console.log(`Using existing course ID: ${config.existingCourseId}`);
}
console.log('='.repeat(60));
console.log('');

try {
    await createCourseSetup(config);
    console.log('');
    console.log('='.repeat(60));
    console.log('Course setup completed successfully!');
    console.log('='.repeat(60));
} catch (error) {
    console.error('');
    console.error('='.repeat(60));
    console.error('Course setup failed!');
    console.error('='.repeat(60));
    console.error(error.message);
    if (error.response) {
        console.error('Response status:', error.response.status);
        console.error('Response body:', error.response.data);
    }
    process.exit(1);
}
