package com.visionet.letsdesk.app.common.mail;


import com.google.common.collect.Lists;
import com.visionet.letsdesk.app.channel.service.mail.MailProcessService;
import com.visionet.letsdesk.app.common.file.FileUtil;
import com.visionet.letsdesk.app.common.modules.validate.Validator;
import com.visionet.letsdesk.app.foundation.vo.AttachmentVo;
import org.apache.commons.codec.binary.Base64;
import org.apache.xmlbeans.impl.xb.xsdschema.Public;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;
import javax.mail.internet.MimeUtility;
import javax.mail.search.SearchTerm;
import java.io.*;
import java.security.PrivateKey;
import java.util.*;

public class MailRecipient extends MailService {
	private static Logger logger = LoggerFactory.getLogger(MailRecipient.class);

	protected int port = 143;
	protected String protocol = "imap";
	private boolean downloadFile;
	private String folderName;
	private String resourceDirPath;
	private MailListener listener;
	private SearchTerm searchTerm;
	
	public MailRecipient(Properties prop){
		super(prop);
	}
	
	public List<MailBean> recipient() throws MessagingException{
		Session session = getSession();
		Store store = session.getStore(protocol);
		store.connect(host,port,userName,userPwd); 
		
		boolean recipientBool = true;
		if(listener != null){
			recipientBool = listener.connect(store);
		}
		
		List<MailBean> mailBeanList = new ArrayList<MailBean>();
		
		if(recipientBool){
			mailBeanList = _recipient(store);
		} 
		
		store.close();
		
		return mailBeanList;
	}

	private List<MailBean> _recipient(Store store) throws MessagingException {
		Folder folder = store.getFolder(folderName == null ? "INBOX" : folderName);
		List<MailBean> mailBeanList = new ArrayList<MailBean>();
		
		if(folder != null){
			folder.open(Folder.READ_WRITE);
			Message[] msgArr = null;
			
			if(searchTerm != null){
				msgArr = folder.search(searchTerm);
			}else{
				msgArr = folder.getMessages();
			}

			for (int i = 0; i < msgArr.length; i++) {
				MimeMessage mimeMessage = (MimeMessage)msgArr[i];

				Flags flags = mimeMessage.getFlags();
				if (flags.contains(Flags.Flag.SEEN)){
					continue;
                }
				MailBean mailBean = _convert(mimeMessage);
				if(mailBean==null){
					break;
				}
				
				if(isDebug){
					debug(mailBean, logger);
				}
				
				if(listener != null){
					listener.each(msgArr[i], mailBean);
				}
				mailBeanList.add(mailBean);
				mimeMessage.setFlag(Flags.Flag.DELETED, true);
			}
			folder.close(true);
		}
		
		return mailBeanList;
	}

