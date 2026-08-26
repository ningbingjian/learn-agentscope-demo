package com.example.agentscope.kubernetes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KubernetesProductionApplication {

    public static void main(String[] args) {
        SpringApplication.run(KubernetesProductionApplication.class, args);
    }
}
