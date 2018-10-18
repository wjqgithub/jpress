/**
 * Copyright (c) 2016-2019, Michael Yang 杨福海 (fuhai999@gmail.com).
 * <p>
 * Licensed under the GNU Lesser General Public License (LGPL) ,Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/lgpl-3.0.txt
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.jpress.web.wechat;

import com.jfinal.core.JFinal;
import com.jfinal.kit.HttpKit;
import com.jfinal.weixin.sdk.api.ApiResult;
import com.jfinal.weixin.sdk.kit.ParaMap;
import com.jfinal.weixin.sdk.utils.HttpUtils;
import io.jboot.utils.EncryptCookieUtils;
import io.jboot.utils.StrUtils;
import io.jboot.web.controller.annotation.RequestMapping;
import io.jpress.JPressConsts;
import io.jpress.JPressOptions;
import io.jpress.service.UserService;
import io.jpress.web.base.ControllerBase;

import javax.inject.Inject;

/**
 * @author Michael Yang 杨福海 （fuhai999@gmail.com）
 * @version V1.0
 * @Package io.jpress.web.wechat
 */
@RequestMapping("/wechat/authorization")
public class WechatAuthorizationController extends ControllerBase {


    /**
     * 获取用户信息的url地址
     * 会弹出框让用户进行授权
     */
    public static final String AUTHORIZE_URL = "https://open.weixin.qq.com/connect/oauth2/authorize"
            + "?appid={appid}"
            + "&redirect_uri={redirecturi}"
            + "&response_type=code"
            + "&scope=snsapi_userinfo"
            + "&state=235#wechat_redirect";


    /**
     * 静默授权的url地址
     */
    public static final String BASE_AUTHORIZE_URL = "https://open.weixin.qq.com/connect/oauth2/authorize"
            + "?appid={appid}"
            + "&redirect_uri={redirecturi}"
            + "&response_type=code"
            + "&scope=snsapi_base"
            + "&state=235#wechat_redirect";


    @Inject
    private UserService userService;


    public void index() {

        String gotoUrl = getPara("goto");


        String uid = EncryptCookieUtils.get(this, JPressConsts.COOKIE_UID);

        //说明当前用户已经登录
        if (StrUtils.isNotBlank(uid)) {
            redirect(StrUtils.urlDecode(gotoUrl));
            return;
        }

        String appId = JPressOptions.get(JPressConsts.OPTION_WECHAT_APPID);
        if (StrUtils.isBlank(appId)) {
            renderText("管理员的微信APPID配置错误，请联系管理在后台 -> 微信 -> 基础设置 配置正确的APPID。");
            return;
        }

        String domain = JPressOptions.get(JPressConsts.OPTION_WEB_DOMAIN);
        if (StrUtils.isBlank(domain)) {
            domain = getRequest().getScheme() + "://" + getRequest().getServerName();
        }

        if (StrUtils.isNotBlank(JFinal.me().getContextPath())) {
            domain = domain + JFinal.me().getContextPath();
        }


        //这个url是微信执行完毕之后跳转回来的url
        //也是下方的这个 action
        String redirecturi = domain + "/wechat/authorization/back?goto=" + gotoUrl;

        String wechatUrl = AUTHORIZE_URL.replace("{appid}", appId).replace("{redirecturi}", redirecturi);

        redirect(wechatUrl);
    }


    public void back() {
        String gotoUrl = getPara("goto");
        String code = getPara("code");

        String appId = JPressOptions.get(JPressConsts.OPTION_WECHAT_APPID);
        String appSecret = JPressOptions.get(JPressConsts.OPTION_WECHAT_APPSECRET);

        if (StrUtils.isBlank(appId) || StrUtils.isBlank(appSecret)) {
            renderText("管理员的微信AppId或AppSecret配置错误，请联系管理在后台 -> 微信 -> 基础设置 配置正确。");
            return;
        }

        ApiResult result = getOpenId(appId, appSecret, code);
        if (result == null) {
            renderText("网络错误，获取不到微信信息，请联系管理员");
            return;
        }

        //在某些时候获取不到微信信息
        //一般情况下是code有问题
        //重复发起刚才的过程
        if (result.isSucceed() == false) {
            redirect(StrUtils.urlDecode(gotoUrl));
            return;
        }

        String openId = result.getStr("openid");
        String accessToken = result.getStr("access_token");

        ApiResult userInfoResult = getUserInfo(openId, accessToken);

        Long userId = WechatKit.doGetOrCreateUser(userInfoResult, userService);
        if (userId == null) {
            //这种情况非常严重，一般情况下只有链接不上数据库了
            //或者是在 RPC 下，无法调用到 provider 了
            renderText("can not query user or save user Model to database");
            return;
        }

        EncryptCookieUtils.put(this, JPressConsts.COOKIE_UID, userId);
        redirect(StrUtils.urlDecode(gotoUrl));
    }


    private static ApiResult getUserInfo(String openId, String accessToken) {
        ParaMap pm = ParaMap.create("access_token", accessToken).put("openid", openId).put("lang", "zh_CN");
        return new ApiResult(HttpKit.get("https://api.weixin.qq.com/sns/userinfo", pm.getData()));
    }


    private static ApiResult getOpenId(String appId, String appSecret, String code) {

        String url = "https://api.weixin.qq.com/sns/oauth2/access_token" + "?appid={appid}"
                + "&secret={secret}" + "&code={code}" + "&grant_type=authorization_code";

        String getOpenIdUrl = url.replace("{appid}", appId).replace("{secret}", appSecret)
                .replace("{code}", code);

        String jsonResult = null;
        try {
            jsonResult = HttpUtils.get(getOpenIdUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (jsonResult == null)
            return null;

        return new ApiResult(jsonResult);
    }

}
