import { TutorLeaderboardElement } from 'app/exercise/dashboards/tutor-leaderboard/tutor-leaderboard.model';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { StatsForDashboard as StatsForDashboardDTO } from 'app/openapi/model/stats-for-dashboard';

export class StatsForDashboard {
    public numberOfStudents = 0;
    public numberOfSubmissions = new DueDateStat();
    public totalNumberOfAssessments = 0;
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

    /**
     * Correctly initializes a class instance from the JSON body of a dashboard statistics response.
     * Returns a 'real' class instance that supports all class methods. Properties the server omits keep their defaults here.
     * @param statsForDashboard The response body
     * @returns The class instance
     */
    static from(statsForDashboard: StatsForDashboardDTO): StatsForDashboard {
        const stats = hydrate(new StatsForDashboard(), statsForDashboard);
        stats.numberOfSubmissions = hydrate(new DueDateStat(), stats.numberOfSubmissions);
        stats.numberOfAutomaticAssistedAssessments = hydrate(new DueDateStat(), stats.numberOfAutomaticAssistedAssessments);
        return stats;
    }
}
