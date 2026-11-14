package com.agentworkshq.chatengine.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ ChatProperties.class, LlmProperties.class })
public class ChatConfig {

}
