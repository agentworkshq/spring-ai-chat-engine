package com.agentworkshq.chatengine;

import org.springframework.boot.SpringApplication;

public class TestSpringAiChatEngineApplication {

	public static void main(String[] args) {
		SpringApplication.from(SpringAiChatEngineApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
