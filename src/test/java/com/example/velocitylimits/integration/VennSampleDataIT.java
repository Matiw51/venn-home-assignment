package com.example.velocitylimits.integration;

import com.example.velocitylimits.processor.LoadProcessor;
import com.example.velocitylimits.repository.LoadAttemptRepository;
import com.example.velocitylimits.service.VelocityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

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
class VennSampleDataIT {

    @MockBean
    private LoadProcessor suppressedProcessor;

    @Autowired
    private VelocityService velocityService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoadAttemptRepository repository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void clearDatabase() {
        repository.deleteAll();
    }

    @Test
    void shouldMatchExpectedOutputForVennSampleData() throws Exception {
        Path outputFile = tempDir.resolve("output.txt");

        new LoadProcessor(velocityService, objectMapper)
                .run("venn_input.txt", outputFile.toString());

        List<String> actual = Files.readAllLines(outputFile, StandardCharsets.UTF_8);
        List<String> expected = readClasspathLines("venn_expected_output.txt");

        assertThat(actual).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            assertThat(actual.get(i))
                    .as("Line %d", i + 1)
                    .isEqualTo(expected.get(i));
        }
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
