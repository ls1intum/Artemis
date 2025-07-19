package de.tum.cit.aet.artemis.iris.domain.settings;

import java.util.SortedSet;

public interface HasEnabledCategories {

    SortedSet<String> getEnabledForCategories();

    void setEnabledForCategories(SortedSet<String> enabledForCategories);
}
