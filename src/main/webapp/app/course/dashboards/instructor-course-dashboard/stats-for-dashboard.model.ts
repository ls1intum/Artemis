import { TutorLeaderboardElement } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.model';

export class StatsForDashboard {
    public numberOfStudents = 0;
    public numberOfInTimeSubmissions = 0;
    public numberOfLateSubmissions = 0;
    public numberOfAssessments = 0;
    public numberOfLateAssessments = 0;
    public numberOfAutomaticAssistedAssessments = 0;
    public numberOfComplaints = 0;
    public numberOfOpenComplaints = 0;
    public numberOfMoreFeedbackRequests = 0;
    public numberOfOpenMoreFeedbackRequests = 0;

    public tutorLeaderboardEntries: TutorLeaderboardElement[] = [];

    constructor() {}
}
