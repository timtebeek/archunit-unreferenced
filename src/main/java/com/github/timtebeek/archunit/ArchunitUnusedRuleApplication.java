package com.github.timtebeek.archunit;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

@Service
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

@Service
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

@Service
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
class ComponentD { // Unused
	final ServiceD service;

	void doSomething(ModelD model) { // Unused
		service.process(model);
	}
}

@Service
class ServiceD {
	void process(ModelD model) {
		System.out.println(model);
	}
}

@Value
class ModelD {
	String name;
}

@RestController
class ControllerE {
	@GetMapping(value = PathsE.PATH)
	public void get() {
	}
}

interface PathsE { // False positive
	String PATH = "/path";
}

@RestController
@RequiredArgsConstructor
class ControllerF {
	@PutMapping
	public Optional<String> put(Optional<ModelF> model) {
		return model.map(ModelF::toUpper);
	}
}

@Value
class ModelF {
	String name;

	String toUpper() {
		return name.toUpperCase();
	}
}
