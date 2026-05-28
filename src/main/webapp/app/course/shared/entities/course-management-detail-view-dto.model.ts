export class CourseManagementDetailViewDto {
    // Total Assessment
    currentPercentageAssessments: number;
    currentAbsoluteAssessments: number;
    currentMaxAssessments: number;

    // Total Complaints
    currentPercentageComplaints: number;
    currentAbsoluteComplaints: number;
    currentMaxComplaints: number;

    // More Feedback Request
    currentPercentageMoreFeedbacks: number;
    currentAbsoluteMoreFeedbacks: number;
    currentMaxMoreFeedbacks: number;

    // Average Student Score
    currentPercentageAverageScore: number;
    currentAbsoluteAverageScore: number;
    currentMaxAverageScore: number;

    // LLM Stats
    currentTotalLlmCostInEur: number;
}
