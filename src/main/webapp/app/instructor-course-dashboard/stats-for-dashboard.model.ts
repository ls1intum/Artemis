import { TutorLeaderboardElement } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.model';

export class StatsForDashboard {
    public numberOfStudents = 0;
    public numberOfSubmissions = 0;
    public numberOfAssessments = 0;
    public numberOfComplaints = 0;
    public numberOfOpenComplaints = 0;

    public tutorLeaderboardEntries: TutorLeaderboardElement[] = [];

    constructor() {}
}
