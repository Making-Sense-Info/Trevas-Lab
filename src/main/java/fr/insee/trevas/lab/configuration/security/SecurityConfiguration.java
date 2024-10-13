package fr.insee.trevas.lab.configuration.security;

import fr.insee.trevas.lab.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Value("${app.security.enabled}")
    private boolean securityEnabled;

    @Value("${spring.security.oauth2.login-page}")
    private String loginPage;

    @Value("${jwt.username-claim}")
    private String usernameClaim;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (!securityEnabled) {
            // Désactive toute sécurité
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize
                            .anyRequest().permitAll()
                    );
        } else {
            // Active OIDC
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/public").permitAll()  // Endpoint public
                            .anyRequest().authenticated()
                    )
                    .oauth2Login(oauth2 -> oauth2
                            .loginPage("/oauth2/authorization/myclient") // Page de login personnalisée, peut être modifiée
                    );
        }
        return http.build();
    }

    @Bean
    public UserProvider getUserProvider() {
        return auth -> {
            final User user = new User();
            if (null == auth) {
                return user;
            }
            final Jwt jwt = (Jwt) auth.getPrincipal();
            user.setId(jwt.getClaimAsString(usernameClaim));
            user.setAuthToken(jwt.getTokenValue());
            return user;
        };
    }
}
