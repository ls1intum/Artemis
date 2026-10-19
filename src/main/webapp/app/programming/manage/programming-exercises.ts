/**
 * - {@link ImportOptions.recreateBuildPlans} Option determining whether the build plans should be recreated or copied from the imported exercise
 * - {@link ImportOptions.setTestCaseVisibilityToAfterDueDate} Option determining whether the test cases should be hidden until the release date of the results
 */
export interface ImportOptions {
    recreateBuildPlans: boolean;
    setTestCaseVisibilityToAfterDueDate: boolean;
}
