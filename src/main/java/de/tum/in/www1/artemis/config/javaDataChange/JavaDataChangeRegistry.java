package de.tum.in.www1.artemis.config.javaDataChange;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.javaDataChange.entries.ChangeEntry0;
import de.tum.in.www1.artemis.config.javaDataChange.entries.ChangeEntry1;

@Service
public class JavaDataChangeRegistry {

    private final List<Class<? extends JavaDataChangeEntry>> entryList = new ArrayList<>();

    private final AutowireCapableBeanFactory beanFactory;

    public JavaDataChangeRegistry(AutowireCapableBeanFactory beanFactory) {
        // Here we define the order of the ChangeEntries
        entryList.add(ChangeEntry0.class);
        entryList.add(ChangeEntry1.class);
        this.beanFactory = beanFactory;
    }

    @EventListener
    public void execute(ApplicationReadyEvent event) {
        entryList.forEach(entryClass -> {
            // We have to manually autowire the components here
            JavaDataChangeEntry entry = (JavaDataChangeEntry) beanFactory.autowire(entryClass, AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, true);
            entry.execute();
        });
    }
}
