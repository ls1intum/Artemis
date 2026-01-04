/**
 * FAQ creation
 */

export async function createFaqs(client, courseId) {
    const faqs = [];

    const faqConfigs = [
        {
            questionTitle: 'How do I submit my programming exercise?',
            questionAnswer: `You can submit your programming exercise in two ways:

1. **Online Editor**: Use the built-in code editor in Artemis. Your code is automatically saved and you can submit by clicking the "Submit" button.

2. **IDE (Offline)**: Clone the repository to your local machine, make changes, commit, and push. Each push triggers an automatic submission.

Remember: Only your last submission before the deadline counts!`,
            categories: ['Exercises', 'Programming'],
        },
        {
            questionTitle: 'What happens if I miss a deadline?',
            questionAnswer: `If you miss an exercise deadline:

- **Before the due date**: Full points possible
- **After the due date**: Depending on the exercise settings, late submissions may receive reduced points or not be accepted at all

Always check the exercise details for specific late submission policies. Some exercises allow late submissions with a point deduction.`,
            categories: ['General', 'Deadlines'],
        },
        {
            questionTitle: 'How are quiz exercises graded?',
            questionAnswer: `Quiz exercises are graded automatically based on your answers:

- **Multiple Choice (Single)**: Full points for correct answer, 0 for incorrect
- **Multiple Choice (Multiple)**: Partial credit based on correct selections
- **Short Answer**: Matched against expected solutions with similarity checking
- **Drag and Drop**: Points for correctly placed items

Your score is calculated immediately after the quiz ends.`,
            categories: ['Exercises', 'Quiz'],
        },
        {
            questionTitle: 'How do I contact my tutor?',
            questionAnswer: `You can reach your tutor through several channels:

1. **Tutorial Group Channel**: Use the messaging feature in your tutorial group
2. **Course Communication**: Post questions in the course discussion forum
3. **Office Hours**: Attend scheduled office hours (check the course schedule)
4. **Direct Messages**: Send a direct message through the platform

For exercise-specific questions, use the exercise discussion thread.`,
            categories: ['Communication', 'Tutorial Groups'],
        },
        {
            questionTitle: 'How do competencies work?',
            questionAnswer: `Competencies track your learning progress throughout the course:

- **Prerequisites**: Skills you should have before starting
- **Competencies**: Learning goals you'll achieve during the course

Each competency has a mastery threshold. Complete linked exercises and lectures to improve your mastery level. Your progress is visualized in the competency dashboard.`,
            categories: ['Learning', 'Progress'],
        },
        {
            questionTitle: 'What should I do if tests fail in my programming exercise?',
            questionAnswer: `When tests fail:

1. **Read the feedback**: Check the test output for error messages
2. **Understand the test**: Look at what the test expects vs. what your code produces
3. **Debug locally**: Run tests on your machine before submitting
4. **Check edge cases**: Tests often check boundary conditions
5. **Review the problem statement**: Ensure you understood the requirements

If you're still stuck, ask in the exercise discussion or contact your tutor.`,
            categories: ['Exercises', 'Programming', 'Troubleshooting'],
        },
        {
            questionTitle: 'How do I prepare for exams?',
            questionAnswer: `Exam preparation tips:

1. **Review lectures**: Go through all lecture materials and notes
2. **Practice exercises**: Complete all exercises, especially programming ones
3. **Check competencies**: Ensure you've mastered all required competencies
4. **Attend tutorial sessions**: Get help with topics you're unsure about
5. **Practice quizzes**: Use practice mode for quiz exercises

On exam day, arrive early and ensure your device is ready.`,
            categories: ['Exams', 'Preparation'],
        },
        {
            questionTitle: 'Can I work with others on exercises?',
            questionAnswer: `Collaboration policies vary by exercise:

- **Individual exercises**: Must be completed alone. Code similarity checks are used.
- **Team exercises**: Collaboration within your team is expected.
- **Practice exercises**: Discuss concepts, but submit your own work.

When in doubt, ask your instructor. Plagiarism results in serious consequences.`,
            categories: ['General', 'Collaboration'],
        },
    ];

    for (const config of faqConfigs) {
        const faq = await createFaq(client, courseId, config);
        faqs.push(faq);
    }

    console.log(`  Created ${faqs.length} FAQs`);
    return faqs;
}

async function createFaq(client, courseId, config) {
    // CreateFaqDTO expects: courseId, questionTitle, questionAnswer, categories (Set<String>), faqState
    const faq = {
        courseId: courseId,
        questionTitle: config.questionTitle,
        questionAnswer: config.questionAnswer,
        faqState: 'ACCEPTED',
        categories: config.categories, // Simple array of strings
    };

    const response = await client.post(`/api/communication/courses/${courseId}/faqs`, faq);
    console.log(`    Created FAQ: ${config.questionTitle.substring(0, 40)}...`);
    return response.data;
}
