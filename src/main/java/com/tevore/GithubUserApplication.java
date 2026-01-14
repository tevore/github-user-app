package com.tevore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching
@EnableRetry
@EnableAsync
public class GithubUserApplication {

	public static void main(String[] args) {
		SpringApplication.run(GithubUserApplication.class, args);
	}

}
