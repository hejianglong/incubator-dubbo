package com.alibaba.dubbo.demo.provider;

import com.alibaba.dubbo.demo.UserService;

/**
 * @author hejianglong
 * @date 2019/11/27
 */
public class UserServiceImpl implements UserService {

    @Override
    public void saveUser() {
        System.out.println("---save user");
    }

    @Override
    public void updateUser() {
        System.out.println("---update user");
    }
}
