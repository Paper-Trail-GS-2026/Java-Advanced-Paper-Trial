package br.com.fiap.PaperTrail.guidance.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GuidanceProperties.class)
public class GuidanceConfig {
}
