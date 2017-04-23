package com.whayer.wx.pay2;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.ClientProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.alibaba.fastjson.JSONObject;
import com.whayer.wx.common.X;
import com.whayer.wx.common.mvc.BaseController;
import com.whayer.wx.common.mvc.Box;
import com.whayer.wx.common.mvc.ResponseCondition;
import com.whayer.wx.order.service.OrderService;
import com.whayer.wx.order.vo.Order;
import com.whayer.wx.pay.util.CommonUtil;
import com.whayer.wx.pay.util.Constant;
import com.whayer.wx.pay.util.RandomUtils;
import com.whayer.wx.pay.vo.PayInfo;
import com.whayer.wx.pay2.service.PayV2Service;
import com.whayer.wx.pay2.util.HttpRequest;
import com.whayer.wx.pay2.util.XStreamUtil;
import com.whayer.wx.pay2.vo.OrderReturnInfo;

@RequestMapping(value = "/payV2")
@Controller
public class PayV2Controller extends BaseController{
	private final static Logger log = LoggerFactory.getLogger(PayV2Controller.class);
	
	@Resource
	private OrderService orderService;
	
	@Resource
	private PayV2Service payV2Service;
	
	/**
	 *    生成订单 (业务系统) --> 客户端申请支付
	 *    客户端获取登陆态code
	 *    服务端通过code获取openid/session_key
	 *         https://api.weixin.qq.com/sns/jscode2session?
	 *         appid=小程序ID&secret=小程序SECRET&js_code=登陆态code&grant_type=authorization_code
	 *	  获得预支付prepayId
	 *	       统一下单地址: https://api.mch.weixin.qq.com/pay/unifiedorder  
	 *                     @see https://pay.weixin.qq.com/wiki/doc/api/wxa/wxa_api.php?chapter=9_1
	 *		   预支付签名:1> 非空属性参与签名
	 *     				2> key按字典序排序
	 *     				3> MD5签名后转大写
	 *     				4> 最后签名赋值setSign(sign)
	 *               
	 *	  正式支付签名: 此次签名为客户端发起支付请求所需字段
	 *         签名所需字段: appId,nonceStr,package,signType,timeStamp,key (字典序后再加key)
	 *         1> nonceStr:后端生成随机字符串(低于32位)
	 *         2> package :'prepay_id='+prepayId
	 *         3> signType:MD5
	 *         4> timeStamp:时间戳
	 *         5> key:加密密钥
	 *	  输出客户端需要的参数
	 *		   paySign: 上述二次签名结果
	 *		   prepayId:预支付id
	 *		   nonceStr:上述服务端生产随机字符串(低于32位)
	 *	  客户端支付...
	 *    回调: 回调通知8次,服务端返回成功信息并停止通知,否则认为支付失败;
	 *         需要验证签名,避免回调通知伪造;
	 *         再对业务订单进行状态设置,同时需加以写锁控制,以免业务订单的数据混乱造成资金损失,账目对不上
	 *         
	 */
	
	/**
	 * 预支付
	 * @param code
	 * @param orderId
	 * @param model
	 * @param request
	 * @return
	 * @throws IOException 
	 * @throws NoSuchAlgorithmException 
	 * @throws KeyStoreException 
	 * @throws ClientProtocolException 
	 * @throws KeyManagementException 
	 * @throws UnrecoverableKeyException 
	 */
	@ResponseBody
    @RequestMapping(value = "/prepay")
    public ResponseCondition prePay(HttpServletRequest request, HttpServletResponse response) throws UnrecoverableKeyException, KeyManagementException, ClientProtocolException, KeyStoreException, NoSuchAlgorithmException, IOException {
		log.debug("PayV2Controller.prepay()");
		
		Box box = loadNewBox(request);
		String code = box.$p("code");
		String orderId = box.$p("orderId");
		
		if(isNullOrEmpty(orderId) || isNullOrEmpty(code)){
			log.error("参数为空: code: " + code + "; orderId: " + orderId);
			return getResponse(X.FALSE);
		}
		
		//获取业务系统订单信息
        Order order = orderService.getOrderById(orderId);
        if(isNullOrEmpty(order)){
        	log.error("没有此订单");
        	ResponseCondition res = getResponse(X.FALSE);
			res.setErrorMsg("没有此订单");
			return res;
        }
		
		//获取openid
		String openId = payV2Service.getOpenId(code);
		if(isNullOrEmpty(openId)){
			log.error("openid 获取失败");
			ResponseCondition res = getResponse(X.FALSE);
			res.setErrorMsg("openid 获取失败");
			return res;
		}
		
		openId = openId.replace("\"", "").trim();
		//获取客户端IP
		String clientIP = CommonUtil.getClientIp(request);
        //随机字符串(32字符以下)
        String randomNonceStr = RandomUtils.generateMixString(32);
        
        PayInfo payInfo = payV2Service.createPayInfo(openId, clientIP, randomNonceStr, order);
        String sign = payV2Service.getPrepaySign(payInfo);
        payInfo.setSign(sign);
        
        String result = HttpRequest.sendPost(Constant.URL_UNIFIED_ORDER, order);
		log.info("下单返回:"+result);
		
		OrderReturnInfo returnInfo = (OrderReturnInfo)XStreamUtil.Xml2Obj(result, OrderReturnInfo.class);
//		XStream xStream = new XStream();
//		xStream.alias("xml", OrderReturnInfo.class); 
//		OrderReturnInfo returnInfo = (OrderReturnInfo)xStream.fromXML(result);
		
		String prepayId = returnInfo.getPrepay_id();
		if(isNullOrEmpty(prepayId)){
			ResponseCondition res = getResponse(X.FALSE);
			res.setErrorMsg("prepayId 获取失败");
			return res;
		}
		
		//正式支付再次签名
		JSONObject json = payV2Service.getPaySign(prepayId);
		
		ResponseCondition res = getResponse(X.TRUE);
		List<JSONObject> list = new ArrayList<>();
		list.add(json);
		res.setList(list);
		return res;
	}
	
	
	
	/**
	 * 支付回调
	 * @param request
	 * @param response
	 * @throws Exception
	 */
	@ResponseBody
    @RequestMapping(value = "/callback", produces = "application/xml")
    public void callback(HttpServletRequest request, HttpServletResponse response) throws Exception {
		log.debug("PayV2Controller.callback()");
		
	}
}
