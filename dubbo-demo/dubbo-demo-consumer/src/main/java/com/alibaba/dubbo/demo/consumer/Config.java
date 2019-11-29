package com.alibaba.dubbo.demo.consumer;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;

/**
 * @author hejianglong
 * @date 2019/11/29
 */
@Configuration
@ImportResource({"classpath:/META-INF/spring/dubbo-demo-consumer.xml"})
@Import(TestService.class)
public class Config {
}
