package com.aiconsultant.consultant.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.aiconsultant.consultant.entity.User;
import com.aiconsultant.consultant.pojo.Result;
import org.springframework.stereotype.Service;

@Service
public interface UserService extends IService<User> {
    Result login(String username, String password);

    Result register(User user);
}
