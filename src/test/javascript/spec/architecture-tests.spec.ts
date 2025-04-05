import { CycleFreeFileCondition, DependOnFileCondition, filesOfProject } from 'tsarch';

describe('Angular Architecture Rules', () => {
    const regularModules = ['assessment', 'atlas', 'exam', 'fileupload', 'iris', 'lecture', 'lti', 'modeling', 'plagiarism', 'programming', 'quiz', 'text', 'tutorialgroup'];

    const exceptionModules = ['core', 'exercise', 'shared', 'buildagent', 'communication'];

    const allModules = [...regularModules, ...exceptionModules];

    const modulesWithExerciseAccess = ['fileupload', 'quiz', 'programming', 'text', 'modeling'];

    jest.setTimeout(60000);
    // TODO our entities are not compatible with this
    // describe('Module Structure Rules', () => {
    //     it('modules should be free of cycles', async () => {
    //         for (const module of allModules) {
    //             const rule = filesOfProject().inFolder(`${module}`).should().beFreeOfCycles();
    //             await checkForViolations(rule);
    //         }
    //     });
    // });

    const checkForViolations = async (rule: DependOnFileCondition | CycleFreeFileCondition) => {
        const violations = await rule.check();
        if (violations.length > 0) {
            let errorMessage = violations.length + ' violations in the module structure:\n';

            for (const violation of violations) {
                if (rule instanceof CycleFreeFileCondition) {
                    console.log('cycle', violation);
                    //errorMessage += `- Cycle in ${JSON.stringify(violation)}:\n`;
                } else {
                    // @ts-ignore
                    errorMessage += `- Access from ${violation.dependency.sourceLabel} to ${violation.dependency.targetLabel}\n`;
                }
            }

            throw new Error(errorMessage);
        }
    };

    describe('Dependency Rules', () => {
        it('overview should not depend on manage', async () => {
            for (const module of regularModules) {
                const rule = filesOfProject().inFolder(`${module}/overview`).shouldNot().dependOnFiles().inFolder(`${module}/manage`);
                await checkForViolations(rule);
            }
        });

        it('manage should not depend on overview', async () => {
            for (const module of regularModules) {
                const rule = filesOfProject().matchingPattern(`${module}/manage`).shouldNot().dependOnFiles().inFolder(`${module}/overview`);
                await checkForViolations(rule);
            }
        });

        it('no module should depend on another module (except shared and possibly exercise)', async () => {
            // Test that each module only depends on its own content or potentially shared/exercise
            for (const sourceModule of allModules) {
                for (const targetModule of allModules) {
                    // Skip checking dependencies on self, shared, and exercise (for specific modules)
                    if (sourceModule === targetModule || targetModule === 'shared') {
                        continue;
                    }

                    // Special case for modules allowed to access exercise
                    if (targetModule === 'exercise' && modulesWithExerciseAccess.includes(sourceModule)) {
                        continue;
                    }

                    // All other cross-module dependencies are forbidden
                    const rule = filesOfProject().inFolder(`${sourceModule}`).shouldNot().dependOnFiles().inFolder(`${targetModule}`);
                    await checkForViolations(rule);
                }
            }
        });

        // Additional test that checks for dependencies on specific module sections
        it('modules should not depend on specific components of other modules (including irregular modules)', async () => {
            for (const sourceModule of allModules) {
                for (const targetModule of allModules) {
                    // Skip self-dependencies
                    if (sourceModule === targetModule) {
                        continue;
                    }

                    // Special case for modules allowed to access exercise
                    if (targetModule === 'exercise' && modulesWithExerciseAccess.includes(sourceModule)) {
                        continue;
                    }

                    // Skip shared module since it's designed to be shared
                    if (targetModule === 'shared') {
                        continue;
                    }

                    // For regular modules, we already checked manage/overview specifically
                    // Now check for dependencies on any specific folders/files in target modules
                    // This will capture dependencies on irregular structure components too
                    const rule = filesOfProject().inFolder(`${sourceModule}`).shouldNot().dependOnFiles().inFolder(`${targetModule}`);
                    await checkForViolations(rule);
                }
            }
        });

        // Test shared module access permissions
        it('shared module components should be properly isolated', async () => {
            for (const module of allModules) {
                // Skip the shared module itself
                if (module === 'shared') {
                    continue;
                }

                // Only shared components from a module should be accessed by other modules
                for (const otherModule of allModules) {
                    if (module === otherModule) {
                        continue;
                    }

                    // Other modules should only access the shared directory of this module
                    const rule = filesOfProject().inFolder(`${otherModule}`).shouldNot().dependOnFiles().inFolder(`${module}`);
                    await checkForViolations(rule);
                }
            }
        });
    });
});
