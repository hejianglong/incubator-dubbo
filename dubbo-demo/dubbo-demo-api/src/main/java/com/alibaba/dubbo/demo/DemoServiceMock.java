/*
 * Project: com.alibaba.dubbo.demo
 * 
 * File Created at 2019/3/31
 * 
 * Copyright 2019 CMCC Corporation Limited.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * ZYHY Company. ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license.
 */
package com.alibaba.dubbo.demo;

/**
 * @author hejianglong
 * @date 2019/3/31 9:21 PM
 * @email hejlong@163.com
 * @Desc
 */
public class DemoServiceMock implements DemoService {
    @Override
    public String sayHello(String name) {
        return "wocuole";
    }
}