	private MailBean _convert(MimeMessage msg) throws MessagingException {
		MailBean mailBean = new MailBean();
		try{
            mailBean.setMessageID(msg.getMessageID());

            String fromAddress = null;

            if(null != msg.getFrom() && msg.getFrom().length > 0){
                InternetAddress[] addresses = (InternetAddress[])msg.getFrom();
                fromAddress  = addresses[0].getAddress();
            }else if(null != msg.getSender()){
                InternetAddress address = (InternetAddress)msg.getSender();
                fromAddress = address.getAddress();
            }else{
                fromAddress = "unnamed";
            }

            mailBean.setSender(fromAddress);
			mailBean.setSubject(new MailSubject(msg).getSubject());
            mailBean.setSenderDate(msg.getSentDate());

            InternetAddress[] tos = (InternetAddress[])msg.getRecipients(RecipientType.TO);
            mailBean.setToAddresses(new ArrayList<String>());
            for (int i = 0; i < tos.length; i++) {
                mailBean.getToAddresses().add(tos[i].getAddress());
            }

            InternetAddress[] ccs = (InternetAddress[])msg.getRecipients(RecipientType.CC);
            mailBean.setCcAddresses(new ArrayList<String>());
            if(ccs != null && ccs.length>0){
	            for (int i = 0; i < ccs.length; i++) {
	                mailBean.getCcAddresses().add(ccs[i].getAddress());
	            }
            }

            InternetAddress[] bccs = (InternetAddress[])msg.getRecipients(RecipientType.TO);
            mailBean.setBccAddresses(new ArrayList<String>());
            for (int i = 0; i < bccs.length; i++) {
                mailBean.getBccAddresses().add(bccs[i].getAddress());
            }

            InternetAddress[] replays = (InternetAddress[])msg.getReplyTo();
            mailBean.setReplyToAddresses(new ArrayList<String>());
            for (int i = 0; i < replays.length; i++) {
                mailBean.getReplyToAddresses().add(replays[i].getAddress());
            }

            if(msg.getHeader("Disposition-Notification-TO") != null
                    && msg.getHeader("Disposition-Notification-TO").length > 0){
                mailBean.setReceipt(true);
            }

            @SuppressWarnings("unchecked")
            Enumeration<Header> headers = (Enumeration<Header>)msg.getAllHeaders();
            mailBean.setHeaders(new HashMap<String,String>());
            while (headers.hasMoreElements()) {
                Header header = headers.nextElement();
                mailBean.getHeaders().put(header.getName(), header.getValue());
            }

            mailBean.setAttachments(Lists.newArrayList());
            mailBean.setInnerResources(Lists.newArrayList());

            try {
                _convertMessageBody((Part)msg,mailBean);
            } catch (IOException e) {
                logger.error("get email content error!", e);
            }

            return mailBean;
        }catch (MessagingException me){
			logger.error("MailRecipient _convert error:"+me.toString());
			return null;
		}
	}
	private static String getcontext(String str){
		if (str == null) {
			return null;
		} else {
			try {
				String s1 = new String(Base64.decodeBase64(str), "gb2312");
				return s1;
			} catch (UnsupportedEncodingException var3) {
				return str;
			}
		}
	}
	
