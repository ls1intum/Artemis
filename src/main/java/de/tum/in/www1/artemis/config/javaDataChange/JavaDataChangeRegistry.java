package de.tum.in.www1.artemis.config.javaDataChange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.javaDataChange.entries.ChangeEntry20211127_120000;
import de.tum.in.www1.artemis.config.javaDataChange.entries.ChangeEntry20211128_120000;
import de.tum.in.www1.artemis.domain.JavaDataChangelog;
import de.tum.in.www1.artemis.repository.JavaDataChangeRepository;

@Service
public class JavaDataChangeRegistry {

    @Autowired
    private JavaDataChangeRepository javaDataChangeRepository;

    @Value("${artemis.version}")
    private String artemisVersion;

    // Make it a map to make sure nothing breaks if an entry gets accidentally deleted
    private final Map<Integer, Class<? extends JavaDataChangeEntry>> entryMap = new HashMap<>();

    private final AutowireCapableBeanFactory beanFactory;

    private final MessageDigest md = MessageDigest.getInstance("MD5");

    public JavaDataChangeRegistry(AutowireCapableBeanFactory beanFactory) throws NoSuchAlgorithmException {
        // Here we define the order of the ChangeEntries
        entryMap.put(0, ChangeEntry20211127_120000.class);
        entryMap.put(1, ChangeEntry20211128_120000.class);
        this.beanFactory = beanFactory;
    }

    @EventListener
    public void execute(ApplicationReadyEvent event) throws IOException, NoSuchAlgorithmException {
        String startupHash = toMD5(ZonedDateTime.now().toString());
        Map<Integer, JavaDataChangeEntry> javaDataChangeEntryMap = entryMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e ->
        // We have to manually autowire the components here
        (JavaDataChangeEntry) beanFactory.autowire(e.getValue(), AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, true)));
        checkIntegrity(javaDataChangeEntryMap);

        List<String> executedChanges = javaDataChangeRepository.findAll().stream().map(JavaDataChangelog::getDateString).toList();

        javaDataChangeEntryMap = javaDataChangeEntryMap.entrySet().stream().filter(e -> !executedChanges.contains(e.getValue().date()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        for (Map.Entry<Integer, JavaDataChangeEntry> integerClassEntry : javaDataChangeEntryMap.entrySet()) {
            JavaDataChangeEntry entry = integerClassEntry.getValue();
            entry.execute();
            JavaDataChangelog newChangelog = new JavaDataChangelog();
            newChangelog.setAuthor(entry.author());
            newChangelog.setDateExecuted(ZonedDateTime.now());
            newChangelog.setDateString(entry.date());
            newChangelog.setSystemVersion(artemisVersion);
            newChangelog.setDeploymentId(startupHash);

            javaDataChangeRepository.save(newChangelog);
            System.out.println("Executed change " + entry.date());
        }
    }

    private String toMD5(String string) {
        return Hex.encodeHexString(md.digest(string.getBytes(StandardCharsets.UTF_8)));
    }

    private void checkIntegrity(Map<Integer, JavaDataChangeEntry> instances) {
        long mapSize = entryMap.size();
        long distinctKeys = entryMap.keySet().stream().distinct().count();
        long distinctDateStrings = instances.values().stream().map(JavaDataChangeEntry::date).distinct().count();
        if (mapSize != distinctKeys) {
            throw new RuntimeException("JavaDateChangeRegistry corrupted by keys duplication");
        }
        if (mapSize != distinctDateStrings) {
            throw new RuntimeException("JavaDateChangeRegistry corrupted by dates duplication");
        }
        List<JavaDataChangeEntry> entryList = instances.values().stream().toList();
        if (entryList.size() > 0) {
            String baseDateString = entryList.get(0).date();
            for (int i = 1; i < entryList.size(); i++) {
                if (baseDateString.compareTo(entryList.get(i).date()) >= 0) {
                    throw new RuntimeException("JavaDateChangeRegistry corrupted by dates order. " + entryList.get(i).date() + " should come before " + baseDateString);
                }
                baseDateString = entryList.get(i).date();
            }
        }
    }
}
