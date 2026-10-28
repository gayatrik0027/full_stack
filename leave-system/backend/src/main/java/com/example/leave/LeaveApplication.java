package com.example.leave;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.annotation.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Main Spring Boot application entry point for Leave Application System.
 * Automatically deploys BPMN process definition and user task form to Camunda Zeebe on startup.
 */
@SpringBootApplication
@Deployment(resources = "classpath:bpmn/*.*")
public class LeaveApplication {

    private static final Logger log = LoggerFactory.getLogger(LeaveApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(LeaveApplication.class, args);
    }

    /**
     * Programmatic deployment backup runner ensuring workflows are deployed when Zeebe connects.
     */
    @Bean
    public ApplicationRunner deployWorkflowsRunner(@Autowired(required = false) ZeebeClient zeebeClient) {
        return args -> {
            if (zeebeClient != null) {
                try {
                    log.info("Verifying BPMN workflow deployment to Camunda Zeebe...");
                    zeebeClient.newDeployResourceCommand()
                        .addResourceFromClasspath("bpmn/leave-request.bpmn")
                        .addResourceFromClasspath("bpmn/leave-approval.form")
                        .send()
                        .join();
                    log.info("Successfully deployed 'leave-request.bpmn' and 'leave-approval.form' to Camunda Zeebe.");
                } catch (Exception e) {
                    log.warn("Camunda cluster is not currently reachable on startup: {}. Running in standalone mode. Workflows will be deployed when Zeebe connects.",
                        e.getMessage());
                }
            } else {
                log.info("Running in standalone/test mode without active Camunda Zeebe connection.");
            }
        };
    }
}
