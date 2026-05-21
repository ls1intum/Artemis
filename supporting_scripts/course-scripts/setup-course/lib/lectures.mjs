/**
 * Lecture creation functions
 */

// Minimal valid PDF content (a simple one-page PDF with "Sample Document" text)
const SAMPLE_PDF_BASE64 = `JVBERi0xLjQKMSAwIG9iago8PAovVHlwZSAvQ2F0YWxvZwovUGFnZXMgMiAwIFIKPj4KZW5kb2JqCjIgMCBvYmoKPDwKL1R5cGUgL1BhZ2VzCi9LaWRzIFszIDAgUl0KL0NvdW50IDEKL01lZGlhQm94IFswIDAgNjEyIDc5Ml0KPj4KZW5kb2JqCjMgMCBvYmoKPDwKL1R5cGUgL1BhZ2UKL1BhcmVudCAyIDAgUgovUmVzb3VyY2VzIDw8Ci9Gb250IDw8Ci9GMSA0IDAgUgo+Pgo+PgovQ29udGVudHMgNSAwIFIKPj4KZW5kb2JqCjQgMCBvYmoKPDwKL1R5cGUgL0ZvbnQKL1N1YnR5cGUgL1R5cGUxCi9CYXNlRm9udCAvSGVsdmV0aWNhCj4+CmVuZG9iago1IDAgb2JqCjw8Ci9MZW5ndGggNDQKPj4Kc3RyZWFtCkJUCi9GMSAyNCBUZgoxMDAgNzAwIFRkCihTYW1wbGUgRG9jdW1lbnQpIFRqCkVUCmVuZHN0cmVhbQplbmRvYmoKeHJlZgowIDYKMDAwMDAwMDAwMCA2NTUzNSBmIAowMDAwMDAwMDA5IDAwMDAwIG4gCjAwMDAwMDAwNTggMDAwMDAgbiAKMDAwMDAwMDE0NyAwMDAwMCBuIAowMDAwMDAwMjc0IDAwMDAwIG4gCjAwMDAwMDAzNTMgMDAwMDAgbiAKdHJhaWxlcgo8PAovU2l6ZSA2Ci9Sb290IDEgMCBSCj4+CnN0YXJ0eHJlZgo0NDgKJSVFT0Y=`;

/**
 * Create lectures with different content types
 */
export async function createLectures(client, courseId, exercises) {
    const lectures = [];

    // Lecture 1: Introduction with text units and an exercise unit
    const lecture1 = await createLecture(client, courseId, {
        title: 'Introduction to Software Engineering',
        description: 'This lecture covers the basics of software engineering principles.',
    });

    await createTextUnit(client, lecture1.id, {
        name: 'What is Software Engineering?',
        content: `# What is Software Engineering?

Software engineering is the systematic application of engineering approaches to the development of software.

## Key Concepts

1. **Requirements Engineering**: Understanding what needs to be built
2. **Design**: Planning the architecture and structure
3. **Implementation**: Writing the actual code
4. **Testing**: Verifying the software works correctly
5. **Maintenance**: Keeping the software running and updated

## Why It Matters

Software engineering practices help teams:
- Build reliable software
- Manage complexity
- Work efficiently in teams
- Deliver on time and budget`,
    });

    await createTextUnit(client, lecture1.id, {
        name: 'Development Methodologies',
        content: `# Development Methodologies

## Waterfall Model
Traditional sequential approach where each phase must be completed before the next begins.

## Agile Development
Iterative approach focusing on:
- Customer collaboration
- Responding to change
- Working software
- Individuals and interactions

## Scrum Framework
- Sprint-based development
- Daily standups
- Sprint reviews and retrospectives`,
    });

    if (exercises.programming.length > 0) {
        await createExerciseUnit(client, lecture1.id, exercises.programming[0]);
    }

    // Add attachment unit with PDF
    await createAttachmentUnit(client, lecture1.id, {
        name: 'Course Syllabus',
        description: 'The complete course syllabus with schedule and grading information.',
        filename: 'syllabus.pdf',
    });

    lectures.push(lecture1);

    // Lecture 2: Design Patterns with online resources
    const lecture2 = await createLecture(client, courseId, {
        title: 'Design Patterns',
        description: 'Learn about common design patterns in software development.',
    });

    await createTextUnit(client, lecture2.id, {
        name: 'Introduction to Design Patterns',
        content: `# Design Patterns

Design patterns are typical solutions to common problems in software design.

## Categories

### Creational Patterns
- Singleton
- Factory Method
- Abstract Factory
- Builder
- Prototype

### Structural Patterns
- Adapter
- Bridge
- Composite
- Decorator
- Facade

### Behavioral Patterns
- Observer
- Strategy
- Command
- State
- Template Method`,
    });

    await createOnlineUnit(client, lecture2.id, {
        name: 'Design Patterns Reference',
        description: 'External resource for design pattern examples',
        source: 'https://refactoring.guru/design-patterns',
    });

    if (exercises.modeling.length > 0) {
        await createExerciseUnit(client, lecture2.id, exercises.modeling[0]);
    }

    lectures.push(lecture2);

    // Lecture 3: Testing and Quality
    const lecture3 = await createLecture(client, courseId, {
        title: 'Software Testing',
        description: 'Learn about different testing strategies and quality assurance.',
    });

    await createTextUnit(client, lecture3.id, {
        name: 'Testing Fundamentals',
        content: `# Software Testing

## Types of Testing

### Unit Testing
Testing individual components in isolation.

\`\`\`java
@Test
void testAddition() {
    Calculator calc = new Calculator();
    assertEquals(4, calc.add(2, 2));
}
\`\`\`

### Integration Testing
Testing how components work together.

### System Testing
Testing the complete integrated system.

### Acceptance Testing
Verifying the system meets business requirements.

## Test-Driven Development (TDD)

1. Write a failing test
2. Write minimal code to pass
3. Refactor
4. Repeat`,
    });

    await createTextUnit(client, lecture3.id, {
        name: 'Code Quality',
        content: `# Code Quality

## Static Analysis
Tools that analyze code without executing it:
- Checkstyle
- SpotBugs
- SonarQube

## Code Reviews
Peer review process to catch issues early.

## Metrics
- Code coverage
- Cyclomatic complexity
- Technical debt`,
    });

    if (exercises.quiz.length > 0) {
        await createExerciseUnit(client, lecture3.id, exercises.quiz[0]);
    }

    lectures.push(lecture3);

    // Lecture 4: Advanced Topics
    const lecture4 = await createLecture(client, courseId, {
        title: 'Advanced Software Architecture',
        description: 'Explore advanced architectural patterns and microservices.',
    });

    await createTextUnit(client, lecture4.id, {
        name: 'Microservices Architecture',
        content: `# Microservices Architecture

## Principles
- Single responsibility
- Autonomous services
- Decentralized data management
- Infrastructure automation

## Benefits
- Independent deployment
- Technology flexibility
- Scalability
- Fault isolation

## Challenges
- Distributed system complexity
- Network latency
- Data consistency
- Service discovery`,
    });

    await createOnlineUnit(client, lecture4.id, {
        name: 'Microservices Patterns',
        description: 'Learn about common microservices patterns',
        source: 'https://microservices.io/patterns/index.html',
    });

    if (exercises.text.length > 0) {
        await createExerciseUnit(client, lecture4.id, exercises.text[0]);
    }

    lectures.push(lecture4);

    console.log(`  Created ${lectures.length} lectures with various content types`);
    return lectures;
}

