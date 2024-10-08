package com.example.BlessingChess.service;

import com.example.BlessingChess.data.dto.LoginData;
import com.example.BlessingChess.data.dto.WeChatLoginModel;
import com.example.BlessingChess.data.po.User;
import com.example.BlessingChess.data.vo.Result;
import com.example.BlessingChess.data.vo.WeChatLoginResult;
import com.example.BlessingChess.data.wechat.WeChat;
import com.example.BlessingChess.mapper.UserMapper;
import com.example.BlessingChess.utils.*;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Random;


/**
 * 用于微信用户登录
 * 验证微信登录并发放JWT令牌
 *
 * @author LJY LXY
 * @version 2.0
 * @since 2024-02-5
 */
@Service
public class LoginService {

    //注入UserMapper 
    @Autowired
    UserMapper userMapper;

    //注入UserUtils
    @Autowired
    UserUtils userUtils;

    @Autowired
    RandomUtil randomUtil;


    //接受前端传入的登录信息并处理
    public Result getToken(LoginData loginData) {

        //封装微信登录模型
        WeChatLoginModel model = new WeChatLoginModel(loginData.getCode(), WeChat.appId,WeChat.secret,loginData.getWxUsername());

        //传入登录模型，取得登录接口响应的信息
        WeChatLoginResult result = WeChatLogin(model);

        //返回结果，成功则返回Token
        if(result.getToken() == null) {
            return Result.error(0, "登录失败");
        }
        else {
            return Result.success(result.getToken(), "登录成功");
        }
    }


    //返回登录接口响应数据
    public WeChatLoginResult WeChatLogin(WeChatLoginModel model) {

        //实例化返回模型
        WeChatLoginResult result = new WeChatLoginResult();

        try {
            //打包好url并发送请求
            String urlFormat = "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s";
            String url = String.format(urlFormat, WeChat.appId, WeChat.secret, model.getCode());
            String response = HttpClientUtil.doGet(url);

            //将请求响应信息转为json格式
            JSONObject jsonObject = new JSONObject(response);

            //判断请求是否有错
            if (! jsonObject.has("errcode")) {

                //在数据库中根据openid查找信息
                User user;
                user = userMapper.findUserByOpenId(jsonObject.getString("openid"));

                //如果没有登录过，数据库里面没有数据
                if ( ! userUtils.hasUser(user)) {

                    //打包User实体
                    user = new User();
                    user.setSessionKey(jsonObject.getString("session_key"));
                    user.setOpenId(jsonObject.getString("openid"));
                    user.setLastTime(LocalDateTime.now());
                    user.setUsername(model.getWxUsername());
                    user.setInvitationCode(randomUtil.RandomInvitationCode());

                    //在数据库中插入新用户
                    userMapper.insertNewUser(user);
                }

                //如果有登录过
                else {

                    //更新最后登录时间
                    user.setLastTime(LocalDateTime.now());
                    userMapper.updateTimeById(user);
                }

                //将用户信息传入token的载荷中
                String userToken =(JwtUtils.generateJwt(user.toMap()));

                //封装返回模型
                result.setToken(userToken);
            }
        }catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return result;
    }

    /**
     * 暂时用这个来测试user数据库的功能（因为缺少code）
     *
     * @param loginData
     * @return 模拟的token
     */
    public Result testGetToken(LoginData loginData) {

        WeChatLoginResult result = new WeChatLoginResult();

        //在数据库中根据openid查找信息
        User user;
        user = userMapper.findUserByOpenId(loginData.getCode());

        //如果没有登录过，数据库里面没有数据
        if (user == null || user.getId() == null) {

            //打包User实体
            user = new User();
            user.setSessionKey(RandomStringGenerator.generateRandomString(10));
            user.setOpenId(loginData.getCode());
            user.setLastTime(LocalDateTime.now());
            user.setUsername(loginData.getWxUsername());
            user.setInvitationCode(randomUtil.RandomInvitationCode());
            //在数据库中插入新用户
            userMapper.insertNewUser(user);
        }
        //如果有登录过
        else {
            //更新最后登录时间
            user.setLastTime(LocalDateTime.now());
            userMapper.updateTimeById(user);/*TODO：这里用了BaseMapper的update，但是不知道这样写行不行*/
        }
        //将用户信息传入token的载荷中
        String userToken = (JwtUtils.generateJwt(user.toMap()));
        //封装返回模型
        result.setToken(userToken);

        //返回结果，成功则返回Token
        if (result.getToken() == null) {
            return Result.error(0, "登录失败");
        } else {
            return Result.success(result.getToken(), "登录成功");
        }
    }
}
