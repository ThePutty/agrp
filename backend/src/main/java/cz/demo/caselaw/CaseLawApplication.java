package cz.demo.caselaw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * CODEXIS hackathon demo: AI case-law search with verified citations.
 * One JVM hosts the GraphQL API, the Temporal worker, the deterministic search/verification
 * layers and the LangGraph4j AI agent.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CaseLawApplication {
    public static void main(String[] args) {
        SpringApplication.run(CaseLawApplication.class, args);
    }
}
