package com.vk.studentdiscounts.auth;

import com.vk.studentdiscounts.error.ApiException;
import com.vk.studentdiscounts.error.ErrorCode;
import com.vk.studentdiscounts.session.SessionService;
import com.vk.studentdiscounts.session.TokenResponse;
import com.vk.studentdiscounts.user.User;
import com.vk.studentdiscounts.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * Регистрация и вход по email и паролю.
 * Хеширование пароля намеренно медленное, поэтому методы не открывают транзакцию
 * и не держат соединение с БД, пока считается хеш.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, SessionService sessions, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public User register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        User user = User.register(email, passwordEncoder.encode(request.password()),
                request.displayName().strip(), clock.instant());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Тот же email успели зарегистрировать между проверкой и вставкой.
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
    }

    public TokenResponse login(LoginRequest request, String userAgent) {
        User user = users.findByEmail(User.normalizeEmail(request.email())).orElse(null);
        // Пароль проверяется и для неизвестного email, чтобы по времени ответа нельзя было узнать,
        // зарегистрирован ли адрес.
        String passwordHash = user != null ? user.getPasswordHash() : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        if (user == null || !passwordMatches) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.isBlocked()) {
            throw new ApiException(ErrorCode.ACCOUNT_BLOCKED);
        }
        return sessions.open(user, userAgent);
    }
}
