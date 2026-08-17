package com.tongji.auth.mapper;

import com.tongji.auth.model.UserOAuthConnection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserOAuthConnectionMapper {
    UserOAuthConnection findByProviderAndOpenId(@Param("provider") String provider,
                                                @Param("openId") String openId);

    void insert(UserOAuthConnection connection);
}
