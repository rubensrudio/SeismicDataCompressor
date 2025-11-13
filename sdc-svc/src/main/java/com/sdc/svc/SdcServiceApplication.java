package com.sdc.svc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class SdcServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SdcServiceApplication.class, args);
    }
}

@RestController
class HealthController {
    @GetMapping("/api/health")
    Mono<String> health() { return Mono.just("SDC Service is up"); }
}
