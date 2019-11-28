package com.alibaba.dubbo.demo.provider.annotation;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

/**
 * @author hejianglong
 * @date 2019/11/27
 */
@Configuration
@ImportResource({"classpath:/META-INF/spring/dubbo-demo-provider.xml"})
public class AnnConfig {
}
