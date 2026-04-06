package com.bookphrase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BookPhraseApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookPhraseApplication.class, args);
	}

}
