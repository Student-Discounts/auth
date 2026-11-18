package com.vk.studentdiscounts.admin;

import com.vk.studentdiscounts.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Назначает старших модераторов из настройки {@code sd.auth.bootstrap.senior-moderators} при каждом запуске.
 * Через API эту роль выдать нельзя, поэтому первый старший модератор появляется только так.
 * Пользователь должен быть уже зарегистрирован; повторный запуск ничего не меняет.
 */
@Component
public class SeniorModeratorBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeniorModeratorBootstrap.class);

    private final UserAdministrationService administration;
    private final List<String> emails;

    public SeniorModeratorBootstrap(UserAdministrationService administration, AuthProperties properties) {
        this.administration = administration;
        this.emails = properties.bootstrap().seniorModerators();
    }

    @Override
    public void run(ApplicationArguments args) {
        apply(emails);
    }

    public void apply(List<String> emails) {
        for (String email : emails) {
            if (!administration.grantSeniorModeratorAtStartup(email)) {
                log.warn("Старший модератор из sd.auth.bootstrap.senior-moderators не назначен: "
                        + "пользователь ещё не зарегистрирован");
            }
        }
    }
}
