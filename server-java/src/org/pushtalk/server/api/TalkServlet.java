package org.pushtalk.server.api;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.pushtalk.server.Config;
import org.pushtalk.server.model.Channel;
import org.pushtalk.server.model.Message;
import org.pushtalk.server.utils.ServiceUtils;
import org.pushtalk.server.web.common.FreemarkerBaseServlet;

import cn.jpush.api.JPushClient;
import cn.jpush.api.push.CustomMessageParams;
import cn.jpush.api.push.MessageResult;
import cn.jpush.api.push.ReceiverTypeEnum;

public class TalkServlet extends FreemarkerBaseServlet {
	private static final long serialVersionUID = 348660245631638687L;
    private static Logger LOG = Logger.getLogger(TalkServlet.class);

    // 第次重新启动，会以不同的 sendNo 作为起点，从而避免重复记录
	private static int sendId = getRandomSendNo();
	private static final JPushClient jpushClient = new JPushClient(
				Config.JPUSH_MASTER_SECRET,Config.JPUSH_APPKEY);

	@Override
	public void process(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
	    LOG.debug("action - talk");
        Map<String, Object> data = new HashMap<String, Object>();

        String udid = request.getParameter("udid");
        String channelName = request.getParameter("channel_name");
        String friend = request.getParameter("friend");
        String content = request.getParameter("message");
        String chatNo = request.getParameter("chatNo");
        if (null == udid) {
            return;
        }
        if (null == channelName && null == friend) {
            return;
        }
        if (StringUtils.isEmpty(content)) {
            return;
        }
        
        LOG.debug("udid (" + udid +") talk:" + content);
        
        String chatting = null;
        MessageResult msgResult = null;
        String myName = talkService.getUserByUdid(udid).getName();
    	
        if (null != channelName) {
            Channel channel = talkService.getChannelByName(channelName);
            if (null == channel) {
                data.put("error", "the channel does not exist - " + channelName);
            } else {
            	sendId ++;
	            Map<String, Object> extras = new HashMap<String, Object>();
	            extras.put("channel", channelName);
	            extras.put("sendNo", sendId);
	            
                CustomMessageParams params = new CustomMessageParams();
                params.setReceiverType(ReceiverTypeEnum.TAG);
                params.setReceiverValue(ServiceUtils.postfixAliasAndTag(channelName));
                msgResult = jpushClient.sendCustomMessage(myName, content, params, extras);
	            
	            chatting = ServiceUtils.getChattingChannel(channelName);
            }
        } else {
        	sendId ++;
            Map<String, Object> extras = new HashMap<String, Object>();
            extras.put("sendNo", sendId);
            
            CustomMessageParams params = new CustomMessageParams();
            params.setReceiverType(ReceiverTypeEnum.ALIAS);
            params.setReceiverValue(ServiceUtils.postfixAliasAndTag(friend));
            msgResult = jpushClient.sendCustomMessage(myName, content, params, extras);
            
            chatting = ServiceUtils.getChattingChannel(myName, friend);
        }
        
        if (!msgResult.isResultOK()) {
            String info = "Send msg error - errorCode:" + msgResult.getErrorCode()
                    + ", errorMsg:" + msgResult.getErrorMessage();
            LOG.error(info);
            data.put("error", info);
            
        } else {
            Message message = new Message(msgResult.getSendNo(), myName, content, channelName);
            talkService.putMessage(udid, chatting, message);
            
            if (null != friend) {
                chatting = ServiceUtils.getChattingFriend(friend);
            }
            talkService.newRecentChat(udid, chatting);
            talkService.showedMessage(udid, chatting);
            
            data.put("sent", true);
            data.put("message", message);
            data.put("chatNo", chatNo);
        }
        processJSON(response, data);
	}
	
	
    public static final int MAX = Integer.MAX_VALUE / 2;
    public static final int MIN = MAX / 2;
    
    /**
     * 保持 sendNo 的唯一性是有必要的
     * It is very important to keep sendNo unique.
     * @return sendNo
     */
    public static int getRandomSendNo() {
        return (int) (MIN + Math.random() * (MAX - MIN));
    }
    
}
