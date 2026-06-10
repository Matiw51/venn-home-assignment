package com.example.velocitylimits.processor;

import com.example.velocitylimits.dto.LoadRequest;
import com.example.velocitylimits.dto.LoadResponse;
import com.example.velocitylimits.service.VelocityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

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

    public LoadProcessor(VelocityService velocityService, ObjectMapper objectMapper) {
        this.velocityService = velocityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        String inputPath = args.length >= 1 ? args[0] : "input.txt";
        String outputPath = args.length >= 2 ? args[1] : "output.txt";

        log.info("Starting processing: input={}, output={}", inputPath, outputPath);

        int count = 0;
        try (BufferedReader reader = openReader(inputPath);
             PrintWriter writer = openWriter(outputPath)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                final int currentLine = ++lineNumber;
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    LoadRequest request = objectMapper.readValue(line, LoadRequest.class);
                    Optional<LoadResponse> response = velocityService.process(request);
                    if (response.isPresent()) {
                        writer.println(objectMapper.writeValueAsString(response.get()));
                        count++;
                    }
                } catch (Exception e) {
                    log.error("Error processing line {}: {}", currentLine, line, e);
                }
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

    private BufferedReader openReader(String path) throws IOException {
        // Try filesystem first, then classpath
        File file = new File(path);
        if (file.exists()) {
            return new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8));
        }
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is == null) throw new FileNotFoundException(path);
        return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
    }

    private PrintWriter openWriter(String path) throws IOException {
        return new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(path), StandardCharsets.UTF_8));
    }
}
