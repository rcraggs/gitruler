package gitruler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTests {

    @BeforeAll
    static void setup() {
    }

    @Test
    void testNoScore() throws IOException {

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("gitrules-empty.json").getFile());
        GitRulerConfig config = new GitRulerConfig(file);

        double availScore = config.getTotalAvailableScore();
        assertEquals(availScore, 0);
    }

    @Test
    void testScore() throws IOException {

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("gitrules-score.json").getFile());
        GitRulerConfig config = new GitRulerConfig(file);

        double availScore = config.getTotalAvailableScore();
        assertEquals(availScore, 22.5);
    }
}
