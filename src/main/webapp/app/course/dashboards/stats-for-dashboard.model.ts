import { TutorLeaderboardElement } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.model';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';

export class StatsForDashboard {
    public numberOfStudents = 0;
    public numberOfSubmissions = new DueDateStat();
    public totalNumberOfAssessments = new DueDateStat();
    public numberOfAutomaticAssistedAssessments = new DueDateStat();
    public numberOfComplaints = 0;
    public numberOfOpenComplaints = 0;
    public numberOfMoreFeedbackRequests = 0;
    public numberOfOpenMoreFeedbackRequests = 0;
    public numberOfAssessmentLocks = 0;
    public totalNumberOfAssessmentLocks = 0;
    public complaintsEnabled = true;
    public feedbackRequestEnabled = true;
    public numberOfAssessmentsOfCorrectionRounds = [new DueDateStat()]; // Array with number of assessments for each correction round
    public numberOfLockedAssessmentByOtherTutorsOfCorrectionRound = [new DueDateStat()]; // Array with number of locked assessments for each correction round
    public numberOfRatings = 0;

    public tutorLeaderboardEntries: TutorLeaderboardElement[] = [];

    constructor() {}

    /**
     * Correctly initializes a class instance from a typecasted object.
     * Returns a 'real' class instance that supports all class methods.
     * @param object: The typecasted object
     * @returns The class instance
     */
    static from(object: StatsForDashboard): StatsForDashboard {
        const stats = Object.assign(new StatsForDashboard(), object);
        stats.numberOfSubmissions = Object.assign(new DueDateStat(), stats.numberOfSubmissions);
        stats.totalNumberOfAssessments = Object.assign(new DueDateStat(), stats.totalNumberOfAssessments);
        stats.numberOfAutomaticAssistedAssessments = Object.assign(new DueDateStat(), stats.numberOfAutomaticAssistedAssessments);
        return stats;
    }
}
