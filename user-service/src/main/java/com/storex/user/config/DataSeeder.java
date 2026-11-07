package com.storex.user.config;

import com.storex.user.entity.User;
import com.storex.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seedUsers(UserRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                log.debug("Users already seeded, skipping");
                return;
            }
            repository.save(new User("Nguyen Van A", "a@storex.local"));
            repository.save(new User("Tran Thi B", "b@storex.local"));
            log.info("Seeded {} users", repository.count());
        };
    }
}
