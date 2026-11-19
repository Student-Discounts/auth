package com.vk.studentdiscounts.authz;

import com.vk.studentdiscounts.token.TokenClaims;
import com.vk.studentdiscounts.user.Role;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Превращает claim {@code roles} в authority: {@code ROLE_STUDENT} для каждой роли
 * и имя каждого разрешения из {@link RolePermissions}. Неизвестные роли пропускаются.
 */
public class JwtAuthorities implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final Set<String> KNOWN_ROLES = Arrays.stream(Role.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<String> claim = jwt.getClaimAsStringList(TokenClaims.ROLES);
        List<Role> roles = claim == null ? List.of() : claim.stream()
                .filter(KNOWN_ROLES::contains)
                .map(Role::valueOf)
                .toList();

        List<GrantedAuthority> authorities = new ArrayList<>();
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name())));
        RolePermissions.of(roles).forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission.name())));
        return authorities;
    }
}
