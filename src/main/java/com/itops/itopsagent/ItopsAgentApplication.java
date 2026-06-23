package com.itops.itopsagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.itops.itopsagent.mapper")
public class ItopsAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItopsAgentApplication.class, args);
    }

}
