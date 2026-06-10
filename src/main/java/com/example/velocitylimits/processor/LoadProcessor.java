package com.example.velocitylimits.processor;

import com.example.velocitylimits.dto.LoadRequest;
import com.example.velocitylimits.service.VelocityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.stream.Collectors.joining;

/**
 * Reads fund load attempts from an input file, applies velocity limit checks via
 * {@link VelocityService}, and writes the results to an output file.
 *
 * <p>Input is expected to be a newline-delimited file of JSON objects. Each line is processed
 * independently. Malformed lines are logged and skipped. Duplicate load IDs per customer
 * produce no output line.
 *
 * <p>Implements {@link CommandLineRunner} so the application processes {@code input.txt →
 * output.txt} on startup when run as a standalone Spring Boot application.
 */
@Component
public class LoadProcessor implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LoadProcessor.class);

    private final VelocityService velocityService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public LoadProcessor(VelocityService velocityService, ObjectMapper objectMapper, Validator validator) {
        this.velocityService = velocityService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Override
    public void run(String... args) {
        String inputPath = args.length >= 1 ? args[0] : "input.txt";
        String outputPath = args.length >= 2 ? args[1] : "output.txt";

        log.info("Starting processing: input={}, output={}", inputPath, outputPath);

        int count = 0;
        try (BufferedReader reader = openReader(inputPath);
             BufferedWriter writer = openWriter(outputPath)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) continue;
                if (processLine(line, lineNumber, writer)) count++;
            }
        } catch (FileNotFoundException e) {
            log.warn("Input file not found: {}. Nothing to process.", inputPath);
            return;
        } catch (IOException e) {
            log.error("Failed to process files: {}", e.getMessage(), e);
            return;
        }

        log.info("Done. {} results written to {}", count, outputPath);
    }

    /**
     * Deserializes, validates, and processes a single input line.
     *
     * @return true if a response was written to the output, false otherwise
     */
    private boolean processLine(String line, int lineNumber, BufferedWriter writer) {
        try {
            var request = objectMapper.readValue(line, LoadRequest.class);

            var violations = validator.validate(request);
            if (!violations.isEmpty()) {
                var messages = violations.stream()
                        .map(v -> v.getPropertyPath() + " " + v.getMessage())
                        .collect(joining(", "));
                log.warn("Invalid request on line {}: {}", lineNumber, messages);
                return false;
            }

            var response = velocityService.process(request);
            if (response.isPresent()) {
                writer.write(objectMapper.writeValueAsString(response.get()));
                writer.newLine();
                return true;
            }
        } catch (Exception e) {
            log.error("Error processing line {}: {}", lineNumber, line, e);
        }
        return false;
    }

    private BufferedReader openReader(String path) throws IOException {
        // Try filesystem first, then classpath
        Path filePath = Path.of(path);
        if (Files.exists(filePath)) {
            return Files.newBufferedReader(filePath, StandardCharsets.UTF_8);
        }
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is == null) throw new FileNotFoundException(path);
        return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
    }

    private BufferedWriter openWriter(String path) throws IOException {
        return Files.newBufferedWriter(Path.of(path), StandardCharsets.UTF_8);
    }
}
