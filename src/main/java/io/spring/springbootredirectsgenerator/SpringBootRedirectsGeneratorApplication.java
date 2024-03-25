package io.spring.springbootredirectsgenerator;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpringBootRedirectsGeneratorApplication {

	private static final Logger logger = LoggerFactory.getLogger(SpringBootRedirectsGeneratorApplication.class);

	@Bean
	ApplicationRunner applicationRunner() {
		return (args) -> {
			Path checkoutDir = Path.of(args.getNonOptionArgs().get(0));
			logger.info("Creating rewrites from checkout dir {}", checkoutDir);
			Redirects redirects = new Redirects(checkoutDir);
			redirects.add("actuator-api", "spring-boot-actuator-autoconfigure");
			redirects.add("gradle-plugin", "spring-boot-tools/spring-boot-gradle-plugin");
			redirects.add("maven-plugin", "spring-boot-tools/spring-boot-maven-plugin");
			redirects.add("", "spring-boot-docs");
			System.out.println();
			System.out.println();
			System.out.println(redirects.generate());
			System.out.println();
			System.out.println();
		};
	}


	public static void main(String[] args) {
		SpringApplication.run(SpringBootRedirectsGeneratorApplication.class, args);
	}

}
