package de.tum.cit.aet.artemis.exercise.service;

/**
 * Reports the progress of one running asynchronous deletion. The deletion job tells the tracker which step it starts and how much effort every finished step took,
 * and the tracker turns that into weighted progress messages. The progress is weighted: a step that is expected to take twice as long moves the progress twice as far.
 */
public class DeletionProgressTracker {

    private static final long MIN_NANOS_BETWEEN_ITEM_REPORTS = 250_000_000L;

    private final EntityDeletionProgressService progressService;

    private final EntityDeletionClaim claim;

    private final double totalWeight;

    private int stepsStarted;

    private double completedWeight;

    private String currentStep;

    private long lastItemReportNanos;

    DeletionProgressTracker(EntityDeletionProgressService progressService, EntityDeletionClaim claim, double totalWeight) {
        this.progressService = progressService;
        this.claim = claim;
        this.totalWeight = totalWeight;
    }

    /**
     * Announces that the work of a step starts.
     *
     * @param stepKey the stable key of the step, for example {@code participations}
     */
    public void startStep(String stepKey) {
        currentStep = stepKey;
        int stepsCompleted = stepsStarted;
        stepsStarted++;
        progressService.updateProgress(claim, stepKey, stepsCompleted, 0, 0, null, percentOf(0));
    }

    /**
     * Adds the weight of a step that is completely done.
     *
     * @param weight the expected effort of the finished step
     */
    public void completeStep(double weight) {
        completedWeight += weight;
    }

    /**
     * Reports how many items of the current step are done.
     *
     * @param itemsProcessed the items that are done
     * @param totalItems     all items of the step
     * @param itemTitle      the title of the item that is processed now, or null
     * @param partialWeight  the part of the weight of the current step that is already done
     */
    public void reportItems(int itemsProcessed, int totalItems, String itemTitle, double partialWeight) {
        // Deleting thousands of participations must not send thousands of messages and take the distributed lock for each of them.
        long now = System.nanoTime();
        boolean lastItem = itemsProcessed >= totalItems;
        if (!lastItem && lastItemReportNanos != 0 && now - lastItemReportNanos < MIN_NANOS_BETWEEN_ITEM_REPORTS) {
            return;
        }
        lastItemReportNanos = now;
        progressService.updateProgress(claim, currentStep, Math.max(stepsStarted - 1, 0), itemsProcessed, totalItems, itemTitle, percentOf(partialWeight));
    }

    int stepsCompleted() {
        return Math.max(stepsStarted - 1, 0);
    }

    String currentStep() {
        return currentStep;
    }

    double percentOfCompleted() {
        return percentOf(0);
    }

    private double percentOf(double partialWeight) {
        if (totalWeight <= 0) {
            return 0;
        }
        return Math.min(99.0, (completedWeight + partialWeight) / totalWeight * 100.0);
    }
}
