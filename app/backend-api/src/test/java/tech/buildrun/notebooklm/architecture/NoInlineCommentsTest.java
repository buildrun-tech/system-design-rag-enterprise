package tech.buildrun.notebooklm.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NoInlineCommentsTest {

    private static final Pattern INLINE = Pattern.compile("^\\s*(//|/\\*(?!\\*))");

    @Test
    void productionCodeHasNoInlineComments() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            List<String> offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .flatMap(NoInlineCommentsTest::offendingLines)
                    .toList();
            assertThat(offenders).isEmpty();
        }
    }

    private static Stream<String> offendingLines(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            return java.util.stream.IntStream.range(0, lines.size())
                    .filter(i -> INLINE.matcher(lines.get(i)).find())
                    .mapToObj(i -> file + ":" + (i + 1));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
