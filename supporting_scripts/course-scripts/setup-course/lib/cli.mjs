/**
 * CLI argument parsing utilities
 */

export function parseArgs(argv) {
    const args = {};
    for (const arg of argv) {
        if (arg.startsWith('--')) {
            const [key, value] = arg.slice(2).split('=');
            args[key] = value ?? true;
        }
    }
    return args;
}

export function printUsage() {
    console.log(`
Artemis Course Setup Script

Usage: npm run setupCourse -- [options]

Options:
  --server-url=<url>         Server URL (default: http://localhost:8080)
  --admin-user=<username>    Admin username (default: artemis_admin)
  --admin-password=<pass>    Admin password (default: artemis_admin)
  --student-password=<pass>  Password for created students/tutors (default: Password123!)
  --student-count=<n>        Number of students to create (default: 5)
  --course-id=<id>           Use existing course ID instead of creating new
  --skip-participations      Skip student participations and assessments
  --help                     Show this help message

Environment Variables:
  ARTEMIS_SERVER_URL         Server URL
  ARTEMIS_ADMIN_USER         Admin username
  ARTEMIS_ADMIN_PASSWORD     Admin password
  ARTEMIS_STUDENT_PASSWORD   Password for created students/tutors

Examples:
  npm run setupCourse
  npm run setupCourse -- --student-count=10
  npm run setupCourse -- --course-id=123 --skip-participations
  npm run setupCourse -- --server-url=http://artemis.example.com
`);
}
