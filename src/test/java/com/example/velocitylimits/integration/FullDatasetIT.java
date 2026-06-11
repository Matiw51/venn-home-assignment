package com.example.velocitylimits.integration;

import com.example.velocitylimits.processor.LoadProcessor;
import com.example.velocitylimits.repository.LoadAttemptRepository;
import com.example.velocitylimits.service.VelocityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FullDatasetIT {

    private static final String VENN_INPUT = "venn_input.txt";
    private static final String VENN_EXPECTED_OUTPUT = "venn_expected_output.txt";
    private static final String OUTPUT_FILE = "output.txt";

    @MockitoBean
    private LoadProcessor suppressedProcessor;

    @Autowired
    private VelocityService velocityService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoadAttemptRepository repository;

    @Autowired
    private Validator validator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void clearDatabase() {
        repository.deleteAll();
    }

    @Test
    void shouldMatchExpectedOutputForVennSampleData() throws Exception {
        Path outputFile = tempDir.resolve(OUTPUT_FILE);

        runLoadProcessor(Path.of(VENN_INPUT), outputFile);

        List<String> actual = Files.readAllLines(outputFile, StandardCharsets.UTF_8);
        List<String> expected = readClasspathLines(VENN_EXPECTED_OUTPUT);

        assertThat(actual).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            assertThat(actual.get(i))
                    .as("Line %d", i + 1)
                    .isEqualTo(expected.get(i));
        }
    }

    private void runLoadProcessor(Path inputFile, Path outputFile) {
        new LoadProcessor(velocityService, objectMapper, validator)
                .run(inputFile.toString(), outputFile.toString());
    }

    private List<String> readClasspathLines(String resource) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource)),
                StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.isBlank())
                    .collect(Collectors.toList());
        }
    }
}
