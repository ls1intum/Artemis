package de.tum.in.www1.artemis.domain.plagiarism;

public enum PlagiarismVerdict {

    // Warning: The ordering of the enum values are important, they should be ordered from the most severe to least severe.
    // See findMostSevereVerdict method below.
    PLAGIARISM, POINT_DEDUCTION, WARNING, NO_PLAGIARISM;

    public static PlagiarismVerdict findMostSevereVerdict(Iterable<PlagiarismVerdict> plagiarismVerdicts) {
        for (PlagiarismVerdict maxVerdict : PlagiarismVerdict.values()) {
            for (PlagiarismVerdict currentVerdict : plagiarismVerdicts) {
                if (currentVerdict == maxVerdict) {
                    return currentVerdict;
                }
            }
        }
        return null;
    }
}
