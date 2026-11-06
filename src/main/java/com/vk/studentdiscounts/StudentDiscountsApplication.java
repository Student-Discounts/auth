package com.vk.studentdiscounts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StudentDiscountsApplication {

    static void main(String[] args) {
        SpringApplication.run(StudentDiscountsApplication.class, args);
    }

}
