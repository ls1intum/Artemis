/**
 * class MultipleChoiceAllOrNothingScorer extends MultipleChoiceScorer
 *
 * @constructor
 * @extends MultipleChoiceScorer
 */
var MultipleChoiceAllOrNothingScorer = function() {
    MultipleChoiceScorer.apply(this, arguments);
};

// inheritance
MultipleChoiceAllOrNothingScorer.prototype = Object.create(MultipleChoiceScorer.prototype);
MultipleChoiceAllOrNothingScorer.prototype.constructor = MultipleChoiceAllOrNothingScorer;

/**
 * calculate score for the given answer to the given question
 *
 * @param selectedAnswers {Array} an array containing all the answerOption that the user has selected
 * @param question {Object} the question that the answer belongs to
 * @return {Number} the resulting score
 */
MultipleChoiceAllOrNothingScorer.prototype.scoreAnswerForQuestion = function(selectedAnswers, question) {
    for (var i = 0; i < question.answerOptions.length; i++) {
        var answerOption = question.answerOptions[i];
        var isSelected = this.isAnswerOptionSelected(answerOption, selectedAnswers);
        // if the user was wrong about this answer option, the entire answer can no longer be 100% correct
        // being wrong means either a correct option is not selected, or an incorrect option is selected
        if ((answerOption.isCorrect && !isSelected) || (!answerOption.isCorrect && isSelected)) {
            return 0;
        }
    }
    // the user wasn't wrong about a single answer option => the answer is 100% correct
    return question.score;
};
