package com.myyak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MyyakServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MyyakServerApplication.class, args);
	}

}
