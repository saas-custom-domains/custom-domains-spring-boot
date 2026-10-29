package com.example.billport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BillportApplication {

	public static void main(String[] args) {
		SpringApplication.run(BillportApplication.class, args);
	}

}
