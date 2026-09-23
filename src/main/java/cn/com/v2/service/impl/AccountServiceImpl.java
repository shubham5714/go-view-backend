package cn.com.v2.service.impl;

import cn.com.v2.mapper.AccountMapper;
import cn.com.v2.model.Account;
import cn.com.v2.service.IAccountService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements IAccountService {

    @Override
    public Account getOrCreateAccountForUser(String userId) {
        LambdaQueryWrapper<Account> wrapper = new LambdaQueryWrapper<Account>()
                .eq(Account::getOwnerUserId, userId)
                .last("LIMIT 1");
        Account account = getOne(wrapper);
        if (account != null) {
            return account;
        }
        Account created = new Account();
        created.setOwnerUserId(userId);
        created.setName("Account-" + userId);
        created.setStatus("ACTIVE");
        save(created);
        return created;
    }

    @Override
    public Account getCurrentAccountForUser(String userId) {
        LambdaQueryWrapper<Account> wrapper = new LambdaQueryWrapper<Account>()
                .eq(Account::getOwnerUserId, userId)
                .last("LIMIT 1");
        return getOne(wrapper);
    }
}

