package com.github.timtebeek.archunit;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class ArchunitUnusedRuleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArchunitUnusedRuleApplication.class, args);
    }

}

@RestController
@RequiredArgsConstructor
class ControllerA {

    final ServiceA service;

    @PostMapping
    void postModel(ModelA model) {
        service.process(model);
    }
}

class ServiceA {
    public void process(ModelA model) {
        System.out.println(model);
    }
}

@Value
class ModelA {
    String name;
}

@Service
@RequiredArgsConstructor
class ListenerB {
    final ServiceB service;

    @KafkaListener(topics = "models-b")
    void listenerB(ModelB model) {
        service.process(model);
    }
}

class ServiceB {
    public void process(ModelB model) {
        System.out.println(model);
    }
}

@Value
class ModelB {
    String name;
}

@Service
@RequiredArgsConstructor
class JobC {
    final ServiceC service;

    @Scheduled(fixedRateString = "1m")
    void jobC() {
        service.process(new ModelC("name"));
    }
}

class ServiceC {
    public void process(ModelC model) {
        System.out.println(model);
    }
}

@Value
class ModelC {
    String name;
}

@Service
@RequiredArgsConstructor
class ComponentD {
    final ServiceD service;

    void doSomething(ModelD model) {
        service.process(model);
    }
}

class ServiceD {
    public void process(ModelD model) {
        System.out.println(model);
    }
}

@Value
class ModelD {
    String name;
}