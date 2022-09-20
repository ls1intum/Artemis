package de.tum.in.www1.artemis.domain.plagiarism;

public enum PlagiarismVerdict {

    // Warning: The ordering of the enum values are important, they should be ordered from the most severe to least severe.
    // See findMostSevereVerdict method below.
    PLAGIARISM, POINT_DEDUCTION, WARNING, NO_PLAGIARISM;

    /**
     * Finds the most severe plagiarism verdict where severity is defined by the order of the enum constants in {@link PlagiarismVerdict}
     * (The first enum constant is the most severe).
     *
     * In the intended usage scenario, all members of the plagiarismVerdicts should belong to the same student and in the same course or exam.
     * @param plagiarismVerdicts an iterable of plagiarism verdicts.
     * @return the most servere verdict for the student or null if there is none
     */
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
