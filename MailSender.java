package com.visionet.letsdesk.app.common.mail;

import com.sun.mail.util.MailSSLSocketFactory;
import com.visionet.letsdesk.app.channel.service.mail.MailProcessService;
import com.visionet.letsdesk.app.common.modules.props.PropsKeys;
import com.visionet.letsdesk.app.common.modules.props.PropsUtil;
import com.visionet.letsdesk.app.common.modules.validate.Validator;
import com.visionet.letsdesk.app.foundation.vo.AttachmentVo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.*;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Properties;


public class MailSender extends MailService{
	private static Log log = LogFactory.getLog(MailSender.class);

	protected int port = 25;
	protected String protocol = "smtp";
	private static Logger logger = LoggerFactory.getLogger(MailSender.class);

	public MailSender(Properties prop){
		super(prop);
		if(prop.getProperty(PROTOCOL) != null){
			this.protocol = prop.getProperty(PROTOCOL);
		}
		if(prop.getProperty(PORT) != null){
			this.port = Integer.parseInt(prop.getProperty(PORT));
		}
	}


	public static void sendEmail(MailBean mailBean,String userName,String userPwd, String host, int port, boolean smtpSSL) throws MessagingException,GeneralSecurityException{
		Properties prop = new Properties();
		prop.setProperty(HOST, host);
		prop.setProperty(PROTOCOL, "smtp");
		prop.setProperty(USERNAME, userName);
		prop.setProperty(USERPWD, userPwd);
		prop.setProperty(PORT, port + "");

		if(host.equals("smtp.qq.com")) {
			prop.setProperty("mail.smtp.auth", "true");
			MailSSLSocketFactory sf = new MailSSLSocketFactory();
			sf.setTrustAllHosts(true);
			prop.put("mail.smtp.ssl.enable", "true");
			prop.put("mail.smtp.ssl.socketFactory", sf);
		}else if(smtpSSL){
            prop.setProperty("SMTPSSL", "1");
//        		prop.setProperty("SSL","true");
		}

		MailSender sender = new MailSender(prop);
		sender.send(mailBean);
	}


	public class SendCallBack{
		public void callBack(){};
	}

	public  void sendAsyn(MailBean mailBean,SendCallBack sendCallBack){
		Thread thread = new Thread(new MailSenderThread(mailBean,this,sendCallBack));
		thread.start();
	}

	public void send(MailBean mailBean) throws MessagingException{
		Session session = getSession();

		MimeMessage msg;
		try {
			msg = _convert(mailBean, session);

			if(isDebug){
				debug(mailBean,logger);
			}

			if(isDebug){
				oldTimestamp = System.currentTimeMillis();
			}

			_send(session,msg);

			if(isDebug){
				newTimestamp = System.currentTimeMillis();
				logger.debug("duration:" + (newTimestamp - oldTimestamp) + "ms");
			}
		} catch (IOException e) {
			log.error(e.toString(),e);
			logger.debug("发送邮件异常", e);
		}

	}

	public boolean sendTest(){
		Session session = getSession();
		Transport transport;
		try {
			transport = session.getTransport(protocol);
			transport.connect(host,port,userName,userPwd);
		} catch (Exception e) {
			logger.error("test send email error!", e);
			return false;
		}
		return true;
	}

	private  void _send(Session session,MimeMessage msg) throws MessagingException{
		msg.saveChanges();
//		System.out.println(host + " : " + userName + " : " + userPwd + " : "+port);
		Transport transport = session.getTransport(protocol);
		transport.connect(host, port, userName,userPwd);
		transport.sendMessage(msg,msg.getAllRecipients());
		transport.close();
	}

	@Override
	protected Session getSession(){
		Properties prop = this.prop;
		Session session = Session.getInstance(prop);
		session.setDebug(false);
		return session;
	}

	private  MimeMessage _convert(MailBean mailBean,Session session) throws MessagingException, IOException{
		MimeMessage msg = new MimeMessage(session);
		msg.setSubject(mailBean.getSubject());

		msg.setFrom(new InternetAddress(mailBean.getSender()));
		msg.setSender(new InternetAddress(mailBean.getSender()));

//		if(null != mailBean.getSentDate()){
//			msg.setSentDate(mailBean.getSentDate());
//		}else{
//			msg.setSentDate(new Date());
//		}

		//处理邮件头信息
		if(null != mailBean.getHeaders()){
			for (Map.Entry<String,String> entry : mailBean.getHeaders().entrySet()) {
				msg.setHeader(entry.getKey(), entry.getValue());
			}
		}

		//处理接收人
		if(null != mailBean.getToAddresses()){
			InternetAddress[] to = new InternetAddress[mailBean.getToAddresses().size()];

			for (int i = 0; i < to.length; i++) {
				to[i] = new InternetAddress(mailBean.getToAddresses().get(i));
			}
			msg.setRecipients(RecipientType.TO, to);
		}
		//处理暗送人
		if(null != mailBean.getBccAddresses()){
			InternetAddress[] bcc = new InternetAddress[mailBean.getBccAddresses().size()];

			for (int i = 0; i < bcc.length; i++) {
				bcc[i] = new InternetAddress(mailBean.getBccAddresses().get(i));
			}
			msg.setRecipients(RecipientType.BCC	, bcc);
		}
		//处理抄送人
		if(null != mailBean.getCcAddresses()){
			InternetAddress[] cc = new InternetAddress[mailBean.getCcAddresses().size()];

			for (int i = 0; i < cc.length; i++) {
				cc[i] = new InternetAddress(mailBean.getCcAddresses().get(i));
			}
			msg.setRecipients(RecipientType.CC, cc);
		}
		//处理回复邮箱
		if(null != mailBean.getReplyToAddresses()){
			InternetAddress[] replyTo = new InternetAddress[mailBean.getReplyToAddresses().size()];

			for (int i = 0; i < replyTo.length; i++) {
				replyTo[i] = new InternetAddress(mailBean.getReplyToAddresses().get(i));
			}
			msg.setReplyTo(replyTo);
		}

		if(mailBean.isReceipt()){
			msg.addHeader("Disposition-Notification-To", "1");
		}

		_dealMsgContent(mailBean,msg);

		return msg;
	}

