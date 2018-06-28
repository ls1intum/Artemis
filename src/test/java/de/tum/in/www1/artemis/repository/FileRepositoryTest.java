package de.tum.in.www1.artemis.repository;

import org.junit.Assert;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileRepositoryTest {

    @Test
    public void writeReadDelete() {

        String json = "{\"Write\":3}";
        JsonModelRepository modelRepository = new JsonModelRepository();

        Assert.assertFalse(modelRepository.exists(1,2,3));
        modelRepository.writeModel(1, 2, 3, json);

        Assert.assertTrue(modelRepository.exists(1,2,3));
        assertThat(modelRepository.readModel(1,2,3).toString()).isEqualTo(json);

        modelRepository.deleteModel(1,2,3);

        Assert.assertFalse(modelRepository.exists(1,2,3));
    }
}
