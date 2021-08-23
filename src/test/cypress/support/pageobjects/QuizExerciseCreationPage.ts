export class QuizExerciseCreationPage {

    setTitle(title:  string) {
        cy.get('#quiz-title').type(title);
    }

    addMCQuestion() {
        cy.get('#quiz-add-mc-question').click();
    }
}
