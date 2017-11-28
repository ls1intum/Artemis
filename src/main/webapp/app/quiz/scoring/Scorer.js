/**
 * abstract class Scorer
 * @constructor
 * @abstract
 */
var Scorer = function() {
    if (this.constructor === Scorer) {
        throw new Error("Can't instantiate abstract class!");
    }
};

/**
 * calculate score for the given answer to the given question
 *
 * @param answer {Object | Array} the user's answer (type depends on the question's type)
 * @param question {Object} the question that the answer belongs to
 * @return {Number} the resulting score
 * @abstract
 */
Scorer.prototype.scoreAnswerForQuestion = function(answer, question) {
    throw new Error("Abstract method!");
};
