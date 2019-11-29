package com.alibaba.dubbo.demo.consumer;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.demo.DemoService;
import com.alibaba.dubbo.demo.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author hejianglong
 * @date 2019/11/29
 */
@Service("testService")
public class TestService {

    @Autowired
    private UserService userService;

    @Autowired
    private DemoService demoService;

    public void tes() {
        String hl = demoService.sayHello("hello long");
        System.out.println(hl);
    }

}
