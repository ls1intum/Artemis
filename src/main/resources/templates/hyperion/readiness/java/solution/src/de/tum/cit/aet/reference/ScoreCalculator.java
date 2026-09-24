package de.tum.cit.aet.reference;

public final class ScoreCalculator {

    private ScoreCalculator() {
    }

    public static int countPassing(int[] scores) {
        int passing = 0;
        for (int score : scores) {
            if (score >= 50) {
                passing++;
            }
        }
        return passing;
    }
}
