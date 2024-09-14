package de.tum.cit.aet.artemis.exercise.domain;

/**
 * Enum that decides
 * - if and how max_points of the exercise increase the reachable points in a course/exam
 * - if and how points / bonus points achieved by a student in an exercise count towards his/her course / exam score
 */
public enum IncludedInOverallScore {

    /**
     * - exercise can have max points > 0 and max bonus points >= 0
     * - max_points of exercise increase the maximum reachable points in a course / exam
     * - max_bonus_points of exercise do not increase the maximum reachable points in a course / exam
     * - points and bonus points achieved by a student/team do count towards the student's/team's overall course / exam score
     */
    INCLUDED_COMPLETELY {

        @Override
        public boolean validateBonusPoints(Double bonusPoints) {
            return bonusPoints >= 0;
        }
    },
    /**
     * - exercise can have max points > 0 and max bonus points must be 0
     * - max_points of exercise do NOT increase the maximum reachable points in a course / exam (as they are included as a bonus)
     * - points achieved by a student/team count as bonus points towards the student's/team's overall course / exam score
     */
    INCLUDED_AS_BONUS {

        @Override
        public boolean validateBonusPoints(Double bonusPoints) {
            return bonusPoints == 0;
        }
    },
    /**
     * - exercise can have max points > 0 and max bonus points must be 0
     * - max_points of exercise do NOT increase the maximum reachable points in a course / exam (as they are not included at all)
     * - points achieved by a student/team do not count towards the student's/team's overall course / exam score
     */
    NOT_INCLUDED {

        @Override
        public boolean validateBonusPoints(Double bonusPoints) {
            return bonusPoints == 0;
        }
    };

    /**
     * Checks if the specified amount of bonus points is a valid exercises configuration
     * for the selected IncludedInOverallScore value.
     *
     * @param bonusPoints the bonus points for the exercise
     * @return true if the amount of bonus points is a valid, false otherwise
     */
    public abstract boolean validateBonusPoints(Double bonusPoints);
}