async function createLecture(client, courseId, config) {
    const lecture = {
        title: config.title,
        description: config.description,
        course: { id: courseId },
        visibleDate: new Date().toISOString(),
    };

    const response = await client.post('/api/lecture/lectures', lecture);
    console.log(`    Created lecture: ${config.title}`);
    return response.data;
}

async function createTextUnit(client, lectureId, config) {
    const unit = {
        type: 'text',
        name: config.name,
        content: config.content,
        lecture: { id: lectureId },
        releaseDate: new Date().toISOString(),
    };

    const response = await client.post(`/api/lecture/lectures/${lectureId}/text-units`, unit);
    console.log(`      Added text unit: ${config.name}`);
    return response.data;
}

async function createExerciseUnit(client, lectureId, exercise) {
    const unit = {
        type: 'exercise',
        exercise: {
            id: exercise.id,
            type: exercise.type,
        },
        lecture: { id: lectureId },
    };

    const response = await client.post(`/api/lecture/lectures/${lectureId}/exercise-units`, unit);
    console.log(`      Added exercise unit for exercise: ${exercise.title}`);
    return response.data;
}

async function createOnlineUnit(client, lectureId, config) {
    const unit = {
        type: 'online',
        name: config.name,
        description: config.description,
        source: config.source,
        lecture: { id: lectureId },
        releaseDate: new Date().toISOString(),
    };

    const response = await client.post(`/api/lecture/lectures/${lectureId}/online-units`, unit);
    console.log(`      Added online unit: ${config.name}`);
    return response.data;
}

async function createAttachmentUnit(client, lectureId, config) {
    // Create multipart form data with proper boundaries
    const boundary = '----FormBoundary' + Math.random().toString(36).substring(2);

    const attachmentVideoUnit = {
        type: 'attachment',
        description: config.description,
        lecture: { id: lectureId },
    };

    const attachment = {
        name: config.name,
        attachmentType: 'FILE',
        releaseDate: new Date().toISOString(),
        version: 1,
    };

    // Decode base64 PDF content
    const pdfContent = Buffer.from(SAMPLE_PDF_BASE64, 'base64');

    // Build multipart body
    let body = '';

    // Part 1: attachmentVideoUnit
    body += `--${boundary}\r\n`;
    body += `Content-Disposition: form-data; name="attachmentVideoUnit"\r\n`;
    body += `Content-Type: application/json\r\n\r\n`;
    body += JSON.stringify(attachmentVideoUnit) + '\r\n';

    // Part 2: attachment
    body += `--${boundary}\r\n`;
    body += `Content-Disposition: form-data; name="attachment"\r\n`;
    body += `Content-Type: application/json\r\n\r\n`;
    body += JSON.stringify(attachment) + '\r\n';

    // Part 3: file (binary)
    const beforeFile = body;
    const afterFile = `\r\n--${boundary}--\r\n`;

    const fileHeader = `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${config.filename}"\r\nContent-Type: application/pdf\r\n\r\n`;

    // Combine all parts as a Buffer for binary safety
    const bodyBuffer = Buffer.concat([
        Buffer.from(beforeFile, 'utf8'),
        Buffer.from(fileHeader, 'utf8'),
        pdfContent,
        Buffer.from(afterFile, 'utf8'),
    ]);

    const response = await client.request('POST', `/api/lecture/lectures/${lectureId}/attachment-video-units`, {
        body: bodyBuffer,
        headers: {
            'Content-Type': `multipart/form-data; boundary=${boundary}`,
        },
        contentType: 'multipart',
    });

    console.log(`      Added attachment unit: ${config.name}`);
    return response.data;
}
