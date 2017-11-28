/**
 * class QuestionScorerFactory
 *
 * constructor is not used because we only need static methods for now
 * @constructor
 */
var QuestionScorerFactory = function() {

};

/**
 * creates and returns an instance of the appropriate implementation of Scorer depending on scoringType and questionType
 *
 * @param scoringType {String} the scoring type for this question
 * @param questionType {String} the type of this question
 * @return {Scorer} an instance of the appropriate Scorer type
 * @static
 */
QuestionScorerFactory.makeScorer = function(scoringType, questionType) {
    if (scoringType === "ALL_OR_NOTHING" && questionType === "multiple-choice") {
        return new MultipleChoiceAllOrNothingScorer();
    } else {
        throw new Error("No Scorer for this combination of scoringType and questionType has been implemented yet!");
    }
};
