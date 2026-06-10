package com.example.velocitylimits.integration;

import com.example.velocitylimits.processor.LoadProcessor;
import com.example.velocitylimits.repository.LoadAttemptRepository;
import com.example.velocitylimits.service.VelocityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LoadProcessorIT {

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

    private LoadProcessor processor() {
        return new LoadProcessor(velocityService, objectMapper);
    }

    @Test
    void shouldProduceCorrectOutputForSampleInput() throws Exception {
        String input = """
                {"id":"1","customer_id":"1","load_amount":"$1000.00","time":"2018-01-01T00:00:00Z"}
                {"id":"2","customer_id":"1","load_amount":"$2000.00","time":"2018-01-01T01:00:00Z"}
                {"id":"3","customer_id":"1","load_amount":"$2500.00","time":"2018-01-01T02:00:00Z"}
                {"id":"4","customer_id":"1","load_amount":"$500.00","time":"2018-01-01T03:00:00Z"}
                {"id":"5","customer_id":"1","load_amount":"$100.00","time":"2018-01-01T04:00:00Z"}
                {"id":"1","customer_id":"1","load_amount":"$1000.00","time":"2018-01-01T05:00:00Z"}
                """;

        Path inputFile = tempDir.resolve("input.txt");
        Path outputFile = tempDir.resolve("output.txt");
        Files.writeString(inputFile, input);

        processor().run(inputFile.toString(), outputFile.toString());

        List<String> lines = Files.readAllLines(outputFile);

        // id=1: $1000 → accepted  (day: $1000, count: 1)
        // id=2: $2000 → accepted  (day: $3000, count: 2)
        // id=3: $2500 → declined  (day would be $5500 > $5000)
        // id=4: $500  → accepted  (day: $3500, count: 3)
        // id=5: $100  → declined  (count would be 4 > 3)
        // id=1 dup    → no output
        assertThat(lines).hasSize(5);
        assertResponse(lines.get(0), "1", "1", true);
        assertResponse(lines.get(1), "2", "1", true);
        assertResponse(lines.get(2), "3", "1", false);
        assertResponse(lines.get(3), "4", "1", true);
        assertResponse(lines.get(4), "5", "1", false);
    }

    @Test
    void shouldHandleEmptyInputGracefully() throws Exception {
        Path inputFile = tempDir.resolve("empty.txt");
        Path outputFile = tempDir.resolve("output.txt");
        Files.writeString(inputFile, "");

        processor().run(inputFile.toString(), outputFile.toString());

        assertThat(Files.readAllLines(outputFile)).isEmpty();
    }

    @Test
    void shouldHandleMissingInputFileGracefully() {
        processor().run("nonexistent_file.txt", tempDir.resolve("output.txt").toString());
    }

    private void assertResponse(String json, String expectedId, String expectedCustomerId,
                                boolean expectedAccepted) throws IOException {
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("id").asText()).isEqualTo(expectedId);
        assertThat(node.get("customer_id").asText()).isEqualTo(expectedCustomerId);
        assertThat(node.get("accepted").asBoolean()).isEqualTo(expectedAccepted);
    }
}
