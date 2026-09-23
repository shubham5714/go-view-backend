package cn.com.v2.service;

import cn.com.v2.model.Account;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IAccountService extends IService<Account> {

    Account getOrCreateAccountForUser(String userId);

    Account getCurrentAccountForUser(String userId);
}

