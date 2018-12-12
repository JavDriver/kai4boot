package top.kaiccc.kai4boot.sldp.service;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.kaiccc.kai4boot.common.utils.RuoKuaiUtils;
import top.kaiccc.kai4boot.common.utils.WxMsgUtils;
import top.kaiccc.kai4boot.sldp.dto.OrderListDto;

import java.util.Date;
import java.util.Map;

/**
 * 商城 参数分析
 *
 * 例：今天10号
 * 1.付款日期，前一天（9号）  开始时间 9号，结束时间9号
 *
 * 2.本月1号至今天的业绩。 付款日期，开始时间本月1号，结束时间 今天（10号）
 * @author kaiccc
 * @date 2018-12-11 16:48
 */
@Service
@Slf4j
public class SldpService {

    private static final String DATE_FORMAT = "MM月dd日";
    /**
     * 30 验证码 URL
     */
    private static final String CAPTCHA_URL = "http://www.sanlingdp.com/site/captcha";

    private static final String LOGIN_URL = "http://www.sanlingdp.com/admin-read/login";
    private static final String ORDER_URL = "http://www.sanlingdp.com/order-read/list?page=1&keyword=&page_size=20&status=-1&payment_status=-1&payment_type=-1&order_type=-1&category_type_id=-1&is_receipt=-1&deliver_shop_id=-1&start_date=&end_date=&pay_start_date={}&pay_end_date={}&province_id=23&city_id=-1&district_id=-1&map=1";

    /**
     * 订单信息 微信推送
     */
    public void orderWxScheduledPush(){
        log.info("orderWxScheduledPush start !");
        /*
         * 昨天
         */
        String yesterdayFormat = DateUtil.format(DateUtil.yesterday(), DATE_FORMAT);
        String yesterday = DateUtil.formatDate(DateUtil.yesterday());

        /*
         * 本月至今
         */
        String monthStartDate = DateUtil.formatDate(DateUtil.beginOfMonth(new Date()));
        String monthEndDate = DateUtil.today();

        int month = DateUtil.month(new Date()) + 1;
        try {
            this.login();
            WxMsgUtils.sendMessage("6496-4c4fa63accad466079b7f46315be1c50", yesterdayFormat+"-四川业绩情况统计", this.findOrderList(yesterday, yesterday));
            WxMsgUtils.sendMessage("6496-4c4fa63accad466079b7f46315be1c50", month +"月至今-四川业绩情况统计", this.findOrderList(monthStartDate, monthEndDate));
        } catch (Exception e) {
            log.error("异常了兄弟：", e);
            WxMsgUtils.sendMessage("4c4fa63accad466079b7f46315be1c50", "业绩查询失败", e.getMessage());
        }
        log.info("orderWxScheduledPush end !");
    }

    /**
     * 登录
     */
    private void login() throws Exception {
        HttpResponse httpResponse = HttpRequest.get(CAPTCHA_URL).execute();
        String imgBase64 = Base64.encode(httpResponse.bodyStream());
        String rkCaptcha = RuoKuaiUtils.ruoKuaiOCR(imgBase64);

        Map<String, Object> loginForm = MapUtil.newHashMap();
        loginForm.put("user_name", "ysx");
        loginForm.put("user_pwd", "ysx");
        loginForm.put("verify_code", rkCaptcha);

        HttpResponse loginResponse = HttpRequest.post(LOGIN_URL)
                .form(loginForm)
                .execute();
        JSONObject bodyJson = new JSONObject(loginResponse.body());

        if (!"0".equals(bodyJson.getStr("code"))){
            throw new Exception("### 登录失败，请呼叫皮皮猪");
        }

        log.info(loginResponse.toString());
    }

    /**
     * 查询 订单列表
     * @param startDate
     * @param endDate
     * @return
     * @throws Exception
     */
    private String findOrderList(String startDate, String endDate) throws Exception {
        String url = StrUtil.format(ORDER_URL, startDate, endDate);

        HttpResponse orderResponse = HttpRequest.get(url).execute();

        OrderListDto orderListDto = new Gson().fromJson(orderResponse.body(), OrderListDto.class);
        log.debug(orderResponse.toString());
        if (ObjectUtil.isNull(orderListDto) || orderListDto.getCode() != 0){
            throw new Exception(StrUtil.format("### 查询失败，请呼叫皮皮猪！{} {}", startDate, endDate));
        }
        return this.formatWxPushMsg(orderListDto);
    }

    /**
     * 推送消息格式化
     * @return
     */
    private String formatWxPushMsg(OrderListDto orderListDto){
        return StrUtil.format("### 订单合计：{} \r" +
                "### 实付金额合计：{} \r" +
                "### 可用PV合计：{} \r" +
                "`发送时间：{}` \r",
                orderListDto.getData().getCount(),
                orderListDto.getData().getPayment_amount(),
                orderListDto.getData().getPv(),
                DateUtil.now());
    }

}