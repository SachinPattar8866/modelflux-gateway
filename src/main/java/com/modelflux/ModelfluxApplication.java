package com.modelflux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.TimeZone;

@SpringBootApplication
public class ModelfluxApplication {

	public static void main(String[] args) {

		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(ModelfluxApplication.class, args);
	}

}
