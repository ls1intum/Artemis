package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

public class StatsForInstructorDashboardDTO {

    public long numberOfStudents;

    public long numberOfTutors;

    public long numberOfSubmissions;

    public long numberOfAssessments;

    public long numberOfComplaints;

    public long numberOfOpenComplaints;

    public List<StatsTutorLeaderboardDTO> tutorLeaderboard;

    public StatsForInstructorDashboardDTO() {
    }
}
