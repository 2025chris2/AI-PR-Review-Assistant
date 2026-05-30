package com.prassistant.pr;

import com.prassistant.pr.config.PrReviewProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PrReviewProperties.class)
public class PrApplication {

	public static void main(String[] args) {
		SpringApplication.run(PrApplication.class, args);
	}

}
