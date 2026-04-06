package com.bookphrase.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 보안 설정
 *
 * 일반 API  → 인증 없이 누구나 접근 가능 (퍼블릭 서비스)
 * Admin API → HTTP Basic Auth (/api/v1/admin/**)
 *             환경변수: ADMIN_USERNAME / ADMIN_PASSWORD
 *             CORS: 허용 Origin 없음 (브라우저에서 직접 접근 불가, curl/Postman만 가능)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:admin1234}")
    private String adminPassword;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().permitAll()
            )
            .httpBasic(httpBasic -> {});

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        var admin = User.builder()
                .username(adminUsername)
                .password("{noop}" + adminPassword)
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // 일반 API CORS — 프론트엔드 도메인만 허용
        CorsConfiguration publicConfig = new CorsConfiguration();
        publicConfig.setAllowedOrigins(List.of(
            "http://localhost:5173",
            "https://book-phrase-frontend.vercel.app",
            "https://todayogu.com",
            "https://www.todayogu.com"
        ));
        publicConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        publicConfig.setAllowedHeaders(List.of("*"));
        publicConfig.setAllowCredentials(true);

        // Admin API CORS — 허용 Origin 없음 (브라우저 CORS 차단, curl/Postman은 여전히 가능)
        // 실제 보호는 HTTP Basic Auth + 강한 비밀번호로 이루어짐
        CorsConfiguration adminConfig = new CorsConfiguration();
        adminConfig.setAllowedOrigins(List.of());
        adminConfig.setAllowedMethods(List.of());
        adminConfig.setAllowedHeaders(List.of());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/admin/**", adminConfig);
        source.registerCorsConfiguration("/**", publicConfig);
        return source;
    }
}
