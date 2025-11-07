export const LEAGUE_THRESHOLDS = {
    BRONZE: 50,
    SILVER: 150,
    GOLD: 300,
    DIAMOND: 500,
} as const;

export const LEAGUES = [
    { league: 'Bronze', translationKey: 'artemisApp.quizTraining.bronze', range: `0 - ${LEAGUE_THRESHOLDS.BRONZE}` },
    { league: 'Silver', translationKey: 'artemisApp.quizTraining.silver', range: `${LEAGUE_THRESHOLDS.BRONZE} - ${LEAGUE_THRESHOLDS.SILVER}` },
    { league: 'Gold', translationKey: 'artemisApp.quizTraining.gold', range: `${LEAGUE_THRESHOLDS.SILVER} - ${LEAGUE_THRESHOLDS.GOLD}` },
    { league: 'Diamond', translationKey: 'artemisApp.quizTraining.diamond', range: `${LEAGUE_THRESHOLDS.GOLD} - ${LEAGUE_THRESHOLDS.DIAMOND}` },
    { league: 'Master', translationKey: 'artemisApp.quizTraining.master', range: `${LEAGUE_THRESHOLDS.DIAMOND}+` },
] as const;
