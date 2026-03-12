package com.example.greenhouse.config;

import jakarta.annotation.PostConstruct;
import java.util.UUID;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageChannel;

@Configuration
public class MqttConfig {

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String baseClientId;

    private String clientId;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    @PostConstruct
    public void init() {
        this.clientId = baseClientId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setKeepAliveInterval(30);
        options.setConnectionTimeout(10);
        if (!username.isEmpty()) {
            options.setUserName(username);
        }
        if (!password.isEmpty()) {
            options.setPassword(password.toCharArray());
        }
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInbound(
            MqttPahoClientFactory mqttClientFactory,
            @Value("${mqtt.client-id}") String baseClientId,
            @Value("${mqtt.sensor-topic}") String sensorTopic,
            @Value("${mqtt.actuator-topic}") String actuatorTopic
    ) {
        String uniqueClientId = baseClientId + "-consumer-" + UUID.randomUUID().toString().substring(0, 8);
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(uniqueClientId, mqttClientFactory, sensorTopic, actuatorTopic);
        adapter.setOutputChannel(mqttInboundChannel());
        adapter.setQos(1);
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MqttPahoMessageHandler mqttOutbound(MqttPahoClientFactory mqttClientFactory,
                                               @Value("${mqtt.client-id}") String baseClientId) {
        String uniqueClientId = baseClientId + "-producer-" + UUID.randomUUID().toString().substring(0, 8);
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(uniqueClientId, mqttClientFactory);
        handler.setAsync(true);
        handler.setDefaultTopic("cmd/default");
        return handler;
    }
}
