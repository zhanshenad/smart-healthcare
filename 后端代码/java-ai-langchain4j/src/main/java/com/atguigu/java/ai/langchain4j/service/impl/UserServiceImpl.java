package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.entity.User;
import com.atguigu.java.ai.langchain4j.mapper.UserMapper;
import com.atguigu.java.ai.langchain4j.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public User findByPhone(String phone) {
        return baseMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
    }

    @Override
    public User getOrCreateByPhone(String phone) {
        User existing = findByPhone(phone);
        if (existing != null) {
            return existing;
        }

        User user = new User();
        user.setPhone(phone);
        user.setNickname("用户" + phone.substring(phone.length() - 4));
        user.setStatus(1);
        try {
            baseMapper.insert(user);
            return user;
        } catch (DuplicateKeyException e) {
            // 并发首次登录时可能同时插入，唯一索引保证只有一个成功
            User concurrentUser = findByPhone(phone);
            if (concurrentUser == null) {
                throw new IllegalStateException("用户创建失败", e);
            }
            return concurrentUser;
        }
    }
}
