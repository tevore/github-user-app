package com.tevore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class GithubUserApplication {

	public static void main(String[] args) {
		SpringApplication.run(GithubUserApplication.class, args);
	}

}
