package de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource;

import java.util.ArrayList;
import java.util.TreeSet;

import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralSolutionEntryGenerationException;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile;

public class CombineChangeBlocks extends BehavioralKnowledgeSource {

    public CombineChangeBlocks(BehavioralBlackboard blackboard) {
        super(blackboard);
    }

    @Override
    public boolean executeCondition() {
        return blackboard.getGroupedFiles() != null && blackboard.getGroupedFiles().stream().noneMatch(groupedFile -> groupedFile.getCommonChanges() == null);
    }

    @Override
    public boolean executeAction() throws BehavioralSolutionEntryGenerationException {
        boolean didChanges = false;

        for (GroupedFile groupedFile : blackboard.getGroupedFiles()) {
            while (true) {
                var currentChangeBlocks = new ArrayList<>(groupedFile.getCommonChanges());
                var newChangeBlocks = new ArrayList<GroupedFile.ChangeBlock>();
                for (int i = 0; i < currentChangeBlocks.size(); i++) {
                    var currentChangeBlock = currentChangeBlocks.get(i);
                    if (i < currentChangeBlocks.size() - 1) {
                        var nextChangeBlock = currentChangeBlocks.get(i + 1);
                        if (currentChangeBlock.intersectsOrTouches(nextChangeBlock)) {
                            var lines = new TreeSet<>(currentChangeBlock.getLines());
                            lines.addAll(nextChangeBlock.getLines());
                            newChangeBlocks.add(new GroupedFile.ChangeBlock(lines));
                            i++;
                            continue;
                        }
                    }
                    newChangeBlocks.add(currentChangeBlock);
                }
                if (!newChangeBlocks.equals(currentChangeBlocks)) {
                    groupedFile.setCommonChanges(newChangeBlocks);
                    didChanges = true;
                }
                else {
                    break;
                }
            }
        }
        return didChanges;
    }
}
