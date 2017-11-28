/**
 * abstract class MultipleChoiceScorer extends Scorer
 *
 * @constructor
 * @abstract
 * @extends Scorer
 */
var MultipleChoiceScorer = function(){
    if (this.constructor === MultipleChoiceScorer) {
        throw new Error("Can't instantiate abstract class!");
    }
    Scorer.apply(this, arguments);
};

/**
 * check if answer option is selected given the selected answer options
 * @param answerOption {Object} the answer option to check for
 * @param selectedAnswerOptions {Array} all selected answer options
 * @return {boolean} true, if the answer option is selected, false otherwise
 */
MultipleChoiceScorer.prototype.isAnswerOptionSelected = function(answerOption, selectedAnswerOptions) {
    return selectedAnswerOptions.findIndex(function(selected) {
        return selected.id === answerOption.id;
    }) !== -1;
};
