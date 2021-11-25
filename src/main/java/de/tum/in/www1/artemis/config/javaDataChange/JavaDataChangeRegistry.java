package de.tum.in.www1.artemis.config.javaDataChange;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.javaDataChange.entries.ChangeEntry0;
import de.tum.in.www1.artemis.config.javaDataChange.entries.ChangeEntry1;

@Service
public class JavaDataChangeRegistry {

    // Make it a map to make sure nothing breaks if an entry gets accidentally deleted
    private final Map<Integer, Class<? extends JavaDataChangeEntry>> entryList = new HashMap<>();

    private final AutowireCapableBeanFactory beanFactory;

    public JavaDataChangeRegistry(AutowireCapableBeanFactory beanFactory) {
        // Here we define the order of the ChangeEntries
        entryList.put(0, ChangeEntry0.class);
        entryList.put(1, ChangeEntry1.class);
        this.beanFactory = beanFactory;
    }

    @EventListener
    public void execute(ApplicationReadyEvent event) {
        initChecker();
        entryList.forEach((key, value) -> {
            // We have to manually autowire the components here
            JavaDataChangeEntry entry = (JavaDataChangeEntry) beanFactory.autowire(value, AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, true);
            entry.execute();
        });
    }

    private void initChecker() {
        long mapSize = entryList.size();
        long distinctKeys = entryList.keySet().stream().distinct().count();
        if (mapSize != distinctKeys) {
            throw new RuntimeException("JavaDateChangeRegistry corrupted");
        }
    }
}