	private  void _dealMsgContent(MailBean mailBean,MimeMessage msg) throws MessagingException, IOException{
		boolean hasAttachments = mailBean.getAttachments() != null && mailBean.getAttachments().size() > 0;
		boolean hasInnerResources = mailBean.getInnerResources() != null && mailBean.getInnerResources().size() > 0;


		//处理邮件内容
		MimeMultipart contentMultipart = new MimeMultipart("alternative");

		MimeBodyPart htmlPart = new MimeBodyPart();
		htmlPart.setContent(mailBean.getContent(), "text/html;charset=\"UTF-8\"");
		MimeBodyPart txtPart = new MimeBodyPart();
		txtPart.setText(mailBean.getContent());

		contentMultipart.addBodyPart(txtPart);
		contentMultipart.addBodyPart(htmlPart);


		if(hasAttachments || hasInnerResources){
			MimeMultipart rootMultipart = new MimeMultipart();
			rootMultipart.setSubType(hasAttachments ? "mixed" : "related");

			MimeBodyPart contentPart = new MimeBodyPart();
			contentPart.setContent(contentMultipart);

			//处理附件
			if(hasAttachments){
				for (AttachmentVo vo: mailBean.getAttachments()) {
					MimeBodyPart filePart = new MimeBodyPart();
					filePart.setDisposition(Part.ATTACHMENT);
					String path = vo.getFullPath();
					if(Validator.isNull(path)){
						if(Validator.isNotNull(vo.getRelativePath())){
							if(vo.getRelativePath().startsWith(PropsUtil.getProperty(PropsKeys.UPLOAD_FILE_DOWNLOAD_PATH))){
								vo.setRelativePath(vo.getRelativePath().replaceAll(PropsUtil.getProperty(PropsKeys.UPLOAD_FILE_DOWNLOAD_PATH),""));
							}
							path = MailProcessService.DirPath + vo.getRelativePath();
						}else {
							continue;
						}
					}
					DataSource source = new FileDataSource(new File(path));
					filePart.setDataHandler(new DataHandler(source));
					filePart.setFileName(MimeUtility.encodeText(vo.getRealName()));
					rootMultipart.addBodyPart(filePart);
//					System.out.println("---path="+path);
				}
			}

			//处理内嵌资源
			if(hasInnerResources){
				MimeMultipart innerMultipart = new MimeMultipart("related");
				innerMultipart.addBodyPart(contentPart);

				for (AttachmentVo vo: mailBean.getInnerResources()) {
					MimeBodyPart innerFilePart = new MimeBodyPart();
					String path = vo.getFullPath();
					if(Validator.isNull(path)){
						if(Validator.isNotNull(vo.getRelativePath())){
							if(vo.getRelativePath().startsWith(PropsUtil.getProperty(PropsKeys.UPLOAD_FILE_DOWNLOAD_PATH))){
								vo.setRelativePath(vo.getRelativePath().replaceAll(PropsUtil.getProperty(PropsKeys.UPLOAD_FILE_DOWNLOAD_PATH),""));
							}
							path = MailProcessService.DirPath + vo.getRelativePath();
						}else {
							continue;
						}
					}
					DataSource source = new FileDataSource(new File(path));
					innerFilePart.setDataHandler(new DataHandler(source));
					innerFilePart.setDisposition(Part.INLINE);
					innerFilePart.setFileName(MimeUtility.encodeText(vo.getRealName()));
					innerFilePart.setContentID(vo.getUuidName());

					innerMultipart.addBodyPart(innerFilePart);
				}

				if(hasAttachments){
					MimeBodyPart innerPart = new MimeBodyPart();
					innerPart.setContent(innerMultipart);
					rootMultipart.addBodyPart(innerPart);
				}else{
					rootMultipart = innerMultipart;
				}
			}else{
				rootMultipart.addBodyPart(contentPart);
			}

			msg.setContent(rootMultipart);
		}else{
			msg.setContent(contentMultipart);
		}
	}

	private class MailSenderThread implements Runnable{

		private MailBean mailBean;
		private MailSender mailSender;
		private SendCallBack sendCallBack;

		private Logger logger = LoggerFactory.getLogger(MailSenderThread.class);

		public MailSenderThread(MailBean mailBean,MailSender mailSender,SendCallBack sendCallBack){
			this.mailBean = mailBean;
			this.mailSender = mailSender;
			this.sendCallBack = sendCallBack;
		}

		@Override
		public void run() {
			try {
				mailSender.send(mailBean);
				if(sendCallBack != null){
					sendCallBack.callBack();
				}
			} catch (MessagingException e) {
				logger.error("异步发送邮件错误", e);
			}
		}
	}

}