	private void _convertMessageBody(Part part,MailBean mailBean) throws MessagingException, IOException {
		if(part.isMimeType("text/plain")){
			if(part.getDisposition() != null){
				String strFileName = MimeUtility.decodeText(part.getFileName()); //MimeUtility.decodeText解决附件名乱码问题
//				System.out.println("发现附件: " +  strFileName);
//				System.out.println("内容类型: " + MimeUtility.decodeText(part.getContentType()));
//				System.out.println("附件内容:" + MimeUtility.decodeText((String)part.getContent()));
				this.processAttachment(strFileName, part.getInputStream(),mailBean);

			}else{
				mailBean.setContentText(new MailBodyPart((BodyPart) part).getContent());
			}
		}else if(part.isMimeType("text/html")){
			mailBean.setContentHtml(part.getContent().toString());
    	}else if(part.isMimeType("multipart/*")){
			Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
				_convertMessageBody(multipart.getBodyPart(i), mailBean);
            }
    	}else if(part.isMimeType("message/rfc822")){
    		_convertMessageBody((BodyPart) part.getContent(),mailBean);
    	}else if(part instanceof MimeBodyPart){
    		this.processAttachment(part,mailBean);
    	}
	}

	private void processAttachment(Part part,MailBean mailBean) throws MessagingException, IOException{
		String disposition = part.getDisposition();
		String contentId = ((MimeBodyPart) part).getContentID();

		if(contentId != null){
			contentId = contentId.replaceAll("^<|>$", "");
		}

		if((disposition != null && (Part.INLINE.equalsIgnoreCase(disposition)
				|| Part.ATTACHMENT.equalsIgnoreCase(disposition)))
				|| contentId != null){
			String fileName = part.getFileName();
			if(null != fileName){
				fileName = MimeUtility.decodeText(fileName);
			}

			String path=null;
//			System.out.println("--disposition2="+disposition+"---contentId="+contentId);
//			System.out.println("--fileName="+fileName);
			String fileType = fileName.contains(".")?fileName.substring(fileName.lastIndexOf(".") + 1):"";
			if(Part.INLINE.equalsIgnoreCase(disposition) || contentId != null){
				if(Validator.isNull(contentId)){
					contentId = UUID.randomUUID().toString();
				}
				contentId+="."+fileType;
				AttachmentVo attachmentVo = new AttachmentVo();
				attachmentVo.setRelativePath(resourceDirPath + File.separatorChar + contentId);
				attachmentVo.setUuidName(contentId);
				attachmentVo.setFullPath(MailProcessService.DirPath + attachmentVo.getRelativePath());
				attachmentVo.setRealName(fileName);
				mailBean.getInnerResources().add(attachmentVo);

				path = attachmentVo.getFullPath();
			}else if(Part.ATTACHMENT.equalsIgnoreCase(disposition)){
				if(Validator.isNull(contentId)){
					contentId = UUID.randomUUID().toString();
				}
				contentId+="."+fileType;
				AttachmentVo attachmentVo = new AttachmentVo();
				attachmentVo.setRelativePath(resourceDirPath + File.separatorChar + contentId);
				attachmentVo.setUuidName(contentId);
				attachmentVo.setFullPath(MailProcessService.DirPath + attachmentVo.getRelativePath());
				attachmentVo.setRealName(fileName);
				mailBean.getAttachments().add(attachmentVo);


				path = attachmentVo.getFullPath();
			}
//			System.out.println("--path="+path);

			final String folder = MailProcessService.DirPath + resourceDirPath;
//			System.out.println("--folder="+folder);
			if(downloadFile&&path!=null){
				if(!FileUtil.exists(folder)) {
					FileUtil.mkdirs(folder);
				}
				((MimeBodyPart)part).saveFile(new File(path));
			}
		}
	}

	public void processAttachment(String attachName,InputStream in,MailBean mailBean) {
		if(in==null){
			return;
		}
		String fileType = attachName.substring(attachName.lastIndexOf(".") + 1);
		String fileName = UUID.randomUUID().toString();
		if(Validator.isNotNull(fileType)){
			fileName+="."+fileType;
		}
		final String folder = MailProcessService.DirPath + resourceDirPath;
		if(downloadFile && !FileUtil.exists(folder)){
			FileUtil.mkdirs(folder);
		}
		BufferedInputStream bis = null;
		BufferedOutputStream bos = null;
		final File file = new File(folder+File.separatorChar+fileName);

//		System.out.println("---path:"+folder+File.separatorChar+fileName);
		AttachmentVo attachmentVo = new AttachmentVo();
		attachmentVo.setRelativePath(resourceDirPath + File.separatorChar + fileName);
		attachmentVo.setUuidName(fileName);
		attachmentVo.setFullPath(folder + File.separatorChar + fileName);
		attachmentVo.setRealName(attachName);
		mailBean.getAttachments().add(attachmentVo);

		if(downloadFile) {
            try {
				bis = new BufferedInputStream(in);
				bos = new BufferedOutputStream(new FileOutputStream(file));
				int len = 2048;
				byte[] b = new byte[len];
				while ((len = bis.read(b)) != -1) {
					bos.write(b, 0, len);
				}
				bos.flush();
            } catch (Exception e) {
                logger.error("folder="+folder+File.separatorChar+fileName);
                logger.error("MailRecipient processAttachment error:",e);
            } finally {
                try {
                    if(bis!=null) bis.close();
                    if(bos!=null) bos.close();
                    if(in!=null) in.close();
                } catch (IOException e) {
                    logger.error("MailRecipient processAttachment IOException:",e);
                }
            }
		}
	}

	@Override
	protected Session getSession() {
		Properties props = new Properties();
    	Session session = Session.getInstance(props);
		return session;
	}

	public String getFolderName() {
		return folderName;
	}

	public void setFolderName(String folderName) {
		this.folderName = folderName;
	}
	
	public boolean isDownloadFile() {
		return downloadFile;
	}

	public void setDownloadFile(boolean downloadFile) {
		this.downloadFile = downloadFile;
	}

	public String getResourceDirPath() {
		return resourceDirPath;
	}

	public void setResourceDirPath(String resourceDirPath) {
		this.resourceDirPath = resourceDirPath;
	}
	public MailListener getListener() {
		return listener;
	}

	public void setListener(MailListener listener) {
		this.listener = listener;
	}

	public SearchTerm getSearchTerm() {
		return searchTerm;
	}

	public void setSearchTerm(SearchTerm searchTerm) {
		this.searchTerm = searchTerm;
	}
	
}
