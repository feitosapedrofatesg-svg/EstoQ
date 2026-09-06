package com.estoq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EstoqApplication {

	public static void main(String[] args) {
		SpringApplication.run(EstoqApplication.class, args);
	}

}