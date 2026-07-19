package com.partnerplan.delivery;

import com.partnerplan.delivery.config.DeliveryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DeliveryProperties.class)
public class PartnerDeliveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(PartnerDeliveryApplication.class, args);
    }
}
